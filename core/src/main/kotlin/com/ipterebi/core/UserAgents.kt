package com.ipterebi.core

/**
 * Identities worth trying against a panel that is refusing us.
 *
 * Which one a panel accepts is arbitrary and undocumented — it is whatever the
 * provider's blocklist happens to say — so this is a list to work through, not
 * a ranking. The tell that this is the problem at all: the API calls all
 * succeed and every stream returns 403.
 */
object UserAgents {

    /** What most IPTV clients send, and the most widely accepted. */
    const val VLC = "VLC/3.0.20 LibVLC/3.0.20"

    /** ffmpeg's own. Some panels allow this where they refuse VLC. */
    const val FFMPEG = "Lavf/60.16.100"

    /** Media3's default, near enough. Occasionally the one that works. */
    const val EXOPLAYER = "ExoPlayer/1.4.1"

    /** Honest. Refused more often than the others, which is why it is not the default. */
    const val HONEST = "IPTerebi"

    val presets: List<Pair<String, String>> = listOf(
        "VLC" to VLC,
        "ffmpeg" to FFMPEG,
        "ExoPlayer" to EXOPLAYER,
        "IPTerebi" to HONEST,
    )
}
