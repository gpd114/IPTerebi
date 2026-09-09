package com.ipterebi.core

/**
 * Panels answer with status codes that mean something other than what the code
 * normally means, and a bare "HTTP 512" sends people to reinstall the app when
 * the real answer is "you are already watching something on the other TV".
 * Turning them into a sentence is most of the support burden of an IPTV client.
 */

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
    // Not a real HTTP code. Several panel forks return it for exactly one
    // condition, and it is worth naming rather than reporting as a number.
    456 -> "The line has hit its connection limit."
    404 -> "The channel is not there. Either it has been removed from your " +
        "package, or this panel does not serve this output format — try " +
        "switching between MPEG-TS and HLS in settings."
    in 500..599 -> "The provider's server failed on this channel ($code). " +
        "Other channels will probably still work."
    else -> "The stream could not be opened (HTTP $code)."
}
