package com.ipterebi.core

import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * The first real line's guide, two hours late in `get_short_epg`, and guides
 * that are fine. Times are UTC; the UK was an hour ahead.
 */
class GuideClockTest {

    /** 18:04 in the UK: the match, which kicked off at 17:30, is on. */
    private val now = Instant.parse("2026-09-12T17:04:00Z")

    private fun at(iso: String) = Instant.parse(iso).epochSecond

    private fun short(title: String, from: String, to: String) = EpgListing(
        title = Base64.getEncoder().encodeToString(title.toByteArray()),
        startTimestamp = at(from),
        stopTimestamp = at(to),
    )

    private fun full(title: String, from: String, to: String, channel: String = "ssme") =
        XmltvProgramme(channel, at(from), at(to), title, "")

    /** What get_short_epg sent for Sky Sports Main Event: really 16:00–19:00. */
    private val matchShort = listOf(
        short("Saturday Night Football", "2026-09-12T18:00:00Z", "2026-09-12T21:00:00Z"),
        short("US Open 2026", "2026-09-12T21:00:00Z", "2026-09-13T00:00:00Z"),
    )

    /** What xmltv.php sent for the same channel, which was right. */
    private val matchFull = listOf(
        full("Saturday Night Football", "2026-09-12T16:00:00Z", "2026-09-12T19:00:00Z"),
        full("US Open 2026", "2026-09-12T19:00:00Z", "2026-09-12T22:00:00Z"),
    )

    @Test
    fun `the full guide gives the error exactly - two hours, not one`() {
        assertEquals(-7200L, shiftAgainst(matchShort, matchFull))
        val clock = GuideClock()
        clock.learnFrom("line", matchShort, matchFull)
        val fixed = clock.correct("line", "ssme", matchShort, now)
        assertEquals(at("2026-09-12T16:00:00Z"), fixed.first().startTimestamp)
        assertEquals("Saturday Night Football", fixed.nowAndNext(now).now?.titleText)
    }

    @Test
    fun `once learned, every channel on the line is put right`() {
        val clock = GuideClock()
        clock.learnFrom("line", matchShort, matchFull)
        val news = listOf(short("Headlines", "2026-09-12T19:00:00Z", "2026-09-12T20:00:00Z"))
        assertEquals(at("2026-09-12T17:00:00Z"), clock.correct("line", "news", news, now).single().startTimestamp)
    }

    @Test
    fun `a line the full guide shows to be right is never shifted`() {
        val honest = listOf(short("Saturday Night Football", "2026-09-12T16:00:00Z", "2026-09-12T19:00:00Z"))
        val clock = GuideClock()
        clock.learnFrom("line", honest, matchFull)
        assertEquals(0L, clock.shiftFor("line"))
        // Even a channel whose answer is honestly all in the future.
        val later = listOf(short("Film", "2026-09-12T20:00:00Z", "2026-09-12T22:00:00Z"))
        assertSame(later, clock.correct("line", "films", later, now))
    }

    @Test
    fun `one channel with nothing on is not enough to shift`() {
        // An honest panel can simply have a gap before its next programme.
        assertSame(matchShort, GuideClock().correct("line", "ssme", matchShort, now))
    }

    @Test
    fun `two channels that agree are enough, and the right hour is found`() {
        val clock = GuideClock()
        clock.correct("line", "ssme", matchShort, now)
        // Sky Sports News: really 17:00–18:00 UTC, sent two hours late.
        val news = listOf(short("Sky Sports News", "2026-09-12T19:00:00Z", "2026-09-12T20:00:00Z"))
        val fixed = clock.correct("line", "news", news, now)
        assertEquals(-7200L, clock.shiftFor("line"))
        assertEquals(at("2026-09-12T17:00:00Z"), fixed.single().startTimestamp)
    }

    @Test
    fun `channels whose evidence disagrees shift nothing`() {
        val clock = GuideClock()
        val a = listOf(short("A", "2026-09-12T17:30:00Z", "2026-09-12T18:00:00Z"))
        val b = listOf(short("B", "2026-09-12T21:00:00Z", "2026-09-12T21:30:00Z"))
        clock.correct("line", "a", a, now)
        assertSame(b, clock.correct("line", "b", b, now))
        assertEquals(0L, clock.shiftFor("line"))
    }

    @Test
    fun `an answer with its programme on now is left alone`() {
        val onNow = listOf(short("Now", "2026-09-12T17:00:00Z", "2026-09-12T18:00:00Z"))
        assertSame(onNow, GuideClock().correct("line", "x", onNow, now))
    }

    @Test
    fun `nothing shared with the full guide teaches nothing`() {
        assertNull(shiftAgainst(matchShort, listOf(full("Something else", "2026-09-12T16:00:00Z", "2026-09-12T19:00:00Z"))))
        // Same title, different length: not the same programme.
        assertNull(shiftAgainst(matchShort, listOf(full("Saturday Night Football", "2026-09-12T16:00:00Z", "2026-09-12T18:00:00Z"))))
    }

    @Test
    fun `lines learn separately and are forgotten on sign out`() {
        val clock = GuideClock()
        clock.learnFrom("line", matchShort, matchFull)
        assertEquals(0L, clock.shiftFor("other"))
        clock.forget("line")
        assertEquals(0L, clock.shiftFor("line"))
    }
}
