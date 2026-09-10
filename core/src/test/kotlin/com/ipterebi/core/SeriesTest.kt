package com.ipterebi.core

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `get_series_info` is the most structurally unreliable response in the API:
 * the same field arrives as an object, an array of arrays, a flat array, or —
 * PHP's way of spelling an empty object — `[]`. Each shape here is one the
 * parser has to read, and the point of each test is that a strict decoder
 * would either throw or lose episodes on it.
 */
class SeriesTest {

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

    private fun parse(body: String, name: String = "Fallback") =
        parseSeriesDetail(LenientJson, LenientJson.decodeFromString(JsonElement.serializer(), body), name)

    private fun ep(id: String, num: Any, season: Int = 0, title: String = "Episode $id", info: String = "{}") =
        """{"id":"$id","episode_num":${if (num is String) "\"$num\"" else num},"title":"$title",""" +
            """"container_extension":"mkv","season":$season,"info":$info}"""

    // The shapes of `episodes`

    @Test
    fun `episodes keyed by season number are grouped by that key`() {
        val detail = parse("""{"episodes":{"1":[${ep("11", 1)},${ep("12", 2)}],"2":[${ep("21", 1)}]}}""")

        assertEquals(listOf(1, 2), detail.seasons.map { it.number })
        assertEquals(listOf("11", "12"), detail.seasons[0].episodes.map { it.episode.id })
        assertEquals(3, detail.episodeCount)
    }

    @Test
    fun `episodes as an array of arrays are seasons in order`() {
        val detail = parse("""{"episodes":[[${ep("11", 1)}],[${ep("21", 1)},${ep("22", 2)}]]}""")

        assertEquals(listOf(1, 2), detail.seasons.map { it.number })
        assertEquals(listOf("21", "22"), detail.seasons[1].episodes.map { it.episode.id })
    }

    @Test
    fun `episodes as one flat array are grouped by their own season field`() {
        val detail = parse("""{"episodes":[${ep("11", 1, season = 1)},${ep("21", 1, season = 2)},${ep("12", 2, season = 1)}]}""")

        assertEquals(listOf(1, 2), detail.seasons.map { it.number })
        assertEquals(listOf("11", "12"), detail.seasons[0].episodes.map { it.episode.id })
    }

    @Test
    fun `episodes as an empty array is a series with no episodes, not a failure`() {
        // `[]` is how PHP serialises an empty associative array.
        val detail = parse("""{"episodes":[],"info":{"name":"Empty Show"}}""")

        assertTrue(detail.seasons.isEmpty())
        assertEquals("Empty Show", detail.name)
    }

    @Test
    fun `a missing episodes field is a series with no episodes`() {
        assertTrue(parse("""{"info":{"name":"X"}}""").seasons.isEmpty())
    }

    @Test
    fun `the grouping key beats the season field inside the episode`() {
        // The field is the one panels leave at zero; the key is where the panel
        // itself will show the episode.
        val detail = parse("""{"episodes":{"3":[${ep("31", 1, season = 0)}]}}""")

        assertEquals(3, detail.seasons.single().number)
        assertEquals(3, detail.seasons.single().episodes.single().seasonNumber)
    }

    // PHP's empty object

    @Test
    fun `an episode whose info is an empty array is kept, with no details`() {
        val detail = parse("""{"episodes":{"1":[${ep("11", 1, info = "[]")}]}}""")

        val entry = detail.seasons.single().episodes.single()
        assertEquals("11", entry.episode.id)
        assertEquals(EpisodeDetails(), entry.details)
    }

    @Test
    fun `a series whose info is an empty array falls back to the name it was listed under`() {
        val detail = parse("""{"info":[],"episodes":{"1":[${ep("11", 1)}]}}""", name = "From the list")

        assertEquals("From the list", detail.name)
        assertEquals(1, detail.episodeCount)
    }

    @Test
    fun `episode details are read when they are there`() {
        val info = """{"movie_image":"http://img/e.jpg","plot":"Things happen.","duration_secs":"2700"}"""
        val entry = parse("""{"episodes":{"1":[${ep("11", 1, info = info)}]}}""").seasons.single().episodes.single()

        assertEquals("Things happen.", entry.details.plot)
        assertEquals(2700, entry.details.durationSeconds)
        assertEquals("45 min", entry.durationLabel)
    }

