package com.ipterebi.core

import java.util.Locale

/**
 * The MIME type to hand another video player along with a stream, from the
 * extension the stream is served with: the live format's (`ts`, `m3u8`) for a
 * channel, the file's own for a film or an episode.
 *
 * Stated rather than left to the other app, because Android picks which apps
 * to offer from the type: a bare URL with no type offers browsers, and a
 * `.ts` link that a player cannot tell is video offers nothing at all. Anything
 * unrecognised is `video/`-something, which every player accepts.
 */
fun playerMimeType(extension: String): String = when (extension.lowercase(Locale.ROOT).removePrefix(".")) {
    "ts", "m2ts", "mts" -> "video/mp2t"
    "m3u8" -> "application/x-mpegurl"
    "mp4", "m4v" -> "video/mp4"
    "mkv" -> "video/x-matroska"
    "avi" -> "video/x-msvideo"
    "webm" -> "video/webm"
    "mov" -> "video/quicktime"
    "flv" -> "video/x-flv"
    "wmv" -> "video/x-ms-wmv"
    "3gp" -> "video/3gpp"
    else -> "video/*"
}
