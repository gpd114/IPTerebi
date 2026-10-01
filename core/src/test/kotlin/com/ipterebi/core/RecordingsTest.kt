package com.ipterebi.core

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Recording, on a line that allows one stream at a time.
 *
 * Each of these is a way to lose a programme someone meant to keep, and all of
 * them fail quietly: a clash that books anyway and records the wrong channel,
 * an evening of one channel refusing itself, a name FAT32 will not accept, a
 * file that crosses four gigabytes two thirds of the way through a film. None
 * of it needs a device, so none of it has any excuse to be untested.
 */
class RecordingsTest {

    private val hour = 3_600L

    private fun rec(
        stream: Int,
        start: Long,
        stop: Long,
        title: String = "A Programme",
        state: RecordingState = RecordingState.SCHEDULED,
        bytes: Long = 0,
    ) = Recording(
        id = recordingId(stream, start),
        streamId = stream,
        channelName = "Channel $stream",
        title = title,
        startSeconds = start,
        stopSeconds = stop,
        programmeStart = start,
        programmeStop = stop,
        state = state,
        bytes = bytes,
    )

    @Test
    fun `a window opens out around the programme`() {
        val window = recordingWindow(1_000, 4_600)
        assertEquals(1_000 - PAD_BEFORE_SECONDS, window.first)
        assertEquals(4_600 + PAD_AFTER_SECONDS, window.last)
    }

    @Test
    fun `the id is the channel and the programme, so asking twice is once`() {
        assertEquals(recordingId(101, 1_000), recordingId(101, 1_000))
        assertTrue(recordingId(101, 1_000) != recordingId(102, 1_000))
        assertTrue(recordingId(101, 1_000) != recordingId(101, 2_000))
    }

    @Test
    fun `the id survives a change of padding`() {
        // Built from the programme's own start, not the padded one, so widening
        // the padding later does not orphan what is already scheduled.
        val programmeStart = 10_000L
        val narrow = recordingWindow(programmeStart, programmeStart + hour, 30, 60)
        val wide = recordingWindow(programmeStart, programmeStart + hour, 300, 600)
        assertTrue(narrow.first != wide.first)
        assertEquals(recordingId(101, programmeStart), recordingId(101, programmeStart))
    }

    @Test
    fun `two channels at once is a clash, because there is one connection`() {
        val existing = listOf(rec(101, 0, hour))
        val clash = clashOf(existing, rec(102, hour / 2, hour * 2))
        assertNotNull(clash)
        assertEquals(101, clash.streamId)
    }

    @Test
    fun `two channels that do not touch are both fine`() {
        val existing = listOf(rec(101, 0, hour))
        assertNull(clashOf(existing, rec(102, hour, hour * 2)))
    }

    @Test
    fun `a finished recording is in nobody's way`() {
        val done = listOf(rec(101, 0, hour, state = RecordingState.DONE))
        assertNull(clashOf(done, rec(102, 0, hour)))
        val cancelled = listOf(rec(101, 0, hour, state = RecordingState.CANCELLED))
        assertNull(clashOf(cancelled, rec(102, 0, hour)))
    }

    @Test
    fun `the same channel is never a clash with itself`() {
        val existing = listOf(rec(101, 0, hour))
        assertNull(clashOf(existing, rec(101, hour / 2, hour * 2)))
    }

    @Test
    fun `an evening of one channel becomes one recording`() {
        // Two programmes back to back: three minutes of padding after the first
        // runs into one before the second, so booked separately they would
        // clash with themselves.
        val first = rec(101, 0, hour + PAD_AFTER_SECONDS, title = "The News")
        val second = rec(101, hour - PAD_BEFORE_SECONDS, hour * 2, title = "The Film")

        val booked = schedule(emptyList(), first)
        assertTrue(booked is Scheduling.Booked)

        val merged = schedule(booked.all, second)
        assertTrue(merged is Scheduling.Merged)
        assertEquals(1, merged.all.size)
        assertEquals(0, merged.recording.startSeconds)
        assertEquals(hour * 2, merged.recording.stopSeconds)
        assertEquals("The News + The Film", merged.recording.title)
    }

    @Test
    fun `merging keeps one title when both halves are the same programme`() {
        val a = rec(101, 0, hour, title = "Snooker")
        val b = rec(101, hour - 60, hour * 2, title = "Snooker")
        val merged = mergedWith(a, b)
        assertEquals("Snooker", merged.title)
        assertEquals(hour * 2, merged.stopSeconds)
    }

    @Test
    fun `asking twice for the same programme is the same recording`() {
        val one = rec(101, 0, hour)
        val first = schedule(emptyList(), one)
        assertTrue(first is Scheduling.Booked)
        val again = schedule(first.all, one.copy(title = "Renamed by the provider"))
        assertTrue(again is Scheduling.Booked)
        assertEquals(1, again.all.size)
    }

