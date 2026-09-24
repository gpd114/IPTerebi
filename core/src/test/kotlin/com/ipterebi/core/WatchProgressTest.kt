package com.ipterebi.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rules behind the Continue watching rows: what is kept, what is dropped,
 * and how long is left. None of this needs a device, and all of it fails
 * quietly if it is wrong — a finished film sitting at the front of the row for
 * ever is not a crash, just an app nobody trusts.
 */
class WatchProgressTest {

    private fun item(
        id: String = "501",
        kind: WatchKind = WatchKind.FILM,
        position: Long = 10 * 60_000,
        duration: Long = 90 * 60_000,
        at: Long = 1_000,
        name: String = "A Film",
    ) = WatchedItem(
        kind = kind,
        id = id,
        name = name,
        extension = "mkv",
        positionMs = position,
        durationMs = duration,
        watchedAt = at,
    )

    @Test
    fun `something part-watched is kept, newest first`() {
        val list = emptyList<WatchedItem>()
            .withWatched(item(id = "1", at = 100))
            .withWatched(item(id = "2", at = 200))

        assertEquals(listOf("2", "1"), list.map { it.id })
    }

    @Test
    fun `watching the same thing again replaces it rather than adding a second`() {
        val list = emptyList<WatchedItem>()
            .withWatched(item(id = "1", position = 10 * 60_000, at = 100))
            .withWatched(item(id = "1", position = 20 * 60_000, at = 200))

        assertEquals(1, list.size)
        assertEquals(20 * 60_000, list.single().positionMs)
    }

    @Test
    fun `a film and an episode may share an id without colliding`() {
        // They come from different numbering on the panel, and an episode's id
        // is a string that could be anything at all.
        val list = emptyList<WatchedItem>()
            .withWatched(item(id = "7", kind = WatchKind.FILM, at = 100))
            .withWatched(item(id = "7", kind = WatchKind.EPISODE, at = 200))

        assertEquals(2, list.size)
    }

    @Test
    fun `finishing something takes it off the row`() {
        val started = emptyList<WatchedItem>().withWatched(item(id = "1", position = 30 * 60_000))
        assertEquals(1, started.size)

        // Ninety minutes long, and one minute from the end: finished.
        val finished = started.withWatched(item(id = "1", position = 89 * 60_000, at = 300))
        assertTrue(finished.isEmpty(), "a finished film should not be offered back")
    }

    @Test
    fun `a few seconds in is a mis-tap, not a film`() {
        val list = emptyList<WatchedItem>().withWatched(item(position = 12_000))
        assertTrue(list.isEmpty())
    }

    @Test
    fun `a stream that never played does not fill the row`() {
        // A refused channel or a film the panel would not serve leaves the
        // position at zero. On a bad line that would otherwise be most of it.
        val list = emptyList<WatchedItem>().withWatched(item(position = 0, duration = 0))
        assertTrue(list.isEmpty())
    }

    @Test
    fun `the list is capped`() {
        var list = emptyList<WatchedItem>()
        for (i in 1..MAX_CONTINUE + 10) list = list.withWatched(item(id = "$i", at = i.toLong()))

        assertEquals(MAX_CONTINUE, list.size)
        // The newest survive, the oldest fall off the end.
        assertEquals("${MAX_CONTINUE + 10}", list.first().id)
    }

    @Test
    fun `an unknown length is not guessed at`() {
        val unknown = item(position = 10 * 60_000, duration = 0)
        assertEquals(0f, unknown.fraction)
        // With no length there is nothing to be near the end of, so it stays.
        assertFalse(unknown.isFinished)
        assertEquals(1, emptyList<WatchedItem>().withWatched(unknown).size)
    }

    @Test
    fun `resuming reads the position back, and only for the right thing`() {
        val list = emptyList<WatchedItem>()
            .withWatched(item(id = "1", kind = WatchKind.FILM, position = 20 * 60_000))

        assertEquals(20 * 60_000, list.resumeAt(WatchKind.FILM, "1"))
        assertEquals(0L, list.resumeAt(WatchKind.EPISODE, "1"))
        assertEquals(0L, list.resumeAt(WatchKind.FILM, "2"))
    }

    @Test
    fun `each kind gets its own row`() {
        val list = emptyList<WatchedItem>()
            .withWatched(item(id = "1", kind = WatchKind.FILM, at = 100))
            .withWatched(item(id = "2", kind = WatchKind.EPISODE, at = 200))
            .withWatched(item(id = "3", kind = WatchKind.FILM, at = 300))

        assertEquals(listOf("3", "1"), list.ofKind(WatchKind.FILM).map { it.id })
        assertEquals(listOf("2"), list.ofKind(WatchKind.EPISODE).map { it.id })
    }

    @Test
    fun `how long is left, in words someone would use`() {
        assertEquals("34m left", remainingLabel(34 * 60_000 + 20_000))
        assertEquals("1h 12m left", remainingLabel(72 * 60_000))
        assertEquals("2h left", remainingLabel(120 * 60_000))
        assertEquals("1m left", remainingLabel(60_000))
        // Never "0m left", which reads as a fault rather than as nearly over.
        assertEquals("under a minute left", remainingLabel(20_000))
        assertEquals("under a minute left", remainingLabel(0))
    }

    @Test
    fun `progress is a fraction of the way through, and stays in range`() {
        assertEquals(0.5f, item(position = 45 * 60_000, duration = 90 * 60_000).fraction)
        // A position past the end — which a panel's own duration can produce —
        // must not draw a bar wider than the bar.
        assertEquals(1f, item(position = 100 * 60_000, duration = 90 * 60_000).fraction)
    }
}

/**
 * How near the end counts as the end, which is not a fixed number of minutes.
 *
 * Found on a device rather than reasoned out: the fake panel's test film is 90
 * seconds long, and a flat two-minute tail meant it was "finished" from 30
 * seconds in, so it never once reached the Continue watching row.
 */
class FinishedTailTest {

    private fun at(position: Long, duration: Long) = WatchedItem(
        kind = WatchKind.FILM,
        id = "1",
        name = "A Film",
        extension = "mp4",
        positionMs = position,
        durationMs = duration,
        watchedAt = 1,
    )

    @Test
    fun `a feature is finished in its last two minutes`() {
        val ninetyMinutes = 90 * 60_000L
        assertFalse(at(87 * 60_000, ninetyMinutes).isFinished)
        assertTrue(at(88 * 60_000 + 30_000, ninetyMinutes).isFinished)
    }

    @Test
    fun `a ninety-second clip is not finished halfway through it`() {
        val clip = 90_000L
        assertFalse(at(45_000, clip).isFinished, "half of a short film is not the end of it")
        // A twentieth of 90 s is four and a half seconds, and that is the tail.
        assertTrue(at(87_000, clip).isFinished)
    }

    @Test
    fun `a twenty-minute episode gets a minute of tail, not two`() {
        val episode = 20 * 60_000L
        assertFalse(at(18 * 60_000 + 30_000, episode).isFinished)
        assertTrue(at(19 * 60_000 + 30_000, episode).isFinished)
    }

    @Test
    fun `a part-watched short film survives being recorded`() {
        // The whole point: this is what failed on the emulator.
        val kept = emptyList<WatchedItem>().withWatched(at(48_000, 90_000))
        assertEquals(1, kept.size)
        assertEquals(48_000, kept.single().positionMs)
    }
}
