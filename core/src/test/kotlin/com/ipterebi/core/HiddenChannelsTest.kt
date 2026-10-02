package com.ipterebi.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Hiding channels. Every one of these is a way to lose a channel the viewer did
 * not mean to lose, or to fail to lose one they did: a hidden channel that
 * comes back when the provider renames it, one that cannot be found again to
 * unhide, or a stale id that blanks out something innocent when the provider
 * renumbers.
 */
class HiddenChannelsTest {

    private fun channel(id: Int, name: String = "Channel $id") =
        LiveStream(streamId = id, name = name)

    private val line = listOf(channel(1, "BBC One"), channel(2, "ITV"), channel(3, "Film4"))

    @Test
    fun `hiding takes a channel out of a list`() {
        val hidden = listOf(channel(2))
        assertEquals(listOf(1, 3), line.withoutHidden(hidden.hiddenIds()).map { it.streamId })
    }

    @Test
    fun `hiding nothing does not copy the line`() {
        // A real line is tens of thousands of channels and this runs on every
        // list that is drawn, so the common case returns what it was given.
        assertSame(line, line.withoutHidden(emptySet()))
    }

    @Test
    fun `an id for a channel not in this list is simply not there`() {
        assertEquals(3, line.withoutHidden(setOf(99)).size)
    }

    @Test
    fun `hiding and showing are opposites`() {
        val once = emptyList<LiveStream>().withHiddenToggled(channel(2))
        assertEquals(listOf(2), once.map { it.streamId })
        assertTrue(once.withHiddenToggled(channel(2)).isEmpty())
    }

    @Test
    fun `the most recently hidden is first, because it is the likeliest mistake`() {
        val hidden = emptyList<LiveStream>()
            .withHiddenToggled(channel(1))
            .withHiddenToggled(channel(2))
        assertEquals(listOf(2, 1), hidden.map { it.streamId })
    }

    @Test
    fun `hiding carries the name, so it can be offered back`() {
        val hidden = emptyList<LiveStream>().withHiddenToggled(channel(1, "BBC One"))
        // The whole reason records are stored rather than ids: the screen that
        // undoes this has to draw something, and the channel is by then out of
        // every list it could have been read from.
        assertEquals("BBC One", hidden.single().name)
    }

    @Test
    fun `a renamed channel stays hidden`() {
        // Providers rename constantly — "BBC One" becomes "UK: BBC ONE HD"
        // overnight — which is why filtering matches on the id alone.
        val hidden = listOf(channel(1, "BBC One"))
        val renamed = listOf(channel(1, "UK: BBC ONE HD"), channel(2, "ITV"))
        assertEquals(listOf(2), renamed.withoutHidden(hidden.hiddenIds()).map { it.streamId })
    }

    @Test
    fun `hiding the same channel twice is showing it again`() {
        val hidden = listOf(channel(2)).withHiddenToggled(channel(2))
        assertTrue(hidden.isEmpty())
    }

    @Test
    fun `one the provider has dropped stops being kept`() {
        val hidden = listOf(channel(1), channel(404))
        assertEquals(listOf(1), hidden.stillOnLine(line).map { it.streamId })
    }

    @Test
    fun `nothing is forgotten when every one still answers`() {
        val hidden = listOf(channel(1), channel(2))
        assertEquals(2, hidden.stillOnLine(line).size)
    }

    @Test
    fun `an empty hidden list is left alone rather than rebuilt`() {
        val empty = emptyList<LiveStream>()
        assertSame(empty, empty.stillOnLine(line))
    }

    @Test
    fun `filtering twice changes nothing the second time`() {
        val ids = listOf(channel(2)).hiddenIds()
        val once = line.withoutHidden(ids)
        assertEquals(once.map { it.streamId }, once.withoutHidden(ids).map { it.streamId })
    }

    @Test
    fun `a channel is hidden or it is not`() {
        val ids = listOf(channel(2)).hiddenIds()
        assertTrue(ids.contains(2))
        assertFalse(ids.contains(1))
    }
}