    @Test
    fun `a clash is refused and names what has the line`() {
        val booked = schedule(emptyList(), rec(101, 0, hour, title = "The Match"))
        assertTrue(booked is Scheduling.Booked)
        val second = schedule(booked.all, rec(102, hour / 2, hour * 2))
        assertTrue(second is Scheduling.Clash)
        assertEquals("The Match", second.by.title)
    }

    @Test
    fun `what is booked stays in start order`() {
        var all = emptyList<Recording>()
        all = (schedule(all, rec(101, hour * 3, hour * 4)) as Scheduling.Booked).all
        all = (schedule(all, rec(102, 0, hour)) as Scheduling.Booked).all
        all = (schedule(all, rec(103, hour, hour * 2)) as Scheduling.Booked).all
        assertEquals(listOf(0L, hour, hour * 3), all.map { it.startSeconds })
    }

    @Test
    fun `the next one to wake for is the soonest unfinished`() {
        val all = listOf(
            rec(101, hour * 4, hour * 5),
            rec(102, hour * 2, hour * 3),
            rec(103, 0, hour, state = RecordingState.DONE),
        )
        assertEquals(102, nextToStart(all, 0)?.streamId)
    }

    @Test
    fun `a box that woke up late still joins a recording in progress`() {
        // Most of the programme is still to come, so being late is not a reason
        // to skip it.
        val all = listOf(rec(101, 0, hour))
        val now = hour / 4
        assertEquals(101, nextToStart(all, now)?.streamId)
        assertTrue(all.first().wants(now))
    }

    @Test
    fun `one that has already finished is not waited for`() {
        val all = listOf(rec(101, 0, hour))
        assertNull(nextToStart(all, hour + 1))
        assertFalse(all.first().wants(hour + 1))
    }

    @Test
    fun `room is judged with the headroom left alone`() {
        val needed = bytesFor(hour)
        assertTrue(roomFor(needed + SPACE_HEADROOM_BYTES, needed))
        assertFalse(roomFor(needed + SPACE_HEADROOM_BYTES - 1, needed))
    }

    @Test
    fun `an hour of the box's free space is the thing that does not fit`() {
        // The owner's box had 737 MB free. An hour at the bitrate this guesses
        // is some 3.6 GB, so the answer has to be no.
        val free = 737L * 1024 * 1024
        assertFalse(roomFor(free, bytesFor(hour)))
        // A quarter of an hour does not fit either, which is the fact that sent
        // recordings to a USB stick.
        assertFalse(roomFor(free, bytesFor(hour / 4)))
    }

    @Test
    fun `a long recording crosses what FAT32 can hold in one file`() {
        assertTrue(bytesFor(hour * 2) > FAT32_MAX_BYTES)
        assertTrue(bytesFor(hour / 2) < FAT32_MAX_BYTES)
    }

    @Test
    fun `deleting takes the oldest finished ones until there is room`() {
        val all = listOf(
            rec(101, 0, hour, state = RecordingState.DONE, bytes = 100),
            rec(102, hour, hour * 2, state = RecordingState.DONE, bytes = 100),
            rec(103, hour * 2, hour * 3, state = RecordingState.DONE, bytes = 100),
        )
        val taken = toDeleteFor(all, 150)
        assertEquals(listOf(101, 102), taken.map { it.streamId })
    }

    @Test
    fun `nothing is deleted when deleting everything would still not be enough`() {
        val all = listOf(rec(101, 0, hour, state = RecordingState.DONE, bytes = 100))
        assertTrue(toDeleteFor(all, 1_000).isEmpty())
    }

    @Test
    fun `what is still to come is never deleted to make room`() {
        val all = listOf(
            rec(101, 0, hour, state = RecordingState.RECORDING, bytes = 500),
            rec(102, hour, hour * 2, bytes = 0),
        )
        assertTrue(toDeleteFor(all, 100).isEmpty())
    }

    @Test
    fun `a file name loses everything FAT32 refuses`() {
        val name = recordingFileName(
            "UK: BBC ONE HD",
            "Match of the Day",
            0,
            ZoneId.of("UTC"),
        )
        assertEquals("UK- BBC ONE HD - Match of the Day - 1970-01-01 00-00.ts", name)
        assertFalse(name.any { it in "\\/:*?\"<>|" })
    }

    @Test
    fun `a name is replaced rather than dropped, so two channels stay apart`() {
        val colon = recordingFileName("UK: BBC", "X", 0, ZoneId.of("UTC"))
        val plain = recordingFileName("UK BBC", "X", 0, ZoneId.of("UTC"))
        assertTrue(colon != plain)
    }

    @Test
    fun `a provider's long title is cut well short of the limit`() {
        val name = recordingFileName(
            "4K-OSN+ ".repeat(20),
            "The Very Long Title ".repeat(20),
            0,
            ZoneId.of("UTC"),
        )
        assertTrue(name.toByteArray().size < 200, "was ${name.toByteArray().size} bytes")
    }

    @Test
    fun `a nameless recording still gets a file name`() {
        val name = recordingFileName("", "", 0, ZoneId.of("UTC"))
        assertEquals("Recording - 1970-01-01 00-00.ts", name)
    }
}
