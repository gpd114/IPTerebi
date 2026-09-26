package com.ipterebi.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Left in the guide, which has to mean two things at once.
 *
 * None of this can be checked on the emulator used for the TV app: it does not
 * deliver a held key to the app at all — neither this nor the hold on OK that
 * has worked on a real remote since the guide was written. So the decision
 * lives here, where holding is a boolean.
 */
class GuideLeftTest {

    private val now = 1790103600L
    private val hour = 3600L

    private fun slot(start: Long, stop: Long) = GuideSlot(start, stop, null)

    /** Four days back, as a channel keeping an archive would allow. */
    private val earliest = now - 4 * 24 * hour

    @Test
    fun `a tap at what is on now is the way to the groups`() {
        val onNow = slot(now - hour, now + hour)
        val before = slot(now - 2 * hour, now - hour)
        assertEquals(GuideLeft.OpenGroups, guideLeft(onNow, before, now, earliest, held = false))
    }

    @Test
    fun `holding it there goes back instead`() {
        val onNow = slot(now - hour, now + hour)
        val before = slot(now - 2 * hour, now - hour)
        assertEquals(GuideLeft.StepBack, guideLeft(onNow, before, now, earliest, held = true))
    }

    @Test
    fun `from the past a tap keeps going back`() {
        // There the groups would be the surprise, not the past.
        val finished = slot(now - 2 * hour, now - hour)
        val before = slot(now - 3 * hour, now - 2 * hour)
        assertEquals(GuideLeft.StepBack, guideLeft(finished, before, now, earliest, held = false))
    }

    @Test
    fun `ahead of now, left is ordinary movement`() {
        // Looking at tonight and stepping back towards what is on: nothing to
        // do with the past, and the groups must not open under you.
        val later = slot(now + 2 * hour, now + 3 * hour)
        val before = slot(now + hour, now + 2 * hour)
        assertEquals(GuideLeft.StepBack, guideLeft(later, before, now, earliest, held = false))
    }

    @Test
    fun `it stops at the end of what the provider keeps`() {
        val oldest = slot(earliest, earliest + hour)
        val beyond = slot(earliest - hour, earliest)
        assertEquals(GuideLeft.Nothing, guideLeft(oldest, beyond, now, earliest, held = false))
        // And holding it there does not force a way through either.
        assertEquals(GuideLeft.Nothing, guideLeft(oldest, beyond, now, earliest, held = true))
    }

    @Test
    fun `a channel with no recording cannot be held into the past`() {
        // Its window is empty: earliest is now, so there is nowhere to go and
        // the hold does nothing rather than opening the groups by surprise.
        val onNow = slot(now - hour, now + hour)
        val before = slot(now - 2 * hour, now - hour)
        assertEquals(GuideLeft.Nothing, guideLeft(onNow, before, now, earliest = now, held = true))
        // A tap on that channel is still the way to the groups.
        assertEquals(GuideLeft.OpenGroups, guideLeft(onNow, before, now, earliest = now, held = false))
    }
}
