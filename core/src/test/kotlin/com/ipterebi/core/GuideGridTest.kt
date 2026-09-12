package com.ipterebi.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A remote moving about the guide: programme by programme, and through gaps. */
class GuideGridTest {

    private val h = 3600L
    private val base = 1_789_000_200L - Math.floorMod(1_789_000_200L, GUIDE_STEP) // on a half hour

    private fun p(fromH: Double, toH: Double, title: String) =
        XmltvProgramme("c", base + (fromH * h).toLong(), base + (toH * h).toLong(), title, "")

    /** News 0–1, a gap 1–2, Film 2–4. */
    private val row = listOf(p(0.0, 1.0, "News"), p(2.0, 4.0, "Film"))

    @Test
    fun `the slot at a time is the programme on then`() {
        assertEquals("News", slotAt(row, base + 600).programme?.title)
        assertEquals("Film", slotAt(row, base + 3 * h).programme?.title)
    }

    @Test
    fun `a gap is stepped through in half hours, cut short by its neighbours`() {
        val gap = slotAt(row, base + h + 60)
        assertNull(gap.programme)
        assertEquals(base + h, gap.start)
        assertEquals(base + h + GUIDE_STEP, gap.stop)
        val rest = nextSlot(row, gap)
        assertEquals(base + h + GUIDE_STEP, rest.start)
        assertEquals(base + 2 * h, rest.stop)
        assertEquals("Film", nextSlot(row, rest).programme?.title)
    }

    @Test
    fun `left from a programme is the one before, across a gap`() {
        val film = slotAt(row, base + 2 * h)
        val gap = previousSlot(row, film)
        assertNull(gap.programme)
        assertEquals(base + 2 * h, gap.stop)
        assertEquals("News", previousSlot(row, previousSlot(row, gap)).programme?.title)
    }

    @Test
    fun `a channel with no guide is half-hour blocks all the way`() {
        val block = slotAt(emptyList(), base + 700)
        assertEquals(base, block.start)
        assertEquals(base + GUIDE_STEP, block.stop)
        assertEquals(base + GUIDE_STEP, nextSlot(emptyList(), block).start)
        assertEquals(base - GUIDE_STEP, previousSlot(emptyList(), block).start)
    }

    @Test
    fun `past the end of the guide is blocks again`() {
        val after = nextSlot(row, slotAt(row, base + 3 * h))
        assertNull(after.programme)
        assertEquals(base + 4 * h, after.start)
    }

    @Test
    fun `the window stays put while the slot is in view`() {
        assertEquals(base, windowFor(base, 2 * h, slotAt(row, base + 600), earliest = 0))
    }

    @Test
    fun `the window moves on for a slot near its right edge, keeping the half hour before`() {
        val film = slotAt(row, base + 2 * h)
        assertEquals(base + 2 * h - GUIDE_STEP, windowFor(base, 2 * h, film, earliest = 0))
    }

    @Test
    fun `the window moves back for a slot wholly before it, but not before the earliest`() {
        val news = slotAt(row, base)
        assertEquals(base, windowFor(base + 3 * h, 2 * h, news, earliest = 0))
        assertEquals(base + h, windowFor(base + 3 * h, 2 * h, news, earliest = base + h))
    }

    @Test
    fun `stepping left onto a long programme brings its start into view`() {
        // Right past it and back: the window follows back to where it began,
        // so a programme on now is seen with the now line, not off to the left.
        val film = slotAt(row, base + 3 * h)
        assertEquals(base + 2 * h, windowFor(base + 3 * h, 2 * h, film, earliest = 0))
    }

    @Test
    fun `up and down keep the window's edge, not the start of a long programme`() {
        val film = slotAt(row, base + 3 * h)
        assertEquals(base + 3 * h, anchorOf(film, windowStart = base + 3 * h))
        assertEquals(base + 2 * h, anchorOf(film, windowStart = base))
    }
}
