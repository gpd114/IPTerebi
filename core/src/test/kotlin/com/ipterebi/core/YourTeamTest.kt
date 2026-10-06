package com.ipterebi.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Which of a team's matches belongs on the screen, and what a name may be. */
class YourTeamTest {

    private fun showing(start: Long, stop: Long, title: String, feeds: Int) = Showing(
        title = title,
        start = start,
        stop = stop,
        channels = (1..feeds).map { "ch$it.uk" },
    )

    private val now = 1_000_000L

    /** Most of these only exercise the ordering, so any name will do. */
    private val team = "Belgium"

    @Test
    fun `what is on now beats what is coming`() {
        // The whole point: the list is wanted when a feed dies mid-match.
        val found = listOf(
            showing(now + 3600, now + 7200, "Tonight's match", feeds = 9),
            showing(now - 600, now + 3000, "The match on now", feeds = 2),
        )
        val match = assertNotNull(teamMatch(found, now, team))
        assertEquals("The match on now", match.showing.title)
        assertTrue(match.onNow)
    }

    @Test
    fun `with nothing on, the soonest to come is shown`() {
        // So the row says something useful for the six days a week when the
        // team is not playing, rather than going blank.
        val found = listOf(
            showing(now + 7200, now + 10800, "Saturday", feeds = 3),
            showing(now + 3600, now + 7200, "Sooner", feeds = 3),
        )
        val match = assertNotNull(teamMatch(found, now, team))
        assertEquals("Sooner", match.showing.title)
        assertTrue(!match.onNow)
    }

    @Test
    fun `among several on at once the widest carried wins`() {
        // A fixture is carried on many channels; a highlights programme that
        // merely names the team is not. Same ranking as the rest of this.
        val found = listOf(
            showing(now - 60, now + 3000, "Transfer Talk mentions them", feeds = 1),
            showing(now - 60, now + 3000, "The fixture", feeds = 7),
        )
        assertEquals("The fixture", assertNotNull(teamMatch(found, now, team)).showing.title)
    }

    @Test
    fun `among several starting together the widest carried wins too`() {
        val found = listOf(
            showing(now + 600, now + 4200, "Also then", feeds = 2),
            showing(now + 600, now + 4200, "The fixture", feeds = 6),
        )
        assertEquals("The fixture", assertNotNull(teamMatch(found, now, team)).showing.title)
    }

    @Test
    fun `a match that has finished is not offered`() {
        val over = listOf(showing(now - 7200, now - 3600, "Yesterday", feeds = 5))
        assertNull(teamMatch(over, now, "Belgium"))
    }

    @Test
    fun `nothing for them is nothing, which is most of the week`() {
        assertNull(teamMatch(emptyList(), now, "Belgium"))
    }

    @Test
    fun `a match ending exactly now has finished`() {
        assertNull(teamMatch(listOf(showing(now - 3600, now, "Just over", feeds = 4)), now, "x"))
    }

    @Test
    fun `a match starting exactly now is on`() {
        val match = assertNotNull(teamMatch(listOf(showing(now, now + 3600, "Kick off", feeds = 4)), now, "x"))
        assertTrue(match.onNow)
    }

    // Telling a fixture from a mention of the name.

    @Test
    fun `a fixture beats a documentary that merely says the name`() {
        // Real: searching "Belgium" with no match on found a History
        // programme, which with nothing to outrank it won by default and made
        // the row look like a fixture listing when it was not.
        val found = listOf(
            showing(now - 600, now + 3000, "The Fall of Belgium", feeds = 3),
            showing(now - 600, now + 3000, "Belgium v France", feeds = 1),
        )
        assertEquals("Belgium v France", assertNotNull(teamMatch(found, now, team)).showing.title)
    }

