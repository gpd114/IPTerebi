package com.ipterebi.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reconnecting a stream that dropped, without hammering a panel that has gone. */
class StreamReconnectTest {

    private fun StreamReconnect.dropsUntilGivingUp(nowMs: Long): List<Long> =
        generateSequence { onDropped(nowMs) }.toList()

    @Test
    fun `a channel that never played is not reconnected`() {
        // Refused at the door: the reason is worth showing, not retrying over.
        assertNull(StreamReconnect().onDropped(nowMs = 0))
    }

    @Test
    fun `a dropped channel is retried with growing waits, then given up on`() {
        val reconnect = StreamReconnect()
        reconnect.onPlaying(nowMs = 0)
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L), reconnect.dropsUntilGivingUp(nowMs = 5_000))
    }

    @Test
    fun `the whole sequence gives up within about half a minute`() {
        assertTrue(StreamReconnect.DEFAULT_DELAYS_MS.sum() <= 30_000)
    }

    @Test
    fun `failed attempts keep counting until the stream plays again`() {
        val reconnect = StreamReconnect()
        reconnect.onPlaying(nowMs = 0)
        assertEquals(1_000L, reconnect.onDropped(nowMs = 60_000))
        // The reconnect itself is refused — the panel still counts the old
        // connection — which is one more attempt, not a fresh start.
        assertEquals(2_000L, reconnect.onDropped(nowMs = 61_000))
        assertEquals(4_000L, reconnect.onDropped(nowMs = 63_000))
    }

    @Test
    fun `a stream that drops again soon after coming back keeps escalating`() {
        val reconnect = StreamReconnect()
        reconnect.onPlaying(nowMs = 0)
        var now = 60_000L
        val waits = mutableListOf<Long?>()
        repeat(6) {
            waits += reconnect.onDropped(now)
            now += 5_000
            reconnect.onPlaying(now) // back for a few seconds, then gone again
            now += 5_000
        }
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L, null), waits)
    }

    @Test
    fun `playing steadily forgives earlier drops`() {
        val reconnect = StreamReconnect()
        reconnect.onPlaying(nowMs = 0)
        reconnect.onDropped(nowMs = 60_000)
        reconnect.onDropped(nowMs = 61_000)
        reconnect.onPlaying(nowMs = 64_000)
        // Half a minute of good playback later, a drop starts from the top.
        assertEquals(1_000L, reconnect.onDropped(nowMs = 64_000 + 30_000))
    }

    @Test
    fun `reconnecting is true from a drop until playing again`() {
        val reconnect = StreamReconnect()
        reconnect.onPlaying(nowMs = 0)
        assertFalse(reconnect.reconnecting)
        reconnect.onDropped(nowMs = 60_000)
        assertTrue(reconnect.reconnecting)
        reconnect.onPlaying(nowMs = 62_000)
        assertFalse(reconnect.reconnecting)
    }

    @Test
    fun `giving up is not reconnecting`() {
        val reconnect = StreamReconnect()
        reconnect.onPlaying(nowMs = 0)
        reconnect.dropsUntilGivingUp(nowMs = 60_000)
        assertFalse(reconnect.reconnecting)
    }

    @Test
    fun `try again starts over, and a retry that is refused at once is shown, not retried`() {
        val reconnect = StreamReconnect()
        reconnect.onPlaying(nowMs = 0)
        reconnect.dropsUntilGivingUp(nowMs = 60_000)
        reconnect.reset()
        assertNull(reconnect.onDropped(nowMs = 120_000))
        reconnect.onPlaying(nowMs = 130_000)
        assertEquals(1_000L, reconnect.onDropped(nowMs = 140_000))
    }
}