    // Damage limitation

    @Test
    fun `one unreadable episode does not cost the rest of the season`() {
        val detail = parse("""{"episodes":{"1":[${ep("11", 1)},"not an episode",42,${ep("13", 3)}]}}""")

        assertEquals(listOf("11", "13"), detail.seasons.single().episodes.map { it.episode.id })
    }

    @Test
    fun `episodes with no id are dropped, and repeated ids appear once`() {
        val detail = parse("""{"episodes":{"1":[${ep("", 1)},${ep("12", 2)},${ep("12", 2)}]}}""")

        assertEquals(listOf("12"), detail.seasons.single().episodes.map { it.episode.id })
    }

    @Test
    fun `a root that is not an object is an empty series, not a crash`() {
        assertTrue(parse("[]").seasons.isEmpty())
        assertEquals("Fallback", parse("[]").name)
    }

    // Order and naming

    @Test
    fun `episodes are in episode order whatever order they arrived in`() {
        val detail = parse("""{"episodes":{"1":[${ep("13", 3)},${ep("11", "1")},${ep("12", 2)}]}}""")

        assertEquals(listOf(1, 2, 3), detail.seasons.single().episodes.map { it.episode.episodeNumber })
    }

    @Test
    fun `unnumbered episodes go after the numbered ones`() {
        val detail = parse("""{"episodes":{"1":[${ep("x", 0)},${ep("11", 1)}]}}""")

        assertEquals(listOf("11", "x"), detail.seasons.single().episodes.map { it.episode.id })
    }

    @Test
    fun `specials in season zero come last, not first`() {
        // Someone opening a series expects to start at the beginning, not at a
        // Christmas special from year four.
        val detail = parse("""{"episodes":{"0":[${ep("s", 1)}],"2":[${ep("21", 1)}],"1":[${ep("11", 1)}]}}""")

        assertEquals(listOf(1, 2, 0), detail.seasons.map { it.number })
        assertEquals("Specials", detail.seasons.last().name)
    }

    @Test
    fun `season names come from seasons when it is there, and are made up when it is not`() {
        val detail = parse(
            """{"seasons":[{"season_number":1,"name":"The First One"}],
                "episodes":{"1":[${ep("11", 1)}],"2":[${ep("21", 1)}]}}"""
        )

        assertEquals(listOf("The First One", "Season 2"), detail.seasons.map { it.name })
    }

    @Test
    fun `an empty seasons list does not hide seasons that have episodes`() {
        val detail = parse("""{"seasons":[],"episodes":{"1":[${ep("11", 1)}]}}""")
        assertEquals("Season 1", detail.seasons.single().name)
    }

    // Labels

    @Test
    fun `the episode code is two-digit and zero-padded`() {
        val entry = EpisodeEntry(Episode(id = "1", episodeNumber = 3), EpisodeDetails(), seasonNumber = 2)
        assertEquals("S02E03", entry.code)
    }

