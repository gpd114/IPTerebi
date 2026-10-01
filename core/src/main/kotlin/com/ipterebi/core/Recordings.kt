package com.ipterebi.core

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Keeping a programme, on a line that allows one stream at a time.
 *
 * A panel offers no recording of its own: `tv_archive` is the provider's
 * recording, and catch-up plays it back. This is the other thing — the viewer
 * keeping a copy, which means opening the channel's stream and writing the
 * bytes to a file. Everything about *whether* a recording can happen, *when*
 * it runs and *what it is called* is decided here, because none of it needs an
 * Android class and all of it is a chance to be quietly wrong.
 *
 * Three facts shape every rule below, and all three were measured rather than
 * assumed:
 *
 * - **One connection is one recorder.** A line usually allows a single stream,
 *   so unlike a set-top box with two tuners there is no "record one, watch
 *   another". Two recordings that overlap *at all* cannot both happen, whatever
 *   channels they are on, and a recording in progress owns the line. This is
 *   the single biggest difference between this and every other PVR, and
 *   [clashOf] is where it lives.
 * - **A broadcast does not keep to its published time.** The catch-up work
 *   found a recording asked for at its scheduled start playing five to ten
 *   minutes of the previous programme: junctions and trailers do not read the
 *   guide. So a recording starts early and ends late by default, and the
 *   padding is part of the window rather than something the UI adds.
 * - **A USB stick is usually FAT32**, which cannot hold a file of 4 GB or
 *   more and refuses a name with `\ / : * ? " < > |` in it. A provider's
 *   channel names are full of colons and slashes — `UK: BBC ONE HD` — so a
 *   name built from one has to be cleaned or the recording fails at the moment
 *   it was supposed to start, which is the worst moment to find out.
 */

/** Where a recording has got to. */
@Serializable
enum class RecordingState {
    /** Waiting for its start. Nothing has been opened. */
    SCHEDULED,

    /** The stream is open and bytes are being written. */
    RECORDING,

    /** Finished, and the file is there to play. */
    DONE,

    /** It ran and something went wrong; [Recording.failure] says what. */
    FAILED,

    /** The viewer changed their mind, before or during. */
    CANCELLED,
}

/**
 * One thing the viewer asked to keep.
 *
 * [startSeconds] and [stopSeconds] are the *recording* window, padding
 * included, because that is what the clock is compared against and what the
 * clash rules work on. The programme's own times are kept beside them for
 * display: the row should say when the programme is on, not when the recorder
 * wakes up.
 *
 * Like everything else keyed on a panel's ids, a recording belongs to the line
 * it was made on — id 4271 is a different channel on the next provider. The
 * store is per line for the same reason `ListStore` and `WatchStore` are.
 */
@Serializable
data class Recording(
    /** Stable and derived, so asking twice for the same thing is one recording. */
    val id: String,
    val streamId: Int,
    val channelName: String,
    /** The programme's title, or the channel's name when recording started by hand. */
    val title: String,
    val description: String = "",
    /** The recording window, padding included. Unix seconds. */
    val startSeconds: Long,
    val stopSeconds: Long,
    /** What the guide said, for showing. 0 when it was started by hand. */
    val programmeStart: Long = 0,
    val programmeStop: Long = 0,
    val state: RecordingState = RecordingState.SCHEDULED,
    /** The document it is written to, once there is one. A SAF uri, as a string. */
    val document: String = "",
    /** What has been written. Grows while recording; final when done. */
    val bytes: Long = 0,
    /** Why it failed, in a sentence a viewer can act on. Empty unless FAILED. */
    val failure: String = "",
) {
    val durationSeconds: Long get() = (stopSeconds - startSeconds).coerceAtLeast(0)

    /** Still to come, still going, or over and done with. */
    val finished: Boolean
        get() = state == RecordingState.DONE ||
            state == RecordingState.FAILED ||
            state == RecordingState.CANCELLED

    /** True while this recording wants the line at [nowSeconds]. */
    fun wants(nowSeconds: Long): Boolean =
        !finished && nowSeconds >= startSeconds && nowSeconds < stopSeconds
}

