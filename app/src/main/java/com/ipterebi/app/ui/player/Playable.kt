package com.ipterebi.app.ui.player

/**
 * What the player has been asked to play.
 *
 * A channel and a file look alike from here — an id and a URL — and differ in
 * almost everything the player does with them. A channel has no duration, no
 * seekable window, a guide, and a live edge to rejoin; a film or an episode has
 * a length, a position worth keeping, and an end. Saying which up front is what
 * lets each of those decisions be made on purpose rather than inherited from
 * whichever kind the player was first written for.
 *
 * Carries only what fits in a navigation route. The title is looked up from the
 * list on screen for the same reason the id is all that travels — see
 * MediaRepository.
 */
sealed interface Playable {

    data class Channel(val id: Int) : Playable

    /** [extension] is the film's own container — mp4, mkv — not a format this app picked. */
    data class Film(val id: Int, val extension: String) : Playable

    /** [id] is the episode's own id, and a string. See Episode.id in core. */
    data class Episode(val id: String, val extension: String) : Playable

    /**
     * A file rather than a broadcast: seekable, with a position worth keeping
     * and an end. Films and episodes behave identically in the player.
     */
    val isOnDemand: Boolean get() = this !is Channel
}