    @Test
    fun `a fixture later beats a mention sooner`() {
        // What anyone wants from a team row is the next time they play, not
        // the next time they are named.
        val found = listOf(
            showing(now + 600, now + 4200, "Belgium: A History", feeds = 4),
            showing(now + 36_000, now + 43_200, "Belgium v Wales", feeds = 1),
        )
        val match = assertNotNull(teamMatch(found, now, team))
        assertEquals("Belgium v Wales", match.showing.title)
        assertTrue(!match.onNow)
    }

    @Test
    fun `between two fixtures the widest carried still wins`() {
        // The shape is a weak signal and sorts only above the feed count.
        val found = listOf(
            showing(now - 60, now + 3000, "Belgium v France", feeds = 2),
            showing(now - 60, now + 3000, "Belgium v France on the other one", feeds = 8),
        )
        assertEquals(
            "Belgium v France on the other one",
            assertNotNull(teamMatch(found, now, team)).showing.title,
        )
    }

    @Test
    fun `a provider writes the sides apart however it likes`() {
        // Measured on a real line, all four of these. The dash is the one
        // that cost a night: Germany played Greece, the guide wrote it
        // "Nogomet: UEFA Liga nacija (M): Greece - Germany", and the row
        // offered Bundesliga highlights on an Indian film channel instead.
        fun fixture(title: String, team: String) =
            showing(now, now + 3600, title, feeds = 1).looksLikeFixtureFor(team)

        assertTrue(fixture("Croatia v England", "England"))
        assertTrue(fixture("Man United vs Liverpool", "Liverpool"))
        assertTrue(fixture("Nogomet: UEFA Liga nacija (M): Greece - Germany", "Germany"))
        assertTrue(fixture("Slavia Prague / Lens", "Lens"))
        assertTrue(fixture("UEFA Nations League: Croatia v England", "Croatia"))
    }

    @Test
    fun `the team has to be one of the sides, not just in the title`() {
        // What makes the wider set of separators safe. Titles are full of
        // dashes and slashes that separate nothing: an earlier rule refused
        // to recognise them at all because of it.
        fun fixture(title: String, team: String) =
            showing(now, now + 3600, title, feeds = 1).looksLikeFixtureFor(team)

        // France is in the description of this one on a real line, never a side.
        assertFalse(fixture("Slavia Prague / Lens", "France"))
        assertFalse(fixture("UEFA Nations League 2026/27 - Match Day 3", "Germany"))
        assertFalse(fixture("Bundesliga Highlights", "Germany"))
        assertFalse(fixture("World War II With Tom Hanks", "Germany"))
        // Not invented: it came up searching England on a real line.
        assertFalse(fixture("V for Vendetta", "England"))
        // A hyphen inside a word separates nothing.
        assertFalse(fixture("U-Boat Wargamers", "Boat"))
    }

    @Test
    fun `a fixture tonight beats a mention on now`() {
        // The ordering that mattered most, and it was wrong the first time.
        // "France" found France24's sports bulletin and a motocross
        // championnat on now — both merely saying the word — and they beat
        // France v Italy that same evening, which is plainly the wrong answer
        // to "where are my team on".
        val team = "France"
        val found = listOf(
            showing(now - 600, now + 3000, "Sports", feeds = 3),
            showing(now - 600, now + 3000, "Motocross: Championnat de France", feeds = 2),
            showing(now + 7200, now + 10800, "France vs Italy - UEFA Nations League", feeds = 1),
        )
        val match = assertNotNull(teamMatch(found, now, team))
        assertEquals("France vs Italy - UEFA Nations League", match.showing.title)
        assertTrue(!match.onNow)
    }

    @Test
    fun `a fixture on now beats a fixture later`() {
        // Among fixtures the old ordering stands: the one being watched is
        // the one a dying feed is interrupting.
        val found = listOf(
            showing(now - 600, now + 3000, "Belgium v Wales", feeds = 2),
            showing(now + 7200, now + 10800, "Belgium v Spain", feeds = 6),
        )
        val match = assertNotNull(teamMatch(found, now, team))
        assertEquals("Belgium v Wales", match.showing.title)
        assertTrue(match.onNow)
    }