/**
 * How early a recording starts and how late it ends.
 *
 * Not a preference, at least not yet: these are the numbers the catch-up work
 * measured a real broadcast drifting by. A minute before is enough for a late
 * junction; three after covers an overrun, which is the commoner way to lose
 * the end of a film.
 */
const val PAD_BEFORE_SECONDS = 60L
const val PAD_AFTER_SECONDS = 180L

/** The recording window for a programme: its times, opened out by the padding. */
fun recordingWindow(
    programmeStart: Long,
    programmeStop: Long,
    padBefore: Long = PAD_BEFORE_SECONDS,
    padAfter: Long = PAD_AFTER_SECONDS,
): LongRange = (programmeStart - padBefore)..(programmeStop + padAfter)

/**
 * The id a programme on a channel always gets.
 *
 * Derived rather than random so that asking twice — from the guide and then
 * again from the banner — is one recording rather than two that clash with
 * each other. The programme's own start is used, not the padded one, so
 * changing the padding later does not orphan what is already scheduled.
 */
fun recordingId(streamId: Int, programmeStart: Long): String = "$streamId@$programmeStart"

/** Do two recordings want the line at the same moment? */
fun overlaps(a: Recording, b: Recording): Boolean =
    a.startSeconds < b.stopSeconds && b.startSeconds < a.stopSeconds

/**
 * What stops [candidate] from being scheduled, or null if nothing does.
 *
 * The rule is the line, not the channel: anything already scheduled that
 * overlaps it is a clash, because there is one connection and it cannot be in
 * two places. Two overlapping recordings on the *same* channel are not a
 * clash — one open stream covers both, and [mergedWith] is what makes that
 * true. Finished recordings are not in anyone's way.
 */
fun clashOf(existing: List<Recording>, candidate: Recording): Recording? =
    existing.firstOrNull {
        !it.finished &&
            it.id != candidate.id &&
            it.streamId != candidate.streamId &&
            overlaps(it, candidate)
    }

/**
 * One recording covering both, for two that touch on the same channel.
 *
 * Recording two programmes back to back would otherwise clash with itself:
 * three minutes of padding after the first runs into a minute before the
 * second. The capture is one stream either way, so it becomes one recording
 * spanning both, named after the first — which is what someone who set two
 * halves of an evening would expect to find.
 */
fun mergedWith(a: Recording, b: Recording): Recording {
    require(a.streamId == b.streamId) { "only recordings of one channel merge" }
    val first = if (a.startSeconds <= b.startSeconds) a else b
    val second = if (first === a) b else a
    return first.copy(
        startSeconds = minOf(a.startSeconds, b.startSeconds),
        stopSeconds = maxOf(a.stopSeconds, b.stopSeconds),
        programmeStop = maxOf(a.programmeStop, b.programmeStop),
        title = if (first.title == second.title) first.title else "${first.title} + ${second.title}",
    )
}

/** What happened when a recording was asked for. */
sealed interface Scheduling {
    /** It went in. [all] is the new list, in start order. */
    data class Booked(val all: List<Recording>, val recording: Recording) : Scheduling

    /** It joined one already there on the same channel. */
    data class Merged(val all: List<Recording>, val recording: Recording) : Scheduling

    /** The line is already spoken for. [by] is what has it. */
    data class Clash(val by: Recording) : Scheduling
}

/**
 * Put [candidate] into [existing], or say why not.
 *
 * Merging is tried before clashing, so an evening of one channel books, and
 * two channels at once does not. Asking for something already scheduled is
 * not an error and not a duplicate — it is the same recording, returned.
 */
fun schedule(existing: List<Recording>, candidate: Recording): Scheduling {
    existing.firstOrNull { it.id == candidate.id && !it.finished }?.let {
        return Scheduling.Booked(existing, it)
    }
    clashOf(existing, candidate)?.let { return Scheduling.Clash(it) }

    val touching = existing.filter {
        !it.finished && it.streamId == candidate.streamId && overlaps(it, candidate)
    }
    if (touching.isEmpty()) {
        return Scheduling.Booked((existing + candidate).sortedBy { it.startSeconds }, candidate)
    }
    val one = touching.fold(candidate, ::mergedWith)
    val rest = existing.filterNot { r -> touching.any { it.id == r.id } }
    return Scheduling.Merged((rest + one).sortedBy { it.startSeconds }, one)
}

