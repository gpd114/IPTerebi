package com.ipterebi.core

/**
 * Panels answer with status codes that mean something other than what the code
 * normally means, and a bare "HTTP 512" sends people to reinstall the app when
 * the real answer is "you are already watching something on the other TV".
 * Turning them into a sentence is most of the support burden of an IPTV client.
 */

/**
 * Not real HTTP codes. Panel forks use them for exactly one condition, the
 * connection limit, and it is worth naming rather than reporting as a number.
 *
 * 456 comes from several forks. 458 was measured on a real line: answered to
 * every reconnect for about fifteen seconds after the phone left Wi-Fi, while
 * the panel still counted the connection that had dropped, and then let the
 * next one in.
 */
private val CONNECTION_LIMIT_CODES = setOf(456, 458)

/** A stream refused with one of the codes panels keep for the connection limit. */
fun isConnectionLimit(code: Int): Boolean = code in CONNECTION_LIMIT_CODES

private const val AT_CONNECTION_LIMIT = "The line has hit its connection limit. If " +
    "nothing else is playing, the panel may still be counting a connection that " +
    "just dropped — try again in a few seconds."

fun describeApiHttpError(code: Int, host: String): String = when (code) {
    401, 403 -> "$host refused the request. Either the username and password " +
        "are wrong, or the panel is rejecting this client — try a different " +
        "user agent in settings."
    404 -> "$host has no player_api.php. Check the address and the port: the " +
        "panel's web page and its API are often on different ports."
    429 -> "$host is rate limiting us. Wait a minute before trying again."
    in 500..599 -> "$host returned a server error ($code). This is the " +
        "provider's end, not the app's."
    else -> "$host returned HTTP $code."
}

fun describeStreamHttpError(code: Int): String = when (code) {
    401, 403 -> "The panel refused the stream. The usual cause is the " +
        "connection limit on your line — close whatever else is playing, and " +
        "give the panel a moment to notice the old connection has gone."
    in CONNECTION_LIMIT_CODES -> AT_CONNECTION_LIMIT
    404 -> "The channel is not there. Either it has been removed from your " +
        "package, or this panel does not serve this output format — try " +
        "switching between MPEG-TS and HLS in settings."
    in 500..599 -> "The provider's server failed on this channel ($code). " +
        "Other channels will probably still work."
    else -> "The stream could not be opened (HTTP $code)."
}

/**
 * The same, for something on demand — a film or an episode. Mostly the same
 * sentences, because the connection limit applies to them exactly as it does to
 * channels — but a 404 cannot be answered with "try the other stream format",
 * because that setting has nothing to do with files. Saying it would send
 * someone to flip a switch that changes nothing, and then conclude the app is
 * broken.
 *
 * [noun] is what to call the thing, so an episode is not called a film.
 */
fun describeOnDemandHttpError(code: Int, noun: String): String = when (code) {
    401, 403 -> "The panel refused the $noun. The usual cause is the connection " +
        "limit on your line — close whatever else is playing, and give the " +
        "panel a moment to notice the old connection has gone."
    in CONNECTION_LIMIT_CODES -> AT_CONNECTION_LIMIT
    404 -> "The $noun is not there. It may have been taken out of your package, " +
        "or the panel has lost the file."
    in 500..599 -> "The provider's server failed on this $noun ($code). " +
        "Others will probably still play."
    else -> "The $noun could not be opened (HTTP $code)."
}

fun describeFilmHttpError(code: Int): String = describeOnDemandHttpError(code, "film")

fun describeEpisodeHttpError(code: Int): String = describeOnDemandHttpError(code, "episode")
