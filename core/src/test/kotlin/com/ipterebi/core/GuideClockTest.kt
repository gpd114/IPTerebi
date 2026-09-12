package com.ipterebi.core

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** A guide whose panel wrote UK times down as UTC, and guides that are fine. */
class GuideClockTest {

    private val london = ZoneId.of("Europe/London")
    private val utc = ZoneId.of("UTC")

    /** 18:04 in the UK on a summer Saturday: 17:04 UTC. */
    private val now = Instant.parse("2026-09-12T17:04:00Z")

    private fun at(iso: String) = Instant.parse(iso).epochSecond

    private fun programme(start: String, end: String, startTs: Long, stopTs: Long, title: String = "") =
        EpgListing(title = title, start = start, end = end, startTimestamp = startTs, stopTimestamp = stopTs)

    /**
     * What the real line sent at 18:04 UK time, while the match was on: the
     * strings in UK time, the timestamps an hour late.
     */
    private val summerAsUtc = listOf(
        programme("2026-09-12 18:00:00", "2026-09-12 21:00:00", at("2026-09-12T18:00:00Z"), at("2026-09-12T21:00:00Z")),
        programme("2026-09-12 21:00:00", "2026-09-13 00:00:00", at("2026-09-12T21:00:00Z"), at("2026-09-13T00:00:00Z")),
        programme("2026-09-13 00:00:00", "2026-09-13 01:00:00", at("2026-09-13T00:00:00Z"), at("2026-09-13T01:00:00Z")),
    )

    @Test
    fun `a guide written in UK time as UTC is moved back an hour`() {
        assertEquals(-3600L, guideShiftSeconds(summerAsUtc, now, london))
        val fixed = GuideClock().correct("line", summerAsUtc, now, london)
        assertEquals(at("2026-09-12T17:00:00Z"), fixed.first().startTimestamp)
        assertEquals(fixed.first(), fixed.nowAndNext(now).now)
    }

    @Test
    fun `a guide with its programme on now is never touched`() {
        val honest = listOf(
            programme("2026-09-12 17:00:00", "2026-09-12 20:00:00", at("2026-09-12T17:00:00Z"), at("2026-09-12T20:00:00Z")),
        )
        // In the UK, the panel's UTC strings disagree with local time by an hour
        // too — which is exactly why the gap alone is not the test.
        assertEquals(0L, guideShiftSeconds(honest, now, london))
        assertSame(honest, GuideClock().correct("line", honest, now, london))
    }

    @Test
    fun `a guide that is honestly all in the future is left alone`() {
        // Strings and timestamps agree; there is just nothing listed until 19:00.
        val gap = listOf(
            programme("2026-09-12 19:00:00", "2026-09-12 20:00:00", at("2026-09-12T18:00:00Z"), at("2026-09-12T19:00:00Z")),
        )
        assertEquals(0L, guideShiftSeconds(gap, now, london))
    }

    @Test
    fun `a shift that would not put the first programme on now is not made`() {
        // The strings are an hour from the timestamps, but moving by that hour
        // still leaves nothing on: no evidence, so no change.
        val later = listOf(
            programme("2026-09-12 20:00:00", "2026-09-12 21:00:00", at("2026-09-12T20:00:00Z"), at("2026-09-12T21:00:00Z")),
        )
        assertEquals(0L, guideShiftSeconds(later, now, london))
    }

    @Test
    fun `gaps that disagree between programmes are not a clock error`() {
        val muddled = listOf(
            summerAsUtc[0],
            programme("2026-09-12 21:00:00", "2026-09-13 00:00:00", at("2026-09-12T22:30:00Z"), at("2026-09-13T00:00:00Z")),
        )
        assertEquals(0L, guideShiftSeconds(muddled, now, london))
    }

    @Test
    fun `unreadable strings mean no evidence`() {
        val bare = summerAsUtc.map { it.copy(start = "") }
        assertEquals(0L, guideShiftSeconds(bare, now, london))
    }

    @Test
    fun `a device in UTC watching the same line sees no shift`() {
        // Read in UTC the strings and timestamps agree, and nothing is on — so
        // there is no evidence, and nothing is moved.
        assertEquals(0L, guideShiftSeconds(summerAsUtc, now, utc))
    }

    @Test
    fun `the shift is kept for the line and put right a channel whose own listing has a gap`() {
        val clock = GuideClock()
        clock.correct("line", summerAsUtc, now, london)
        // Another channel on the same line, whose listing starts after its
        // current programme — nothing of its own to learn from.
        val gappy = listOf(
            programme("2026-09-12 18:30:00", "2026-09-12 19:00:00", at("2026-09-12T18:30:00Z"), at("2026-09-12T19:00:00Z")),
        )
        assertEquals(0L, guideShiftSeconds(gappy, now, london))
        assertEquals(at("2026-09-12T17:30:00Z"), clock.correct("line", gappy, now, london).first().startTimestamp)
        // Another line learns nothing from this one.
        assertSame(gappy, clock.correct("other line", gappy, now, london))
    }
}
