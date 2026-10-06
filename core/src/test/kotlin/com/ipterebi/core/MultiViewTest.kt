package com.ipterebi.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Several channels at once, on a line that would rather give you one.
 *
 * Every rule here is about failing well, because the limit is the provider's
 * and no code can lift it.
 */
class MultiViewTest {

    // The grid. A television is wide, and that decides all of it.

    @Test
    fun `two are side by side, not stacked`() {
        // Stacked leaves half a wide screen empty.
        assertEquals(PaneGrid(1, 2), paneGrid(2))
    }

    @Test
    fun `three is a two by two with a cell spare`() {
        // Three columns on a 16:9 screen is a letterbox slot nobody can
        // follow a ball across.
        assertEquals(PaneGrid(2, 2), paneGrid(3))
        assertEquals(4, paneGrid(3).cells)
    }

    @Test
    fun `one fills the screen and four fill the grid`() {
        assertEquals(PaneGrid(1, 1), paneGrid(1))
        assertEquals(PaneGrid(2, 2), paneGrid(4))
    }

    @Test
    fun `a count out of range is brought back into it`() {
        // A screen asking for nought panes, or nine, gets something drawable
        // rather than a crash or an empty grid.
        assertEquals(PaneGrid(1, 1), paneGrid(0))
        assertEquals(PaneGrid(1, 1), paneGrid(-3))
        assertEquals(PaneGrid(2, 2), paneGrid(99))
    }

    // Opening them. The gap is the whole point.

    @Test
    fun `panes are asked for in turn, not together`() {
        // A real line answered 458 to every reconnect for fifteen seconds
        // after one connection dropped. Four requests in one instant is the
        // worst version of that.
        assertEquals(0L, paneOpenDelayMillis(0))
        assertEquals(PANE_OPEN_GAP_MS, paneOpenDelayMillis(1))
        assertEquals(3 * PANE_OPEN_GAP_MS, paneOpenDelayMillis(3))
    }

    @Test
    fun `the first pane waits for nothing`() {
        // The common case is one channel, and it must not feel slower than
        // the ordinary player.
        assertEquals(0L, paneOpenDelayMillis(-1))
    }

    // The sound.

    @Test
    fun `exactly one pane is heard`() {
        val panes = listOf(Pane(1), Pane(2), Pane(3)).withSoundOn(2)
        assertEquals(1, panes.count { it.sound })
        assertTrue(panes.single { it.sound }.streamId == 2)
    }

    @Test
    fun `naming a pane that is not there still leaves something audible`() {
        // Four silent pictures read as four broken streams rather than as a
        // choice, so this can never return a mute screen.
        val panes = listOf(Pane(1), Pane(2)).withSoundOn(99)
        assertEquals(1, panes.count { it.sound })
        assertEquals(1, panes.single { it.sound }.streamId)
    }

    @Test
    fun `no panes is no sound to place`() {
        assertTrue(emptyList<Pane>().withSoundOn(1).isEmpty())
    }

    // Adding and removing.

    @Test
    fun `the same channel is not opened twice`() {
        // Two connections for one picture is the most expensive mistake
        // available on a line that allows two.
        val panes = listOf(Pane(1, sound = true)).withPaneAdded(1)
        assertEquals(1, panes.size)
    }

    @Test
    fun `a fifth pane is refused`() {
        var panes = emptyList<Pane>()
        listOf(1, 2, 3, 4, 5).forEach { panes = panes.withPaneAdded(it) }
        assertEquals(MAX_PANES, panes.size)
        assertTrue(panes.none { it.streamId == 5 })
    }

    @Test
    fun `adding keeps the sound where it was`() {
        val panes = listOf(Pane(1), Pane(2)).withSoundOn(2).withPaneAdded(3)
        assertEquals(2, panes.single { it.sound }.streamId)
    }

    @Test
    fun `the first pane added is heard`() {
        val panes = emptyList<Pane>().withPaneAdded(7)
        assertEquals(7, panes.single { it.sound }.streamId)
    }

    @Test
    fun `removing the heard pane moves the sound on`() {
        val panes = listOf(Pane(1), Pane(2), Pane(3)).withSoundOn(2).withPaneRemoved(2)
        assertEquals(2, panes.size)
        assertEquals(1, panes.count { it.sound })
    }

    @Test
    fun `removing the last one empties the list rather than refusing`() {
        // The screen above decides what an empty grid means; a rule that
        // cannot empty its own list is one the screen has to work around.
        assertTrue(listOf(Pane(1, sound = true)).withPaneRemoved(1).isEmpty())
    }

