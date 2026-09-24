package com.ipterebi.core

import kotlinx.serialization.Serializable

/**
 * Where you got to in a film or an episode, and the rules about which of those
 * are worth offering back.
 *
 * A live channel has no position worth keeping — you rejoin it at the live
 * edge, which is why this is only about files. Everything here is decided
 * without an Android class on purpose: what counts as finished, what counts as
 * never really started, and how long is left are the parts that can be got
 * wrong quietly, so they are pinned by tests rather than by looking at a phone.
 */
@Serializable
data class WatchedItem(
    val kind: WatchKind,
    /**
     * The panel's id for it, as a string in both cases: a film's is a number
     * and an episode's is not, and this list holds both.
     */
    val id: String,
    val name: String,
    /** "S02E04", or whatever belongs under the name. May be blank. */
    val detail: String = "",
    /** The poster or thumbnail the panel gave, if it gave one. */
    val poster: String = "",
    /**
     * The container the panel holds it in — mp4, mkv, avi. Kept because the
     * URL cannot be built without it, and the list it came from may be long
     * gone by the time someone taps this.
     */
    val extension: String,
    val positionMs: Long,
    /** 0 when the player never learned it, which happens if it failed early. */
    val durationMs: Long,
    /** When it was last watched, unix milliseconds. Newest first in the list. */
    val watchedAt: Long,
) {
    /** How far in, 0f to 1f. 0f when the length is unknown, rather than a guess. */
    val fraction: Float
        get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    val remainingMs: Long get() = (durationMs - positionMs).coerceAtLeast(0)

    /**
     * Whether this has been watched to the end, near enough.
     *
     * The tail is the *smaller* of two minutes and a twentieth of the whole,
     * and that second half is not theory: the fake panel's test film is 90
     * seconds long, and a flat two-minute tail marked it finished 30 seconds
     * in — it could never be carried on with at all. A twenty-minute episode
     * has the same problem in miniature. Two minutes is about how long credits
     * run on a feature; a twentieth is what that is as a proportion, and short
     * things need the proportion.
     */
    val isFinished: Boolean
        get() = durationMs > 0 && remainingMs <= minOf(FINISHED_TAIL_MS, durationMs / 20)

    /**
     * Whether it got far enough in to be worth offering back. A tap on the
     * wrong poster, watched for ten seconds and left, should not take up the
     * row — and on a line where several things fail to play at all, it would
     * otherwise fill with things nobody watched.
     */
    val isStarted: Boolean get() = positionMs >= MIN_KEEP_MS
}

@Serializable
enum class WatchKind { FILM, EPISODE }

/** Under this, it was not watching — it was a mis-tap or a stream that refused. */
const val MIN_KEEP_MS = 30_000L

/**
 * The longest the end-tail gets. Credits on a feature run about this long; on
 * anything short, [WatchedItem.isFinished] uses a twentieth of its length
 * instead, which is less.
 */
const val FINISHED_TAIL_MS = 120_000L

/**
 * How many are kept. Long enough that a fortnight of evenings is still there,
 * short enough that the row is a row and not an archive.
 */
const val MAX_CONTINUE = 20

/**
 * Records where something got to, newest first.
 *
 * Replaces any earlier record of the same thing rather than adding a second —
 * matched on kind and id, never on the name, because providers rename things
 * constantly and matching a whole record would leave two copies of one film on
 * the row the next time a provider tidied up.
 *
 * Something finished, or barely started, is dropped rather than stored: the
 * list is "what to carry on with", so a thing that needs neither has no place
 * in it. Dropping is what makes finishing a film clear it away.
 */
fun List<WatchedItem>.withWatched(item: WatchedItem): List<WatchedItem> {
    val rest = filterNot { it.kind == item.kind && it.id == item.id }
    if (item.isFinished || !item.isStarted) return rest.take(MAX_CONTINUE)
    return (listOf(item) + rest).sortedByDescending { it.watchedAt }.take(MAX_CONTINUE)
}

/** Just the films, or just the episodes — one row each on the home screen. */
fun List<WatchedItem>.ofKind(kind: WatchKind): List<WatchedItem> =
    filter { it.kind == kind }.sortedByDescending { it.watchedAt }

/** Where to start playing this, or 0 if there is nothing to carry on from. */
fun List<WatchedItem>.resumeAt(kind: WatchKind, id: String): Long =
    firstOrNull { it.kind == kind && it.id == id }
        ?.takeIf { it.isStarted && !it.isFinished }
        ?.positionMs
        ?: 0L

/**
 * "34m left", "1h 12m left".
 *
 * Rounded, because a film is not a train: "1h 12m left" is what someone wants
 * to know, and "1h 11m 48s left" is noise. Anything under a minute says so in
 * words rather than showing "0m left", which reads as a fault.
 */
fun remainingLabel(remainingMs: Long): String {
    if (remainingMs < 60_000) return "under a minute left"
    val minutes = remainingMs / 60_000
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0L -> "${minutes}m left"
        rest == 0L -> "${hours}h left"
        else -> "${hours}h ${rest}m left"
    }
}
