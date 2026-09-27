package com.ipterebi.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Watching back something that has already been on.
 *
 * Every one of these is a way to be quietly wrong rather than visibly broken:
 * a catch-up that plays the wrong hour, stops a minute early, or is offered
 * for a programme the provider threw away yesterday all look like the app
 * working until somebody watches.
 */
class CatchUpTest {

    private val day = 24L * 60 * 60

    private fun channel(archive: Int = 1, days: Int = 4) = LiveStream(
        streamId = 101,
        name = "A Channel",
        tvArchive = archive,
        tvArchiveDays = days,
    )

    @Test
    fun `a channel offers catch-up only when it keeps something for more than no days`() {
        assertTrue(channel(archive = 1, days = 4).hasCatchUp)
        assertFalse(channel(archive = 0, days = 4).hasCatchUp)
        // Panels really do send this: archive on, kept for zero days. Offering
        // it would be a button that always fails.
        assertFalse(channel(archive = 1, days = 0).hasCatchUp)
    }

    @Test
    fun `the start is written in the panel's own wall clock, not ours`() {
        // 2026-09-24 19:00:00 UTC. Derived rather than assumed, twice over:
        // the first number here was two days out and the second two hours,
        // and both times every assertion still read plausibly.
        val start = 1790276400L
        // A panel two hours ahead writes its local time as if it were UTC, so
        // GuideClock learned -7200 seconds: add that to a panel timestamp to
        // get real time. Going the other way, the panel calls it 21:00.
        assertEquals("2026-09-24:21-00", catchUpStartLabel(start, shiftSeconds = -7200))
        // A panel telling the truth gets the truth back.
        assertEquals("2026-09-24:19-00", catchUpStartLabel(start, shiftSeconds = 0))
        // And one behind us goes the other way.
        assertEquals("2026-09-24:18-00", catchUpStartLabel(start, shiftSeconds = 3600))
    }

    @Test
    fun `a length is whole minutes, rounded up`() {
        assertEquals(60, catchUpMinutes(0, 60 * 60))
        // Rounded up: asking for 59 of 59 and a half minutes stops a panel
        // half a minute early, on every programme, for ever.
        assertEquals(60, catchUpMinutes(0, 59 * 60 + 30))
        assertEquals(1, catchUpMinutes(0, 5))
        // Nothing sensible to ask for, but never zero or negative: a zero
        // asks a panel for a recording of no length and gets an error page.
        assertEquals(1, catchUpMinutes(100, 50))
    }

    @Test
    fun `something still on cannot be caught up`() {
        val now = 1790103600L
        // Started an hour ago, has half an hour to run.
        assertFalse(canCatchUp(channel(), now - 3600, now + 1800, now))
        // The moment it ends, it can.
        assertTrue(canCatchUp(channel(), now - 3600, now, now))
    }

    @Test
    fun `a programme older than the window is gone`() {
        val now = 1790103600L
        val fiveDaysAgo = now - 5 * day
        assertNull(catchUpFrom(channel(days = 4), fiveDaysAgo, fiveDaysAgo + 3600, now))
        // Inside four days, it is there.
        val threeDaysAgo = now - 3 * day
        assertEquals(threeDaysAgo, catchUpFrom(channel(days = 4), threeDaysAgo, threeDaysAgo + 3600, now))
    }

    @Test
    fun `something half out of the window starts at the edge`() {
        val now = 1790103600L
        val oldest = now - 4 * day
        // A three-hour film that began an hour before the window opens: two
        // hours of it are still kept. Asking from its own start gets nothing.
        val start = oldest - 3600
        val stop = start + 3 * 3600
        assertEquals(oldest, catchUpFrom(channel(days = 4), start, stop, now))
    }

    @Test
    fun `a channel with no archive offers nothing, however recent`() {
        val now = 1790103600L
        assertNull(catchUpFrom(channel(archive = 0), now - 3600, now - 1800, now))
    }

