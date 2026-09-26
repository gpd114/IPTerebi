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

/**
 * What to say when the request never got an answer at all.
 *
 * The exception's own message is written for a developer reading a stack trace
 * — "Failed to connect to /104.21.0.1:8080", "Unable to resolve host
 * \"panel.example\": No address associated with hostname" — and putting that in
 * front of someone whose provider has gone down tells them nothing they can
 * act on. Each of these failures has a different cause and a different thing to
 * try, so each gets its own sentence.
 *
 * This is worth getting right because it is the message people see on the two
 * worst days: the day their provider's server falls over, and the day they
 * type the address in wrong.
 */
fun describeNetworkFailure(failure: Throwable?, host: String): String {
    val kind = failure?.let { it::class.java.simpleName }.orEmpty()
    val detail = failure?.message.orEmpty()

    return when {
        // The name does not resolve. Either it is wrong, or there is no
        // network at all — and the two are told apart by trying anything else,
        // which is what the second sentence asks for.
        kind == "UnknownHostException" || detail.contains("Unable to resolve host") ->
            "Could not find $host. Check the address for a typo — and check " +
                "this device is online, because a phone with no connection " +
                "fails in exactly this way."

        // Something is listening and said no, at once. A wrong port does this.
        kind == "ConnectException" || detail.contains("ECONNREFUSED", ignoreCase = true) ->
            "$host refused the connection. The port is the usual culprit: a " +
                "panel's web page and its API are often on different ports."

        // Connected — or tried to — and nothing came back. No figure is quoted,
        // because the wait that ended is whichever of the connect timeout and
        // the whole-call budget ran out first: measured on the emulator against
        // an unroutable address it gave up at 11 seconds, and a message
        // promising 15 would have been a small lie.
        kind == "SocketTimeoutException" || detail.contains("timeout", ignoreCase = true) ->
            "$host did not answer in time. The provider's server is probably " +
                "down or overloaded — this is their end, not the app's."

        // Reached the network and it said the host is not there.
        kind == "NoRouteToHostException" || detail.contains("EHOSTUNREACH", ignoreCase = true) ->
            "$host cannot be reached from this network. If you are on mobile " +
                "data or a VPN, try the other one."

        // Almost always https against a plain-HTTP panel.
        kind.startsWith("SSL") || detail.contains("SSL", ignoreCase = true) ->
            "The secure connection to $host failed. Most panels are plain " +
                "http on a high port — try the address with http:// instead of https://."

        detail.isNotBlank() -> "Could not reach $host: $detail."
        else -> "Could not reach $host."
    }
}