/**
 * The next recording to wake up for, or null when there is nothing to wait on.
 *
 * Anything already inside its window counts as next: a box that was asleep at
 * the start should join a recording late rather than skip it, since most of
 * the programme is still to come.
 */
fun nextToStart(recordings: List<Recording>, nowSeconds: Long): Recording? =
    recordings.filter { !it.finished && it.stopSeconds > nowSeconds }
        .minByOrNull { it.startSeconds }

/**
 * Roughly how many bytes a recording of this length will take.
 *
 * A guess, and deliberately a generous one: the only number available before
 * opening the stream is what the panel usually serves, and running out of room
 * halfway is worse than refusing. [bitsPerSecond] defaults to 8 Mbit, which is
 * above the 1080p the first real line served.
 */
fun bytesFor(seconds: Long, bitsPerSecond: Long = 8_000_000L): Long =
    seconds.coerceAtLeast(0) * bitsPerSecond / 8

/**
 * What a FAT32 volume can hold in one file: four gigabytes, less a byte.
 *
 * Worth knowing before the recording rather than during it. Most USB sticks
 * arrive formatted FAT32, and at 8 Mbit this is a little over an hour — so a
 * film can cross it, and crossing it is a write error two thirds of the way
 * through something nobody can watch any more.
 */
const val FAT32_MAX_BYTES = 4L * 1024 * 1024 * 1024 - 1

/** How much room to leave behind, so the volume never goes completely full. */
const val SPACE_HEADROOM_BYTES = 256L * 1024 * 1024

/** Is there room for [needed] bytes, leaving the headroom alone? */
fun roomFor(freeBytes: Long, needed: Long, headroom: Long = SPACE_HEADROOM_BYTES): Boolean =
    freeBytes - headroom >= needed

/**
 * The oldest finished recordings to delete to free [wanted] bytes, in order.
 *
 * Only finished ones, and never the one playing or being written. Returns an
 * empty list when deleting everything it may still would not be enough, so
 * the caller can say so rather than deleting a week of television for nothing.
 */
fun toDeleteFor(recordings: List<Recording>, wanted: Long): List<Recording> {
    val spare = recordings.filter { it.state == RecordingState.DONE }
        .sortedBy { it.startSeconds }
    var freed = 0L
    val taken = mutableListOf<Recording>()
    for (r in spare) {
        if (freed >= wanted) break
        taken += r
        freed += r.bytes
    }
    return if (freed >= wanted) taken else emptyList()
}

private val FILE_STAMP: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm", Locale.ROOT)

/** Characters FAT32 refuses, plus the ones a shell or a path would eat. */
private val UNSAFE = Regex("""[\\/:*?"<>|\u0000-\u001f]""")

/**
 * What the file is called on the stick.
 *
 * `UK- BBC ONE HD - Match of the Day - 2026-10-01 22-30.ts`. The channel name
 * comes first because a stick full of recordings is read as a list of
 * channels before it is read as a list of programmes, and the time comes last
 * because it is what makes two of the same thing different.
 *
 * Everything FAT32 refuses is replaced rather than dropped, so `UK: BBC` does
 * not silently become `UK BBC` and collide with a channel actually called
 * that. The whole name is cut well short of the 255-byte limit: a provider's
 * titles run long, and some sticks count bytes rather than characters.
 */
fun recordingFileName(
    channelName: String,
    title: String,
    startSeconds: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    extension: String = "ts",
): String {
    val stamp = Instant.ofEpochSecond(startSeconds).atZone(zone).format(FILE_STAMP)
    val channel = channelName.clean().take(60)
    val programme = title.clean().take(80)
    val stem = listOf(channel, programme).filter { it.isNotBlank() }.joinToString(" - ")
    val named = if (stem.isBlank()) "Recording" else stem
    return "$named - $stamp.$extension"
}

private fun String.clean(): String =
    replace(UNSAFE, "-").replace(Regex("""\s+"""), " ").trim().trim('.', '-').trim()
