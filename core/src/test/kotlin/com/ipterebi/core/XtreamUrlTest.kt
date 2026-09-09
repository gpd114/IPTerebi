package com.ipterebi.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Every one of these is a shape a provider actually hands to a customer. The
 * login screen is where people give up, and it gives up on their behalf if it
 * cannot cope with a paste.
 */
class XtreamUrlTest {

    @Test
    fun `bare host and port gets http and keeps the port`() {
        val parsed = XtreamUrl.parse("line.example.com:8080")
        assertEquals("http://line.example.com:8080", parsed?.base)
    }

    @Test
    fun `trailing slash is dropped`() {
        assertEquals(
            "http://line.example.com:8080",
            XtreamUrl.parse("http://line.example.com:8080/")?.base,
        )
    }

    @Test
    fun `the panel's own web player path is dropped`() {
        assertEquals(
            "http://line.example.com:8080",
            XtreamUrl.parse("http://line.example.com:8080/c/")?.base,
        )
    }

    @Test
    fun `https is preserved when it was asked for`() {
        assertEquals(
            "https://line.example.com:8443",
            XtreamUrl.parse("https://line.example.com:8443/")?.base,
        )
    }

    @Test
    fun `an explicit default port is dropped rather than echoed back`() {
        // Providers write :443 and :80 into the address they hand out. Keeping
        // it is harmless but makes every stored base differ from every other
        // one for the same server, which is confusing to read in a bug report.
        assertEquals(
            "https://line.example.com",
            XtreamUrl.parse("https://line.example.com:443/")?.base,
        )
        assertEquals(
            "http://line.example.com",
            XtreamUrl.parse("http://line.example.com:80/c/")?.base,
        )
    }

    @Test
    fun `a default port is not written back into the base`() {
        assertEquals("http://line.example.com", XtreamUrl.parse("line.example.com")?.base)
    }

    @Test
    fun `credentials are lifted out of a player_api url`() {
        val parsed = XtreamUrl.parse(
            "http://line.example.com:8080/player_api.php?username=alice&password=s3cret"
        )
        assertEquals("http://line.example.com:8080", parsed?.base)
        assertEquals("alice", parsed?.username)
        assertEquals("s3cret", parsed?.password)
    }

    @Test
    fun `credentials are lifted out of an m3u playlist url`() {
        val parsed = XtreamUrl.parse(
            "http://line.example.com:8080/get.php?username=alice&password=s3cret&type=m3u_plus&output=ts"
        )
        assertEquals("http://line.example.com:8080", parsed?.base)
        assertEquals("alice", parsed?.username)
        assertEquals("s3cret", parsed?.password)
    }

    @Test
    fun `percent-encoded credentials come back decoded`() {
        val parsed = XtreamUrl.parse(
            "http://line.example.com:8080/get.php?username=al%20ice&password=p%40ss%2Bword"
        )
        assertEquals("al ice", parsed?.username)
        assertEquals("p@ss+word", parsed?.password)
    }

    @Test
    fun `no credentials in the url leaves them null rather than blank`() {
        val parsed = XtreamUrl.parse("http://line.example.com:8080/")
        assertNull(parsed?.username)
        assertNull(parsed?.password)
    }

    @Test
    fun `empty query parameters count as absent`() {
        val parsed = XtreamUrl.parse("http://line.example.com:8080/player_api.php?username=&password=")
        assertNull(parsed?.username)
        assertNull(parsed?.password)
    }

    @Test
    fun `surrounding whitespace from a paste is ignored`() {
        assertEquals(
            "http://line.example.com:8080",
            XtreamUrl.parse("  http://line.example.com:8080/  ")?.base,
        )
    }

    @Test
    fun `nonsense is rejected rather than turned into a hostname`() {
        assertNull(XtreamUrl.parse(""))
        assertNull(XtreamUrl.parse("   "))
        assertNull(XtreamUrl.parse("http://"))
    }
}
