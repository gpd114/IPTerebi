package com.ipterebi.core

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Films are not channels that happen to end. The two differences that matter
 * are the extension — a film is a file, and `.mp4`, `.mkv` and `.avi` all turn
 * up on the same line — and that the stream format setting, which picks between
 * MPEG-TS and HLS for live television, has nothing to do with them.
 */
class VodTest {

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

    // The URL

    @Test
    fun `a film url uses the movie path and the film's own extension`() {
        val film = VodStream(streamId = 12345, containerExtension = "mkv")

        assertEquals(
            "${account.base}/movie/alice/s3cret/12345.mkv",
            client.vodStreamUrl(account, film),
        )
    }

    @Test
    fun `the live stream format does not follow a film around`() {
        // TS and HLS pick a container for live television. A film is a file on
        // disk, and asking for 12345.ts because live uses .ts gets a 404 from a
        // panel holding the film quite happily.
        val film = VodStream(streamId = 12345, containerExtension = "mp4")
        val hlsAccount = account.copy(format = StreamFormat.HLS)
        val tsAccount = account.copy(format = StreamFormat.TS)

        assertEquals(
            client.vodStreamUrl(tsAccount, film),
            client.vodStreamUrl(hlsAccount, film),
        )
        assertTrue(client.vodStreamUrl(hlsAccount, film).endsWith("12345.mp4"))
    }

    @Test
    fun `a film with no extension is still offered, as mp4`() {
        val film = VodStream(streamId = 7, containerExtension = "")

        assertEquals("mp4", film.playbackExtension)
        assertTrue(client.vodStreamUrl(account, film).endsWith("7.mp4"))
    }

    @Test
    fun `a leading dot on the extension is not doubled`() {
        assertEquals("mkv", VodStream(streamId = 1, containerExtension = ".mkv").playbackExtension)
        assertTrue(
            client.vodStreamUrl(account, VodStream(streamId = 1, containerExtension = ".mkv"))
                .endsWith("1.mkv"),
        )
    }

    @Test
    fun `awkward characters in a password are encoded into a film path too`() {
        val awkward = account.copy(password = "p@ss word/+")
        val url = client.vodStreamUrl(awkward, VodStream(streamId = 1, containerExtension = "mp4"))

        assertTrue(url.contains("p@ss%20word%2F+"), "got: $url")
    }

    @Test
    fun `the live url is unchanged by sharing a builder with films`() {
        // liveStreamUrl now goes through the same helper. Pinned because a
        // regression here breaks every channel rather than one film.
        assertEquals(
            "${account.base}/live/alice/s3cret/12345.ts",
            client.liveStreamUrl(account, 12345),
        )
        assertEquals(
            "${account.base}/live/alice/s3cret/12345.m3u8",
            client.liveStreamUrl(account.copy(format = StreamFormat.HLS), 12345),
        )
    }

    // Parsing

    @Test
    fun `a film list parses with the ids quoted or bare`() {
        val body = """
            [{"num":1,"name":"Arrival","stream_type":"movie","stream_id":518,
              "stream_icon":"http://img/1.jpg","category_id":"12",
              "container_extension":"mkv","rating":"7.6"},
             {"num":"2","name":"Dune","stream_id":"519","category_id":12,
              "container_extension":"mp4","rating":8.1}]
        """.trimIndent()

        val films = LenientJson.decodeFromString(ListSerializer(VodStream.serializer()), body)

        assertEquals(listOf(518, 519), films.map { it.streamId })
        assertEquals(listOf("mkv", "mp4"), films.map { it.playbackExtension })
        assertEquals("12", films[1].categoryId)
    }

    @Test
    fun `a null rating does not fail the whole film list`() {
        val body = """[{"stream_id":1,"name":"Something","rating":null,"custom_sid":null}]"""

        val film = LenientJson.decodeFromString(ListSerializer(VodStream.serializer()), body).single()

        assertEquals("Something", film.name)
        assertNull(film.ratingOutOfTen)
    }

    @Test
    fun `a rating of zero is treated as no rating`() {
        assertNull(VodStream(rating = "0").ratingOutOfTen)
        assertNull(VodStream(rating = "").ratingOutOfTen)
        assertEquals(7.6, VodStream(rating = "7.6").ratingOutOfTen)
    }

    @Test
    fun `films with no usable id are dropped like channels are`() {
        val films = listOf(
            VodStream(streamId = 0, name = "no id"),
            VodStream(streamId = 4, name = "kept"),
            VodStream(streamId = 4, name = "duplicate"),
        )

        assertEquals(listOf(4), films.playableFilms().map { it.streamId })
    }

    // Over the wire

    @Test
    fun `films are asked for with the vod action`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""[{"stream_id":518,"name":"Arrival","container_extension":"mkv"}]""")
        )

        val films = client.vodStreams(account, categoryId = "12")

        val asked = server.takeRequest().requestUrl!!
        assertEquals("get_vod_streams", asked.queryParameter("action"))
        assertEquals("12", asked.queryParameter("category_id"))
        assertEquals("Arrival", films.single().name)
    }

    @Test
    fun `film categories go through the same hygiene as live ones`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """[{"category_id":"","category_name":"Nowhere"},
                    {"category_id":"3","category_name":"Drama"},
                    {"category_id":"3","category_name":"Drama"}]"""
            )
        )

        val categories = client.vodCategories(account)

        assertEquals(listOf("3"), categories.map { it.id })
        assertEquals("get_vod_categories", server.takeRequest().requestUrl!!.queryParameter("action"))
    }

    @Test
    fun `a panel with no film section says so rather than throwing something opaque`() = runBlocking {
        server.enqueue(MockResponse().setBody("false"))

        val failure = runCatching { client.vodStreams(account) }.exceptionOrNull()

        assertTrue(failure is XtreamException, "got: $failure")
        assertTrue(
            failure.message.orEmpty().contains("film list"),
            "message did not name what was missing: ${failure?.message}",
        )
    }

    @Test
    fun `film logging never carries the credentials`() = runBlocking {
        val lines = mutableListOf<String>()
        val logging = XtreamClient(log = { lines += it })
        server.enqueue(MockResponse().setBody("""[{"stream_id":1,"name":"A"}]"""))

        logging.vodStreams(account)

        assertTrue(lines.isNotEmpty())
        assertFalse(lines.any { it.contains("s3cret") }, "credentials leaked: $lines")
    }
}
