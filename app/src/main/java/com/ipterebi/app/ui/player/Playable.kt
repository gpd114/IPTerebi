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

    /**
     * A programme that has already been on, kept by the provider.
     *
     * Addressed by *when* rather than by what: the id of the channel, the
     * instant it started and how many minutes to ask for — see `catchUpUrl`.
     * It behaves like a channel and not like a film, which is the surprise
     * here: a panel serves it as a stream with no length and nothing to seek
     * in, so it gets the channel's treatment throughout, including reconnects
     * that rejoin rather than resume at a byte.
     */
    data class CatchUp(val channelId: Int, val startSeconds: Long, val minutes: Int) : Playable

    /** [extension] is the film's own container — mp4, mkv — not a format this app picked. */
    data class Film(val id: Int, val extension: String) : Playable

    /** [id] is the episode's own id, and a string. See Episode.id in core. */
    data class Episode(val id: String, val extension: String) : Playable

    /**
     * A file rather than a broadcast: seekable, with a position worth keeping
     * and an end. Films and episodes behave identically in the player; a
     * catch-up does not, for the reason given above.
     */
    val isOnDemand: Boolean get() = this is Film || this is Episode

    /**
     * Live television or a recording of it — the same thing to the player.
     * Both are streams with no length, no seekable window and a reconnect that
     * rejoins rather than resumes at a byte.
     */
    val isBroadcast: Boolean get() = this is Channel || this is CatchUp

    /**
     * The channel this is, or the channel this is a recording of. Null for a
     * film or an episode, which belong to no channel. It is what the name, the
     * logo and the notification are looked up by.
     */
    val channelOrNull: Int?
        get() = when (this) {
            is Channel -> id
            is CatchUp -> channelId
            else -> null
        }
}
