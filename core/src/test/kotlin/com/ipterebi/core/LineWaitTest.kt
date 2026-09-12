package com.ipterebi.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Riding out the connection limit instead of reporting it at once. */
class LineWaitTest {

    @Test
    fun `a connection-limit refusal is asked again after a few seconds`() {
        val wait = LineWait()
        assertEquals(LineWait.EVERY_MS, wait.onRefused(458, nowMs = 0))
        assertTrue(wait.waiting)
        assertEquals(LineWait.EVERY_MS, wait.onRefused(456, nowMs = 3_100))
    }

    @Test
    fun `other refusals are shown at once`() {
        // A 403 is also a refused user agent: waiting would only delay saying so.
        val wait = LineWait()
        assertNull(wait.onRefused(403, nowMs = 0))
        assertNull(wait.onRefused(404, nowMs = 0))
        assertFalse(wait.waiting)
    }

    @Test
    fun `waiting gives up after about half a minute`() {
        val wait = LineWait()
        var now = 0L
        var asks = 0
        while (wait.onRefused(458, now) != null) {
            asks++
            now += LineWait.EVERY_MS + 100 // each ask takes a moment to be refused
        }
        assertTrue(now in 25_000..33_000, "gave up at $now ms")
        assertTrue(asks in 8..10, "asked $asks times")
        assertFalse(wait.waiting)
    }

    @Test
    fun `outlasts the fifteen seconds a real line took to let go`() {
        val wait = LineWait()
        for (t in 0L..15_000L step 3_100L) assertEquals(LineWait.EVERY_MS, wait.onRefused(458, t))
    }

    @Test
    fun `a new channel starts a new wait`() {
        val wait = LineWait()
        wait.onRefused(458, nowMs = 0)
        wait.onRefused(458, nowMs = 25_000)
        wait.reset()
        assertEquals(LineWait.EVERY_MS, wait.onRefused(458, nowMs = 40_000))
    }
}
