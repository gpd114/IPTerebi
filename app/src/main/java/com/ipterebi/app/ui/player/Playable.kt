package com.ipterebi.app.ui.player

/**
 * What the player has been asked to play.
 *
 * A channel and a film look alike from here — an id and a URL — and differ in
 * almost everything the player does with them. A channel has no duration, no
 * seekable window, a guide, and a live edge to rejoin; a film has a length, a
 * position worth keeping, and an end. Saying which up front is what lets each
 * of those decisions be made on purpose rather than inherited from whichever
 * kind the player was first written for.
 *
 * Carries only what fits in a navigation route. The title is looked up from the
 * list on screen for the same reason the id is all that travels — see
 * MediaRepository.
 */
sealed interface Playable {
    val id: Int

    data class Channel(override val id: Int) : Playable

    /** [extension] is the film's own container — mp4, mkv — not a format this app picked. */
    data class Film(override val id: Int, val extension: String) : Playable
}