    @Test
    fun `with no fixture at all, what is on now is still shown`() {
        // A mention is worth showing when there is nothing better, just not
        // in preference to a real match.
        val found = listOf(
            showing(now - 600, now + 3000, "Belgium: A History", feeds = 1),
            showing(now + 7200, now + 10800, "Alien Files Reopened", feeds = 2),
        )
        val match = assertNotNull(teamMatch(found, now, team))
        assertEquals("Belgium: A History", match.showing.title)
        assertTrue(match.onNow)
    }

    // The name.

    @Test
    fun `a name is trimmed and its spaces collapsed`() {
        assertEquals("Man Utd", cleanTeamName("  Man   Utd "))
    }

    @Test
    fun `a name is cut to what a row can draw`() {
        assertEquals(MAX_TEAM_NAME, cleanTeamName("x".repeat(100)).length)
    }

    @Test
    fun `blank is how the row is turned off`() {
        // No separate switch to fall out of step with the name.
        assertEquals("", cleanTeamName("   "))
        assertEquals("", cleanTeamName(""))
    }

    @Test
    fun `what was typed is otherwise left alone`() {
        // "Man Utd" and "Manchester United" are both reasonable, and only the
        // provider's own wording decides which finds anything.
        assertEquals("Manchester United", cleanTeamName("Manchester United"))
        assertEquals("1. FC Köln", cleanTeamName("1. FC Köln"))
    }

    // When a match is on, in words. The owner's report: a Champions League
    // tie showing "11:00 – 13:00" and nothing saying which day.

    private val london = java.time.ZoneId.of("Europe/London")
    private val uk = java.util.Locale.UK

    /** Unix seconds for a local time in London, so the cases read as dates. */
    private fun at(date: String, time: String): Long =
        java.time.LocalDateTime.parse("${date}T$time")
            .atZone(london).toEpochSecond()

    @Test
    fun `today is named rather than left to be inferred`() {
        // Not "11:00 – 13:00". The only thing placing that was the ON NOW
        // tag, which is an inference made at a glance by somebody who has
        // just lost a picture.
        assertEquals(
            "Today 11:00 – 13:00",
            matchWhenLabel(
                startSeconds = at("2026-10-06", "11:00"),
                stopSeconds = at("2026-10-06", "13:00"),
                nowSeconds = at("2026-10-06", "12:15"),
                zone = london,
                locale = uk,
            ),
        )
    }

    @Test
    fun `tomorrow is a word, not a date`() {
        assertEquals(
            "Tomorrow 20:00 – 22:00",
            matchWhenLabel(
                startSeconds = at("2026-10-07", "20:00"),
                stopSeconds = at("2026-10-07", "22:00"),
                nowSeconds = at("2026-10-06", "12:15"),
                zone = london,
                locale = uk,
            ),
        )
    }

    @Test
    fun `further off gets the weekday as well as the date`() {
        // "11 Oct" alone does not answer "is that the weekend".
        assertEquals(
            "Sun 11 Oct, 15:00 – 17:00",
            matchWhenLabel(
                startSeconds = at("2026-10-11", "15:00"),
                stopSeconds = at("2026-10-11", "17:00"),
                nowSeconds = at("2026-10-06", "12:15"),
                zone = london,
                locale = uk,
            ),
        )
    }

    @Test
    fun `a match that began last night is yesterday, and still on`() {
        // The one case that needs it: kick-off at 23:30, half past midnight
        // now, and the row is showing it as on now.
        assertEquals(
            "Yesterday 23:30 – 01:30",
            matchWhenLabel(
                startSeconds = at("2026-10-05", "23:30"),
                stopSeconds = at("2026-10-06", "01:30"),
                nowSeconds = at("2026-10-06", "00:30"),
                zone = london,
                locale = uk,
            ),
        )
    }

