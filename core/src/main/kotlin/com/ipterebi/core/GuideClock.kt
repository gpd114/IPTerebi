package com.ipterebi.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * Puts a line's guide back on the right clock when its panel has it wrong.
 *
 * `start_timestamp` is meant to be unix seconds, and everything here reads it
 * rather than the `start` string, which is the panel's wall clock in no stated
 * zone. But a real UK line sent Saturday Night Football as `"18:00:00"` with a
 * timestamp for 18:00 *UTC* — 19:00 in the UK in summer — so the match being
 * played was listed as an hour away, and "now" showed nothing. The panel had
 * written UK wall-clock times down as if they were UTC. Every programme on the
 * line was an hour late, for as long as the clocks were forward.
 *
 * The string cannot simply be trusted instead: a panel in another zone writes
 * its own wall clock there, with timestamps that are right. What tells the two
 * apart is `get_short_epg` itself, which answers from the programme the panel
 * thinks is on now. So a guide is corrected only when **nothing in it is on
 * now, but moving it by the gap between its strings (read in this device's
 * zone) and its timestamps puts its first programme on now** — and that gap is
 * the same for every programme, a whole quarter-hour, and under fourteen hours.
 * A panel with honest timestamps has its current programme on now already, and
 * is never touched.
 *
 * The shift is kept once learned, per line — it is a property of the panel —
 * so a channel whose own listing happens to have a gap is still put right.
 */
class GuideClock {
    private val learned = HashMap<String, Long>()

    /**
     * [listings] from [line], as they should be read at [now] in [zone]:
     * shifted when the panel's timestamps are out, as they came otherwise.
     */
    @Synchronized
    fun correct(line: String, listings: List<EpgListing>, now: Instant, zone: ZoneId): List<EpgListing> {
        if (listings.isEmpty() || listings.any { it.isOnAt(now) }) return listings
        val shift = guideShiftSeconds(listings, now, zone).takeIf { it != 0L }
            ?.also { learned[line] = it }
            ?: learned[line]
            ?: return listings
        return listings.map { it.shiftedBy(shift) }
    }

    /** The shift learned for [line], in seconds; 0 when none. For logs. */
    @Synchronized
    fun shiftFor(line: String): Long = learned[line] ?: 0L
}

/**
 * How many seconds to add to [listings]' timestamps to put them right, or 0
 * when they look right or the evidence is not there. See [GuideClock].
 */
fun guideShiftSeconds(listings: List<EpgListing>, now: Instant, zone: ZoneId): Long {
    val timed = listings.filter { it.hasKnownTimes }
    if (timed.isEmpty() || timed.any { it.isOnAt(now) }) return 0
    val gaps = timed.map { p -> p.start.wallClockSeconds(zone)?.minus(p.startTimestamp) ?: return 0 }
    val shift = gaps.first()
    val plausible = shift != 0L && gaps.all { it == shift } &&
        shift % QUARTER_HOUR == 0L && abs(shift) <= MAX_SHIFT
    if (!plausible) return 0
    return if (timed.minBy { it.startTimestamp }.shiftedBy(shift).isOnAt(now)) shift else 0
}

fun EpgListing.shiftedBy(seconds: Long): EpgListing =
    copy(startTimestamp = startTimestamp + seconds, stopTimestamp = stopTimestamp + seconds)

/** `"2026-09-12 18:00:00"` as unix seconds in [zone], or null for anything else. */
private fun String.wallClockSeconds(zone: ZoneId): Long? = try {
    LocalDateTime.parse(trim(), WALL_CLOCK).atZone(zone).toEpochSecond()
} catch (e: Exception) {
    null
}

private val WALL_CLOCK = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private const val QUARTER_HOUR = 15 * 60L
private const val MAX_SHIFT = 14 * 3600L
