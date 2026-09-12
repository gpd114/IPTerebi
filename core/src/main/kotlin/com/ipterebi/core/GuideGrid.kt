package com.ipterebi.core

/**
 * Moving about a guide grid with a remote: channels down, time across, one
 * programme focused.
 *
 * The grid is driven by a cursor rather than by focusing each cell: a line's
 * worth of rows by four days of programmes is thousands of cells, and focus
 * search between cells of different widths goes wherever geometry says, not
 * to the next programme. So left and right step programme by programme along
 * a row; up and down keep the point in time and take whatever is on then in
 * the next row, as a set-top box's guide does.
 *
 * A stretch with nothing listed is stepped through in half-hour blocks, so a
 * channel with no guide at all can still be moved along, and a gap of six
 * hours is not crossed in one surprising jump.
 */
data class GuideSlot(
    /** Unix seconds. */
    val start: Long,
    val stop: Long,
    /** Null for a stretch with nothing listed. */
    val programme: XmltvProgramme?,
) {
    fun isOnAt(seconds: Long) = seconds in start until stop
}

/** Half an hour, the grid's unit: its time marks, its window steps, its empty blocks. */
const val GUIDE_STEP = 30 * 60L

fun floorToGuideStep(seconds: Long): Long = seconds - Math.floorMod(seconds, GUIDE_STEP)

/**
 * The slot of [programmes] — sorted by start, as the guide store returns them —
 * that holds [at]: the programme on then, or the half-hour block of nothing
 * listed around it, cut short by the programmes either side.
 */
fun slotAt(programmes: List<XmltvProgramme>, at: Long): GuideSlot {
    var before: XmltvProgramme? = null
    for (p in programmes) {
        if (at < p.start) {
            val blockStart = maxOf(floorToGuideStep(at), before?.stop ?: Long.MIN_VALUE)
            val blockStop = minOf(floorToGuideStep(at) + GUIDE_STEP, p.start)
            return GuideSlot(blockStart, blockStop, null)
        }
        if (at < p.stop) return GuideSlot(p.start, p.stop, p)
        before = p
    }
    val blockStart = maxOf(floorToGuideStep(at), before?.stop ?: Long.MIN_VALUE)
    return GuideSlot(blockStart, floorToGuideStep(at) + GUIDE_STEP, null)
}

/** The slot after [current] in the same row. */
fun nextSlot(programmes: List<XmltvProgramme>, current: GuideSlot): GuideSlot = slotAt(programmes, current.stop)

/** The slot before [current] in the same row. */
fun previousSlot(programmes: List<XmltvProgramme>, current: GuideSlot): GuideSlot = slotAt(programmes, current.start - 1)

/**
 * Where the grid's window should start so [slot] is in view, given the
 * window's [visible] length: unchanged when it already is; moved back to the
 * start of a slot that begins before it — stepping left onto a programme
 * shows where it began, and brings the one on now back into view; moved on,
 * keeping the half hour before in view, for one that starts in its last half
 * hour or later. Never before [earliest].
 */
fun windowFor(windowStart: Long, visible: Long, slot: GuideSlot, earliest: Long): Long = when {
    slot.start < windowStart -> maxOf(floorToGuideStep(slot.start), earliest)
    slot.start >= windowStart + visible - GUIDE_STEP ->
        maxOf(floorToGuideStep(slot.start) - GUIDE_STEP, earliest)
    else -> windowStart
}

/**
 * The point in time up and down keep: the focused slot's start, or the
 * window's edge when the slot began before it — so moving down from a film
 * that started hours ago lands on what is on at the left of the screen, not on
 * whatever was on when the film began.
 */
fun anchorOf(slot: GuideSlot, windowStart: Long): Long = maxOf(slot.start, windowStart)
