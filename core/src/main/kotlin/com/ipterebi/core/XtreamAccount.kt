package com.ipterebi.core

/**
 * One Xtream Codes line: where the panel is, who we are on it, and how we want
 * the video handed to us.
 *
 * [base] is scheme, host and port only — no path and no trailing slash. Build
 * it with [XtreamUrl.parse] rather than by hand; what a provider emails a
 * customer is rarely in that shape.
 */
data class XtreamAccount(
    val base: String,
    val username: String,
    val password: String,
    val format: StreamFormat = StreamFormat.TS,
    val userAgent: String = DEFAULT_USER_AGENT,
) {
    companion object {
        /**
         * Panels routinely refuse clients they do not recognise, and every
         * mainstream IPTV player identifies as VLC or as ffmpeg for that
         * reason. Announcing ourselves honestly draws a 403 from a meaningful
         * share of providers, and a 403 on the stream while the API calls all
         * succeed reads as "wrong password" when it is nothing of the kind.
         * Overridable per account for the provider that wants something else.
         */
        const val DEFAULT_USER_AGENT: String = UserAgents.VLC
    }
}

/**
 * The container a live stream is requested in. Xtream serves the same channel
 * at `.ts` or at `.m3u8`, and which one works is a property of the panel rather
 * than of the channel.
 */
enum class StreamFormat(val extension: String, val label: String) {
    /**
     * Raw MPEG-TS. Served by effectively every panel, so it is the default.
     * The stream is unbounded and unseekable: the player reports no duration
     * and the seek bar stays empty, which is correct for live television and
     * not a fault to go chasing.
     */
    TS("ts", "MPEG-TS"),

    /**
     * HLS. Reaches first frame sooner and recovers from a dropped connection on
     * its own, but a good number of panels advertise `m3u8` in
     * `allowed_output_formats` and then serve a playlist that 404s. Worth
     * trying when a channel stutters; not worth defaulting to.
     */
    HLS("m3u8", "HLS"),
}

/** Anything the panel did that we could not turn into an answer. */
class XtreamException(message: String, cause: Throwable? = null) : Exception(message, cause)
