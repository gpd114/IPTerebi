package com.ipterebi.core

/**
 * Several channels on the screen at once.
 *
 * **Read this before changing anything here: the limit is the line, not the
 * box.** Every pane is its own player and so its own connection to the panel,
 * and a line usually allows one at a time. The whole of the rest of this app
 * is built around that — the TV screen reuses one player and stops the old
 * stream before asking for the next, switching channel happens inside the
 * player screen rather than by navigating, and a paused player is stopped
 * because a paused one keeps the connection. Multiview is the one feature
 * that deliberately asks for more than one, and on a line that allows one it
 * will get a refusal for every pane after the first.
 *
 * That is not a reason to refuse to build it — a line that allows three is a
 * line where this is lovely, and the viewer knows what they are paying for
 * better than the app does. It is the reason the rules below are about
 * *failing well*: panes open one at a time with a gap, a pane that is refused
 * says why in the panel's own terms, and the count is small because a box
 * decoding four streams is already working hard.
 */

/**
 * How many panes may be on screen.
 *
 * Four, and the ceiling is the box rather than the screen: a 1080p box
 * decoding four streams at once is near the end of what its hardware decoder
 * will do, and the owner's is an Android 11 Xiaomi that has been as low as
 * 235 MB free. The line's own limit will usually bite long before this does.
 */
const val MAX_PANES = 4

/**
 * How long to wait between opening one pane and the next.
 *
 * **Measured behaviour, not politeness.** A real line answered 458 to every
 * reconnect for about fifteen seconds after a phone dropped off Wi-Fi, until
 * it stopped counting the connection that had gone; and the ordinary case of
 * leaving one channel and opening another is refused if it happens too
 * quickly. Asking a panel for four streams in the same instant is the worst
 * version of that. So they are opened in turn, and a pane that is refused has
 * not poisoned the ones behind it.
 */
const val PANE_OPEN_GAP_MS = 1_500L

/** When pane [index] should be asked for, in millis after the screen opens. */
fun paneOpenDelayMillis(index: Int): Long = index.coerceAtLeast(0) * PANE_OPEN_GAP_MS

/**
 * The shape of the grid for [count] panes: rows by columns.
 *
 * Two side by side rather than stacked, because a television is wide and two
 * 16:9 pictures stacked leave half the screen empty. Three is a 2x2 with one
 * cell spare rather than three thin columns, for the same reason: a third of
 * a 16:9 screen is a letterbox slot nobody can follow a ball across.
 */
data class PaneGrid(val rows: Int, val columns: Int) {
    /** How many cells the grid has; the last may be empty. */
    val cells: Int get() = rows * columns
}

fun paneGrid(count: Int): PaneGrid = when (count.coerceIn(1, MAX_PANES)) {
    1 -> PaneGrid(1, 1)
    2 -> PaneGrid(1, 2)
    else -> PaneGrid(2, 2)
}

/**
 * One pane: a channel, and whether it is the one that can be heard.
 *
 * [streamId] rather than the whole channel, for the reason everything else
 * navigates by id: a list of channels is megabytes on a real line and has no
 * business being held per pane.
 */
data class Pane(val streamId: Int, val sound: Boolean = false)

/**
 * Exactly one pane has sound, and it is [soundOn] where that is one of them.
 *
 * **Exactly one, never none and never all.** Four commentaries at once is not
 * a feature anyone has ever wanted, and silence on every pane reads as four
 * broken streams rather than as a choice. Where the named pane is not in the
 * list the first one takes it, so this cannot return a silent screen.
 */
fun List<Pane>.withSoundOn(soundOn: Int): List<Pane> {
    if (isEmpty()) return this
    val heard = if (any { it.streamId == soundOn }) soundOn else first().streamId
    return map { it.copy(sound = it.streamId == heard) }
}

/**
 * [streamId] added, unless it is already up or there is no room.
 *
 * The same channel twice is two connections for one picture, which on a line
 * that allows one or two is the most expensive possible mistake.
 */
fun List<Pane>.withPaneAdded(streamId: Int): List<Pane> {
    if (size >= MAX_PANES || any { it.streamId == streamId }) return this
    return (this + Pane(streamId)).withSoundOn(firstOrNull { it.sound }?.streamId ?: streamId)
}

/**
 * [streamId] removed, with the sound moved on if it had it.
 *
 * Removing the last pane gives an empty list rather than refusing: the screen
 * above decides whether that means going back, and a rule that cannot empty
 * its own list is one the screen has to work around.
 */
fun List<Pane>.withPaneRemoved(streamId: Int): List<Pane> {
    val left = filterNot { it.streamId == streamId }
    if (left.isEmpty()) return left
    return left.withSoundOn(left.firstOrNull { it.sound }?.streamId ?: left.first().streamId)
}

/**
 * What to say when a pane is refused and others are playing.
 *
 * The ordinary stream wording explains the connection limit in general; this
 * says the thing the viewer can act on, which is that *their own other panes*
 * are what is using the line. [playing] is how many are up and working.
 *
 * Only for a refusal that reads as the limit. A 404 on one channel in a
 * four-pane grid is still a missing channel, and saying "close a pane" about
 * it would send someone chasing the wrong thing.
 */
fun describePaneRefusal(code: Int, playing: Int): String =
    if (code <= 0) {
        // Not an HTTP answer at all: a timeout, a reset, a stream the box
        // could not decode. There is no code to read anything into.
        "This feed did not start."
    } else if (code == 401 || code == 403 || code in CONNECTION_LIMIT_CODES) {
        if (playing <= 0) {
            describeStreamHttpError(code)
        } else {
            "Your line will not give this a second stream: $playing " +
                (if (playing == 1) "pane is" else "panes are") + " already using it. " +
                "Close one to watch this, or ask your provider how many " +
                "connections your line allows."
        }
    } else {
        describeStreamHttpError(code)
    }
