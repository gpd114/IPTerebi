package com.ipterebi.core

import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The panel forks disagree about types. These payloads are trimmed from real
 * responses; the point of each is that strict parsing would throw on it.
 */
class XtreamParsingTest {

    @Test
    fun `stream_id as a number and as a string both parse`() {
        val asNumber = """[{"stream_id":12345,"name":"BBC One HD","category_id":1}]"""
        val asString = """[{"stream_id":"12345","name":"BBC One HD","category_id":"1"}]"""

        val fromNumber = LenientJson.decodeFromString(ListSerializer(LiveStream.serializer()), asNumber)
        val fromString = LenientJson.decodeFromString(ListSerializer(LiveStream.serializer()), asString)

        assertEquals(12345, fromNumber.single().streamId)
        assertEquals(12345, fromString.single().streamId)
        // category_id is quoted by some panels and bare by others, and the two
        // must compare equal or category filtering silently matches nothing.
        assertEquals(fromNumber.single().categoryId, fromString.single().categoryId)
    }

    @Test
    fun `a null icon does not fail the whole channel list`() {
        val json = """[{"stream_id":1,"name":"Channel","stream_icon":null,"epg_channel_id":null}]"""
        val channel = LenientJson.decodeFromString(ListSerializer(LiveStream.serializer()), json).single()
        assertEquals("", channel.icon)
        assertEquals("", channel.epgChannelId)
    }

    @Test
    fun `unknown fields from a fork are ignored`() {
        val json = """[{"stream_id":1,"name":"Channel","tv_archive":1,"custom_sid":"","some_new_field":{"a":1}}]"""
        val channel = LenientJson.decodeFromString(ListSerializer(LiveStream.serializer()), json).single()
        assertEquals("Channel", channel.name)
    }

    @Test
    fun `a decimal channel number is not read as zero`() {
        val json = """[{"stream_id":1,"name":"Channel","num":"3.0"}]"""
        assertEquals(
            3,
            LenientJson.decodeFromString(ListSerializer(LiveStream.serializer()), json).single().number,
        )
    }

    @Test
    fun `a successful login is recognised`() {
        val json = """
            {"user_info":{"username":"alice","auth":1,"status":"Active",
            "exp_date":"1794700800","max_connections":"2","active_cons":"0",
            "allowed_output_formats":["m3u8","ts","rtmp"]},
            "server_info":{"url":"line.example.com","port":"8080"}}
        """.trimIndent()

        val info = LenientJson.decodeFromString(AuthResponse.serializer(), json).userInfo
        assertTrue(info.isAuthenticated)
        assertTrue(info.isActive)
        assertEquals("2", info.maxConnections)
        assertEquals(listOf("m3u8", "ts", "rtmp"), info.allowedOutputFormats)
    }

    @Test
    fun `a rejected login is an ordinary 200 with auth zero`() {
        val json = """{"user_info":{"auth":0,"message":"Invalid credentials"}}"""
        val info = LenientJson.decodeFromString(AuthResponse.serializer(), json).userInfo
        assertFalse(info.isAuthenticated)
        assertEquals("Invalid credentials", info.message)
    }

    @Test
    fun `an expired line authenticates but is not active`() {
        val json = """{"user_info":{"auth":1,"status":"Expired","exp_date":"1600000000"}}"""
        val info = LenientJson.decodeFromString(AuthResponse.serializer(), json).userInfo
        assertTrue(info.isAuthenticated)
        assertFalse(info.isActive)
    }

    @Test
    fun `max_connections as a number parses like the quoted form`() {
        val json = """{"user_info":{"auth":1,"status":"Active","max_connections":2}}"""
        val info = LenientJson.decodeFromString(AuthResponse.serializer(), json).userInfo
        assertEquals("2", info.maxConnections)
    }

    @Test
    fun `logging a sign-in body strips the password the panel echoes back`() {
        // Panels return the password in clear inside user_info. If this ever
        // stops holding, a raw body reaches logcat and takes the user's
        // password with it.
        val body = """{"user_info":{"username":"alice","password":"s3cret","auth":1}}"""
        val safe = body.withoutCredentialValues()

        assertFalse(safe.contains("s3cret"), "password survived: $safe")
        assertFalse(safe.contains("alice"), "username survived: $safe")
        assertTrue(safe.contains("\"auth\":1"), "scrubbing ate the rest of the body: $safe")
    }

    @Test
    fun `scrubbing copes with the spacing panels actually emit`() {
        val body = """{"password" : "s3cret", "username":  "alice"}"""
        val safe = body.withoutCredentialValues()
        assertFalse(safe.contains("s3cret"), "got: $safe")
        assertFalse(safe.contains("alice"), "got: $safe")
    }

    @Test
    fun `a body with no credentials in it is left alone`() {
        val body = """[{"stream_id":1,"name":"BBC One HD"}]"""
        assertEquals(body, body.withoutCredentialValues())
    }
}