    // What a refused pane says.

    @Test
    fun `a refusal beside working panes blames the panes`() {
        // The general wording explains the connection limit; this says the
        // thing the viewer can act on, which is their own other panes.
        val said = describePaneRefusal(458, playing = 2)
        assertTrue(said.contains("2 panes are already using it"), said)
        assertTrue(said.contains("Close one"), said)
    }

    @Test
    fun `one working pane is singular`() {
        assertTrue(describePaneRefusal(403, playing = 1).contains("1 pane is already"))
    }

    @Test
    fun `the first pane being refused is an ordinary refusal`() {
        // Nothing of ours is holding the line, so "close a pane" would send
        // somebody chasing the wrong thing.
        assertEquals(describeStreamHttpError(458), describePaneRefusal(458, playing = 0))
    }

    @Test
    fun `a missing channel is still a missing channel`() {
        // A 404 in a four-pane grid is not the connection limit, and saying
        // "close a pane" about it is a wild goose chase.
        assertEquals(describeStreamHttpError(404), describePaneRefusal(404, playing = 3))
    }

    @Test
    fun `a failure with no HTTP answer reads as one`() {
        // A timeout, a reset, a stream the box could not decode. There is no
        // code to read anything into, and "(HTTP 0)" is not a sentence.
        assertEquals("This feed did not start.", describePaneRefusal(0, playing = 2))
        assertEquals("This feed did not start.", describePaneRefusal(-1, playing = 0))
    }

    @Test
    fun `the count is the others, not this one`() {
        // Found on the emulator: the sentence was written when a pane failed,
        // so the first refusal of four counted the three that had not had
        // their turn yet and announced "3 panes are already using it" while
        // nothing was playing at all. The screen now words it from the live
        // count, and this is the rule that makes that sayable.
        assertTrue(describePaneRefusal(458, playing = 0).contains("connection limit"))
        assertTrue(describePaneRefusal(458, playing = 1).contains("1 pane is"))
    }

    // Changing what a pane shows. The remote this was built for has no
    // number keys, so a pane is changed in place rather than tuned.

    @Test
    fun `a changed pane keeps its place and its sound`() {
        // A grid that reshuffles when one pane changes is one nobody can
        // point at, and changing what a pane shows is not a reason to start
        // listening to a different one.
        val panes = listOf(Pane(1), Pane(2), Pane(3)).withSoundOn(2)
        val after = panes.withPaneChanged(2, 9)
        assertEquals(listOf(1, 9, 3), after.map { it.streamId })
        assertEquals(9, after.single { it.sound }.streamId)
    }

    @Test
    fun `changing to something already up is refused`() {
        // Two connections for one picture, and on a grid two identical
        // pictures as well.
        val panes = listOf(Pane(1, sound = true), Pane(2))
        assertEquals(panes, panes.withPaneChanged(2, 1))
    }

    @Test
    fun `changing a pane that is not there does nothing`() {
        val panes = listOf(Pane(1, sound = true))
        assertEquals(panes, panes.withPaneChanged(7, 9))
    }

    @Test
    fun `changing a pane to what it already shows is allowed and harmless`() {
        val panes = listOf(Pane(1, sound = true), Pane(2))
        assertEquals(panes, panes.withPaneChanged(1, 1))
    }

    // Keeping an arrangement between visits.

    @Test
    fun `an arrangement survives being written down`() {
        val panes = listOf(Pane(11), Pane(22), Pane(33)).withSoundOn(22)
        val back = storedPanes(panes.asStored())
        assertEquals(listOf(11, 22, 33), back.map { it.streamId })
        // The sound is not stored; it lands on the first, which is where
        // somebody who set the grid up would expect it.
        assertEquals(11, back.single { it.sound }.streamId)
    }

    @Test
    fun `rubbish in a stored arrangement costs only itself`() {
        // Losing one pane of four to a stray character should not cost the
        // other three: an arrangement is a convenience, not a document.
        assertEquals(listOf(11, 33), storedPanes("11,,oops,33").map { it.streamId })
    }

    @Test
    fun `nothing stored is nothing restored`() {
        assertTrue(storedPanes("").isEmpty())
        assertTrue(storedPanes("0").isEmpty())
    }

    @Test
    fun `a stored arrangement cannot exceed the ceiling or repeat itself`() {
        assertEquals(listOf(1, 2, 3, 4), storedPanes("1,2,3,4,5,6").map { it.streamId })
        assertEquals(listOf(1, 2), storedPanes("1,2,1,2").map { it.streamId })
    }
}
