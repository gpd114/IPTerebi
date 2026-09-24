package com.ipterebi.core

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Watching a programme that has already been on.
 *
 * Providers that keep a recording expose it on two fields of a channel —
 * `tv_archive` and `tv_archive_duration` — and one URL shape. Everything about
 * which of those URLs to ask for, and for which programmes, is decided here,
 * because it is decidable without a device and because every part of it is a
 * chance to be subtly wrong about time.
 *
 * The subtle part: **the start time in a catch-up URL is the panel's wall
 * clock**, not UTC and not the viewer's. It is the same wall clock that makes
 * `get_short_epg` arrive hours out, and it has to be undone in the same
 * direction — see [GuideClock], which learns that shift from evidence. Sending
 * a time in the viewer's zone to a panel two hours ahead does not fail: it
 * plays the wrong two hours of television, which is far harder to notice.
 */

/** The format a panel wants a catch-up start in: `2026-09-24:19-30`. */
private val CATCH_UP_TIME: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd:HH-mm", Locale.ROOT)

/**
 * The start of a catch-up, written the way a panel reads it.
 *
 * [shiftSeconds] is what [GuideClock] learned, in its own units — seconds,
 * the correction *added* to a
 * panel's own timestamps to get real time. This undoes it, because we are
 * going the other way — from a real instant to what that panel calls it.
 */
fun catchUpStartLabel(startSeconds: Long, shiftSeconds: Long): String =
    Instant.ofEpochSecond(startSeconds - shiftSeconds)
        .atZone(ZoneOffset.UTC)
        .format(CATCH_UP_TIME)

/**
 * How long to ask for, in whole minutes, rounded up.
 *
 * Rounded up because a panel serves exactly what it is asked for: ask for 59
 * minutes of an hour-long programme and it stops a minute early, every time,
 * on every programme.
 */
fun catchUpMinutes(startSeconds: Long, stopSeconds: Long): Int {
    val seconds = (stopSeconds - startSeconds).coerceAtLeast(0)
    return ((seconds + 59) / 60).toInt().coerceAtLeast(1)
}

/**
 * Where a catch-up of this programme would actually start, or null when there
 * is nothing to watch back.
 *
 * One rule in one place, because three things have to hold and each of them
 * has been got wrong by somebody:
 *
 * - **the channel keeps an archive at all**, and keeps it for more than zero
 *   days — panels send `tv_archive: 1` with a duration of 0, which is a
 *   promise of nothing
 * - **the programme has finished.** A panel will serve a catch-up that runs
 *   into the future, and what comes back is the recording stopping dead at the
 *   live edge with the player waiting for more
 * - **some of it is still inside the window**, which is counted from now — so
 *   a programme falls out of it while someone is looking at the list
 *
 * The last of those is why this returns a time rather than a yes. A three-hour
 * film that began four days and one hour ago, on a line keeping four days, has
 * two hours of itself left; asking from its own start gets a refusal or
 * silence, and asking from the window's edge gets the rest of it.
 */
fun catchUpFrom(
    channel: LiveStream,
    startSeconds: Long,
    stopSeconds: Long,
    nowSeconds: Long,
): Long? {
    if (!channel.hasCatchUp) return null
    if (stopSeconds > nowSeconds) return null
    val oldest = nowSeconds - channel.tvArchiveDays * 24L * 60L * 60L
    return when {
        startSeconds >= oldest -> startSeconds
        stopSeconds > oldest -> oldest
        else -> null
    }
}

/** Whether to offer a catch-up of this programme at all. */
fun canCatchUp(
    channel: LiveStream,
    startSeconds: Long,
    stopSeconds: Long,
    nowSeconds: Long,
): Boolean = catchUpFrom(channel, startSeconds, stopSeconds, nowSeconds) != null