    @Test
    fun `the window moves, so what was offered a minute ago may not be now`() {
        // The case that makes this worth a function rather than a field: the
        // list is drawn once and looked at for a while.
        val start = 1790269200L
        val stop = start + 3600
        val justInside = start + 4 * day - 60
        val justOutside = stop + 4 * day + 60

        assertTrue(canCatchUp(channel(days = 4), start, stop, justInside))
        assertFalse(canCatchUp(channel(days = 4), start, stop, justOutside))
    }
}

/** The URL a catch-up is fetched from, which no API call will tell you. */
class CatchUpUrlTest {

    private val client = XtreamClient()

    @Test
    fun `the path carries the length and the start, and the credentials`() {
        val account = XtreamAccount(base = "http://panel.example:8080", username = "demo", password = "secret")
        val url = client.catchUpUrl(
            account = account,
            streamId = 101,
            startSeconds = 1790276400L,
            minutes = 60,
            shiftSeconds = 0,
        )

        assertEquals(
            "http://panel.example:8080/timeshift/demo/secret/60/2026-09-24:19-00/101.ts",
            url,
        )
    }

    @Test
    fun `a password that would invent a path is encoded instead`() {
        // The same bargain the live URL makes: these are path segments, so a
        // password with a slash in it must not become two segments.
        val account = XtreamAccount(base = "http://panel.example:8080", username = "de mo", password = "a/b")
        val url = client.catchUpUrl(account, 101, 1790276400L, 60, 0L)

        assertTrue(url.contains("/de%20mo/a%2Fb/"), url)
    }

    @Test
    fun `the panel's shift lands in the URL, not just in the guide`() {
        val account = XtreamAccount(base = "http://panel.example:8080", username = "demo", password = "demo")
        val url = client.catchUpUrl(account, 101, 1790276400L, 30, shiftSeconds = -7200)

        // The instant is 19:00; this panel calls it 21:00, so that is what it
        // must be asked for. Asking for 19:00 would play two hours early and
        // look like nothing worse than the wrong programme.
        assertTrue(url.contains("/2026-09-24:21-00/"), url)
    }
}

/**
 * The panel stating its own clock, which is the only source that does not
 * need `get_short_epg` to answer — and on the owner's box it never does.
 */
class PanelClockTest {

    @Test
    fun `a panel two hours ahead of UTC says so in its own two fields`() {
        // 2026-09-27 07:46:00 UTC, which this panel calls 09:46.
        val stamp = 1790495160L
        assertEquals(-7200L, panelClockShift("2026-09-27 09:46:00", stamp.toString()))
    }

    @Test
    fun `a panel telling the truth gives zero, and is then never shifted`() {
        val stamp = 1790495160L
        assertEquals(0L, panelClockShift("2026-09-27 07:46:00", stamp.toString()))
    }

    @Test
    fun `a panel behind UTC goes the other way`() {
        val stamp = 1790495160L
        assertEquals(18000L, panelClockShift("2026-09-27 02:46:00", stamp.toString()))
    }

    @Test
    fun `it is the shift catch-up wants, in the direction catch-up wants it`() {
        // Round trip: a panel two hours ahead, a programme at 19:00 UTC, and
        // the label that panel must be asked for.
        val shift = panelClockShift("2026-09-27 09:46:00", "1790495160")!!
        assertEquals("2026-09-24:21-00", catchUpStartLabel(1790276400L, shift))
    }

    @Test
    fun `anything missing or unreadable answers null rather than a guess`() {
        assertNull(panelClockShift("", "1790495160"))
        assertNull(panelClockShift("2026-09-27 09:46:00", ""))
        assertNull(panelClockShift("not a time", "1790495160"))
        assertNull(panelClockShift("2026-09-27 09:46:00", "not a number"))
    }

    @Test
    fun `a pair that disagrees by more than half a day is not an offset`() {
        // No timezone is thirteen hours from UTC in this direction, so the
        // panel is broken rather than distant, and 0 beats a day out.
        assertNull(panelClockShift("2026-09-27 20:46:00", "1790495160"))
    }
}
