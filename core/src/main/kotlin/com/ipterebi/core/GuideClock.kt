package com.ipterebi.core

import java.time.Instant
import kotlin.math.abs

/**
 * Puts a line's `get_short_epg` answers back on the right clock when its panel
 * has it wrong.
 *
 * `start_timestamp` is meant to be unix seconds. On the first real line it was
 * two hours late: Saturday Night Football ran 17:00–20:00 UK time (kick-off
 * 17:30, confirmed by the viewer, and the same in the line's `xmltv.php`), but
 * `get_short_epg` sent it as `"18:00:00"` with a timestamp for 19:00 UK time.
 * The panel's own wall clock was two hours ahead of UTC and it wrote that
 * wall clock into the timestamps as if it were UTC.
 *
 * A first attempt read the strings in the viewer's zone and took the gap to
 * the timestamps as the error — an hour — and looked right, because a
 * three-hour programme covers either start. It was still an hour out. The
 * strings are in the panel's zone, which is unknown, so they prove nothing.
 * What the error really is comes from two kinds of evidence, best first:
 *
 * 1. **The full guide.** `xmltv.php` times carry their own offset. A programme
 *    found in both — same title, same length — gives the error exactly
 *    ([learnFrom]). A line whose timestamps are right learns 0 this way, and is
 *    then never shifted.
 * 2. **What `get_short_epg` answers from.** It starts from the programme the
 *    panel thinks is on now, so when nothing in an answer is on now, the error
 *    must put that first programme on now: a window as wide as the programme.
 *    One channel is not enough — an honest panel can simply have a gap — so a
 *    shift is only made once two channels' windows agree, and the whole hour
 *    nearest the middle of where they overlap is taken.
 */
class GuideClock {
    private val exact = HashMap<String, Long>()
    private val windows = HashMap<String, LinkedHashMap<String, LongRange>>()

    /**
     * Learns [line]'s error exactly from one channel's short answer and the
     * same channel's programmes in the full guide. Does nothing when no
     * programme is in both.
     */
    @Synchronized
    fun learnFrom(line: String, short: List<EpgListing>, full: List<XmltvProgramme>) {
        shiftAgainst(short, full)?.let { exact[line] = it }
    }

    /**
     * [listings], one [channel]'s short answer on [line], as they should be
     * read at [now]: shifted when the panel's clock is known, or agreed, to be
     * out; as they came otherwise.
     */
    @Synchronized
    fun correct(line: String, channel: String, listings: List<EpgListing>, now: Instant): List<EpgListing> {
        exact[line]?.let { shift -> return if (shift == 0L) listings else listings.map { it.shiftedBy(shift) } }
        val timed = listings.filter { it.hasKnownTimes }
        if (timed.isEmpty() || timed.any { it.isOnAt(now) }) return listings

        val first = timed.minBy { it.startTimestamp }
        val seconds = now.epochSecond
        val window = (seconds - first.stopTimestamp + 1)..(seconds - first.startTimestamp)
        val seen = windows.getOrPut(line) { LinkedHashMap() }
        seen.remove(channel)
        seen[channel] = window
        while (seen.size > MAX_WINDOWS) seen.remove(seen.keys.first())

        val shift = agreed(seen.values) ?: return listings
        return listings.map { it.shiftedBy(shift) }
    }

    /** Whether [line]'s error has been measured against the full guide — 0 included. */
    @Synchronized
    fun knows(line: String): Boolean = line in exact

    /** The shift in use for [line], in seconds; 0 when none. For logs. */
    @Synchronized
    fun shiftFor(line: String): Long = exact[line] ?: windows[line]?.values?.let(::agreed) ?: 0L

    /** Forgets [line], when it is signed out of. */
    @Synchronized
    fun forget(line: String) {
        exact.remove(line)
        windows.remove(line)
    }
}

/**
 * The error between a channel's short answer and the full guide, when they
 * share a programme — same title, same length — and every shared programme
 * says the same. Null when there is nothing to compare or they disagree.
 */
fun shiftAgainst(short: List<EpgListing>, full: List<XmltvProgramme>): Long? {
    val byTitle = full.groupBy { it.title.trim().lowercase() }
    val gaps = short.filter { it.hasKnownTimes }.flatMap { listing ->
        val length = listing.stopTimestamp - listing.startTimestamp
        byTitle[listing.titleText.trim().lowercase()].orEmpty()
            .filter { abs((it.stop - it.start) - length) < 60 }
            .map { it.start - listing.startTimestamp }
    }
    val shift = gaps.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: return null
    return shift.takeIf { it % QUARTER_HOUR == 0L && abs(it) <= MAX_SHIFT }
}

/**
 * What every window allows, as a whole hour nearest the middle of it — or a
 * quarter-hour, for the zones that are not whole hours — or null when there
 * are fewer than two windows or they do not overlap.
 */
private fun agreed(windows: Collection<LongRange>): Long? {
    if (windows.size < 2) return null
    val from = windows.maxOf { it.first }
    val to = windows.minOf { it.last }
    if (from > to) return null
    val middle = (from + to) / 2
    for (step in longArrayOf(3600L, QUARTER_HOUR)) {
        val candidates = (Math.floorDiv(from, step) - 1..Math.floorDiv(to, step) + 1)
            .map { it * step }
            .filter { it in from..to && it != 0L && abs(it) <= MAX_SHIFT }
        candidates.minByOrNull { abs(it - middle) }?.let { return it }
    }
    return null
}

fun EpgListing.shiftedBy(seconds: Long): EpgListing =
    copy(startTimestamp = startTimestamp + seconds, stopTimestamp = stopTimestamp + seconds)

private const val QUARTER_HOUR = 15 * 60L
private const val MAX_SHIFT = 14 * 3600L
private const val MAX_WINDOWS = 12