    @Test
    fun `the day is the viewer's, not the clock's`() {
        // 23:00 in London on the 6th is 00:00 on the 7th in Berlin, and the
        // row must say what the person reading it would say.
        val start = at("2026-10-06", "23:00")
        val stop = at("2026-10-07", "01:00")
        val now = at("2026-10-06", "22:00")
        assertTrue(matchWhenLabel(start, stop, now, london, uk).startsWith("Today "))
        assertTrue(
            matchWhenLabel(start, stop, now, java.time.ZoneId.of("Europe/Berlin"), uk)
                .startsWith("Tomorrow "),
        )
    }

    // A repeat ranks below a real fixture. Found on the owner's box: Arsenal
    // were not playing at all, and the row announced "Napoli vs. Arsenal, ON
    // NOW" -- a first-matchday Champions League tie from three weeks before,
    // replayed on a Chilean feed at eleven in the morning.

    private fun fixture(
        start: Long,
        stop: Long,
        title: String,
        feeds: Int = 1,
        repeat: Boolean = false,
    ) = Showing(
        title = title,
        start = start,
        stop = stop,
        channels = (1..feeds).map { "ch$it.$title" },
        repeat = repeat,
    )

    @Test
    fun `a real fixture tonight beats a replay on now`() {
        val match = teamMatch(
            showings = listOf(
                fixture(900, 1800, "Napoli vs. Arsenal", repeat = true),
                fixture(5000, 7000, "Arsenal vs. Brighton"),
            ),
            nowSeconds = 1000,
            team = "Arsenal",
        )
        assertNotNull(match)
        assertEquals("Arsenal vs. Brighton", match.showing.title)
        assertFalse(match.onNow)
        assertFalse(match.repeat)
    }

    @Test
    fun `a replay is still shown when there is nothing else, and says so`() {
        // Better than an empty row: it is the team, and the viewer can see
        // what it is rather than being told it is on now.
        val match = teamMatch(
            showings = listOf(fixture(900, 1800, "Napoli vs. Arsenal", repeat = true)),
            nowSeconds = 1000,
            team = "Arsenal",
        )
        assertNotNull(match)
        assertTrue(match.onNow)
        assertTrue(match.isFixture)
        assertTrue(match.repeat)
    }

    @Test
    fun `a live match on now still beats a fixture to come`() {
        // The ordering this feature exists for, unchanged: a feed dying
        // mid-match is interrupting the thing on now.
        val match = teamMatch(
            showings = listOf(
                fixture(900, 1800, "Napoli vs. Arsenal"),
                fixture(5000, 7000, "Arsenal vs. Brighton"),
            ),
            nowSeconds = 1000,
            team = "Arsenal",
        )
        assertNotNull(match)
        assertEquals("Napoli vs. Arsenal", match.showing.title)
        assertTrue(match.onNow)
        assertFalse(match.repeat)
    }

    @Test
    fun `one channel carrying it live is enough to make it live`() {
        // A fixture replayed on one channel and shown live on another is
        // being played somewhere, and that is the one to switch to. Hence
        // Showing.repeat being all of them rather than any.
        val event = listOf(
            XmltvProgramme("a", 900, 1800, "Napoli vs. Arsenal", "", repeat = true),
            XmltvProgramme("b", 900, 1800, "Napoli vs. Arsenal", "", repeat = false),
        ).showings().single()
        assertFalse(event.repeat)
    }

    @Test
    fun `a repeat beats a mere mention`() {
        val match = teamMatch(
            showings = listOf(
                fixture(900, 1800, "Napoli vs. Arsenal", repeat = true),
                fixture(900, 1800, "Premier League Legends: Arsenal"),
            ),
            nowSeconds = 1000,
            team = "Arsenal",
        )
        assertNotNull(match)
        assertEquals("Napoli vs. Arsenal", match.showing.title)
        assertTrue(match.isFixture)
    }
}
