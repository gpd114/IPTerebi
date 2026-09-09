package com.ipterebi.core

import java.security.MessageDigest

/**
 * The lists the user builds rather than the provider: starred channels and
 * recently watched ones.
 *
 * Here rather than in the app because none of it needs Android and all of it is
 * fiddly in ways that are invisible until they are wrong — a favourite that
 * silently duplicates, a recents list that grows without bound, a starred
 * channel that comes back under a name the provider has since changed.
 *
 * Channels are matched on [LiveStream.streamId] alone. Providers rename
 * channels constantly ("BBC One" becomes "UK: BBC ONE HD" overnight) and a list
 * that matched on the whole record would quietly lose every entry the next time
 * the provider tidied up.
 */

/**
 * A stable, non-reversible identifier for one line.
 *
 * These lists are stored per line: stream ids are a panel's private numbering,
 * so id 4271 on one provider and 4271 on another are unrelated channels, and a
 * shared list would show a favourite that plays something else entirely.
 *
 * Hashed rather than stored as `base|username` because it ends up in a
 * preferences key: the username is half of a credential pair, and there is no
 * reason to write it anywhere it is not needed. Sixteen hex characters is 64
 * bits, which is ample for telling apart the handful of lines one person has.
 */
val XtreamAccount.lineKey: String
    get() = MessageDigest.getInstance("SHA-256")
        .digest("$base|$username".toByteArray())
        .joinToString("") { "%02x".format(it) }
        .take(16)

/** Whether [streamId] is in this list. */
fun List<LiveStream>.holds(streamId: Int): Boolean = any { it.streamId == streamId }

/**
 * Adds [channel] if it is absent, removes it if it is present.
 *
 * Adding puts it at the end, so a favourites list stays in the order it was
 * built rather than reshuffling itself every time something is starred.
 */
fun List<LiveStream>.withFavouriteToggled(channel: LiveStream): List<LiveStream> {
    if (!channel.isPlayable) return this
    return if (holds(channel.streamId)) {
        filterNot { it.streamId == channel.streamId }
    } else {
        this + channel
    }
}

/**
 * Records [channel] as the most recently watched.
 *
 * Most recent first, no duplicates, oldest dropped past [limit]. Re-watching
 * something already in the list moves it to the front rather than adding it
 * again, and the freshly supplied copy wins so a renamed channel updates rather
 * than showing under whatever it was called the first time.
 */
fun List<LiveStream>.withRecent(channel: LiveStream, limit: Int = RECENTS_LIMIT): List<LiveStream> {
    if (!channel.isPlayable) return this
    return (listOf(channel) + filterNot { it.streamId == channel.streamId }).take(limit)
}

/** How many recently watched channels are kept. */
const val RECENTS_LIMIT: Int = 20

/**
 * Whether this channel is worth storing or opening at all. A stream id of zero
 * is what the flexible parsing answers for one it could not read, and the URL
 * built from it 404s — see [playableChannels].
 */
val LiveStream.isPlayable: Boolean get() = streamId > 0
