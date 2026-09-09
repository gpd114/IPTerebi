package com.ipterebi.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class XtreamClientTest {

    private lateinit var server: MockWebServer
    private lateinit var account: XtreamAccount
    private val client = XtreamClient()

    @BeforeTest
    fun start() {
        server = MockWebServer().apply { start() }
        account = XtreamAccount(
            base = XtreamUrl.parse(server.url("/").toString())!!.base,
            username = "alice",
            password = "s3cret",
        )
    }

    @AfterTest
    fun stop() = server.shutdown()

    @Test
    fun `authenticate sends the credentials as query parameters`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"user_info":{"auth":1,"status":"Active"}}"""))

        client.authenticate(account)

        val request = server.takeRequest()
        assertEquals("/player_api.php?username=alice&password=s3cret", request.path)
        // A default User-Agent gets this request refused by a fair number of
        // panels, so it must actually be on the wire.
        assertEquals(XtreamAccount.DEFAULT_USER_AGENT, request.getHeader("User-Agent"))
    }

    @Test
    fun `a rejected login reports the panel's own message`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"user_info":{"auth":0,"message":"Line banned"}}"""))

        val failure = assertFailsWith<XtreamException> { client.authenticate(account) }
        assertEquals("Line banned", failure.message)
    }

    @Test
    fun `an expired line is refused with its status named`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"user_info":{"auth":1,"status":"Expired"}}"""))

        val failure = assertFailsWith<XtreamException> { client.authenticate(account) }
        assertTrue(failure.message!!.contains("Expired"), "got: ${failure.message}")
    }

    @Test
    fun `an html challenge page is reported as such, not as a parse error`() = runBlocking {
        server.enqueue(MockResponse().setBody("<!DOCTYPE html><html><body>Attention Required</body></html>"))

        val failure = assertFailsWith<XtreamException> { client.authenticate(account) }
        assertTrue(failure.message!!.contains("web page"), "got: ${failure.message}")
    }

    @Test
    fun `a 403 is explained rather than reported as a number alone`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("nope"))

        val failure = assertFailsWith<XtreamException> { client.authenticate(account) }
        assertTrue(failure.message!!.contains("user agent"), "got: ${failure.message}")
    }

    @Test
    fun `live streams can be narrowed to one category`() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"stream_id":1,"name":"BBC One HD","category_id":"7"}]"""))

        val channels = client.liveStreams(account, categoryId = "7")

        assertEquals("BBC One HD", channels.single().name)
        val request = server.takeRequest()
        assertTrue(request.path!!.contains("action=get_live_streams"))
        assertTrue(request.path!!.contains("category_id=7"))
    }

    @Test
    fun `a panel answering false is named as unsupported`() = runBlocking {
        server.enqueue(MockResponse().setBody("false"))

        val failure = assertFailsWith<XtreamException> { client.liveCategories(account) }
        assertTrue(failure.message!!.contains("does not support"), "got: ${failure.message}")
    }

    @Test
    fun `the stream url puts the credentials in the path`() {
        assertEquals(
            "${account.base}/live/alice/s3cret/12345.ts",
            client.liveStreamUrl(account, 12345),
        )
    }

    @Test
    fun `hls accounts ask for an m3u8`() {
        val hls = account.copy(format = StreamFormat.HLS)
        assertTrue(client.liveStreamUrl(hls, 12345).endsWith("/12345.m3u8"))
    }

    @Test
    fun `awkward characters in a password are encoded into the path`() {
        val awkward = account.copy(password = "p@ss word/+")
        val url = client.liveStreamUrl(awkward, 1)
        // A raw slash would invent a path segment and the request would 404
        // against a panel that is working perfectly well.
        assertTrue(url.contains("p@ss%20word%2F+"), "got: $url")
    }

    @Test
    fun `logging never carries the credentials`() = runBlocking {
        val lines = mutableListOf<String>()
        val logging = XtreamClient(log = { lines += it })
        server.enqueue(MockResponse().setBody("""{"user_info":{"auth":1,"status":"Active"}}"""))

        logging.authenticate(account)

        assertTrue(lines.isNotEmpty())
        assertTrue(lines.none { it.contains("s3cret") }, "credentials leaked into the log: $lines")
    }
}