    @Test
    fun `the episode code uses ascii digits whatever the device language`() {
        // String.format follows the default locale, and in Arabic that gives
        // Arabic-Indic digits.
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            val entry = EpisodeEntry(Episode(id = "1", episodeNumber = 3), EpisodeDetails(), seasonNumber = 2)
            assertEquals("S02E03", entry.code)
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `the series name and code are taken off the front of an episode title`() {
        val entry = EpisodeEntry(
            Episode(id = "1", episodeNumber = 3, title = "Slow Horses - S02E03 - Hello Goodbye"),
            EpisodeDetails(),
            seasonNumber = 2,
        )
        assertEquals("Hello Goodbye", entry.displayTitle("Slow Horses"))
    }

    @Test
    fun `a title that is only the series name and code is kept as sent`() {
        val entry = EpisodeEntry(
            Episode(id = "1", episodeNumber = 3, title = "Slow Horses - S02E03"),
            EpisodeDetails(),
            seasonNumber = 2,
        )
        assertEquals("Slow Horses - S02E03", entry.displayTitle("Slow Horses"))
    }

    @Test
    fun `a title that merely contains the series name is left alone`() {
        val entry = EpisodeEntry(Episode(id = "1", episodeNumber = 1, title = "Return of Slow Horses"), EpisodeDetails(), 1)
        assertEquals("Return of Slow Horses", entry.displayTitle("Slow Horses"))
    }

    // The URL

    @Test
    fun `an episode url uses the series path, the episode id, and the episode extension`() {
        val episode = Episode(id = "4567", containerExtension = "mkv")

        assertEquals(
            "${account.base}/series/alice/s3cret/4567.mkv",
            client.episodeStreamUrl(account, episode),
        )
    }

    @Test
    fun `the live stream format does not follow an episode either`() {
        val episode = Episode(id = "4567", containerExtension = "mp4")
        assertEquals(
            client.episodeStreamUrl(account.copy(format = StreamFormat.TS), episode),
            client.episodeStreamUrl(account.copy(format = StreamFormat.HLS), episode),
        )
    }

    @Test
    fun `an episode id that is not a number still makes a well-formed url`() {
        // The id is a string and is never parsed, so a panel using something
        // else still plays — and whatever it is, it cannot invent a path.
        val url = client.episodeStreamUrl(account, Episode(id = "ab/c d", containerExtension = "mp4"))
        assertTrue(url.endsWith("/series/alice/s3cret/ab%2Fc%20d.mp4"), "got: $url")
    }

    @Test
    fun `the other urls are unchanged by the builder taking a string id`() {
        assertEquals("${account.base}/live/alice/s3cret/12345.ts", client.liveStreamUrl(account, 12345))
        assertEquals(
            "${account.base}/movie/alice/s3cret/518.mkv",
            client.vodStreamUrl(account, VodStream(streamId = 518, containerExtension = "mkv")),
        )
    }

    // The series list

    @Test
    fun `a series list parses with ids quoted or bare and releaseDate in camelCase`() {
        val body = """[{"num":1,"name":"Slow Horses","series_id":88,"cover":"http://c/1.jpg","releaseDate":"2022-04-01","rating":"8.2","category_id":"5"},
                       {"name":"Severance","series_id":"89","rating":0,"category_id":5}]"""

        val list = LenientJson.decodeFromString(ListSerializer(Series.serializer()), body)

        assertEquals(listOf(88, 89), list.map { it.seriesId })
        assertEquals("2022-04-01", list[0].releaseDate)
        assertEquals(8.2, list[0].ratingOutOfTen)
        assertEquals(null, list[1].ratingOutOfTen)
    }

    @Test
    fun `series with no usable id are dropped like channels are`() {
        val list = listOf(Series(seriesId = 0), Series(seriesId = 7), Series(seriesId = 7))
        assertEquals(listOf(7), list.listableSeries().map { it.seriesId })
    }

    // Over the wire

    @Test
    fun `the episode list is asked for by series id`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"info":{"name":"Show"},"episodes":{"1":[${ep("11", 1)}]}}"""))

        val detail = client.seriesDetail(account, seriesId = 88)

        val asked = server.takeRequest().requestUrl!!
        assertEquals("get_series_info", asked.queryParameter("action"))
        assertEquals("88", asked.queryParameter("series_id"))
        assertEquals("Show", detail.name)
        assertEquals(1, detail.episodeCount)
    }

    @Test
    fun `series are asked for with the series action`() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"series_id":88,"name":"Slow Horses"}]"""))

        val list = client.series(account, categoryId = "5")

        val asked = server.takeRequest().requestUrl!!
        assertEquals("get_series", asked.queryParameter("action"))
        assertEquals("5", asked.queryParameter("category_id"))
        assertEquals("Slow Horses", list.single().name)
    }

    @Test
    fun `a challenge page where the episodes should be is reported as such`() = runBlocking {
        server.enqueue(MockResponse().setBody("<html>blocked</html>"))

        val failure = runCatching { client.seriesDetail(account, seriesId = 1) }.exceptionOrNull()

        assertTrue(failure is XtreamException, "got: $failure")
        assertTrue(failure.message.orEmpty().contains("web page"), "got: ${failure?.message}")
    }

    // Error wording

    @Test
    fun `an episode is called an episode, never a film or a channel`() {
        listOf(401, 403, 404, 456, 500, 418).forEach { code ->
            val message = describeEpisodeHttpError(code)
            assertFalse(message.contains("film", ignoreCase = true), "$code said: $message")
            assertFalse(message.contains("channel", ignoreCase = true), "$code said: $message")
        }
        assertTrue(describeEpisodeHttpError(404).contains("episode"))
    }
}
