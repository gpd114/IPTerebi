package com.ipterebi.core

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** A panel address, plus any credentials that were embedded in it. */
data class XtreamEndpoint(
    val base: String,
    val username: String? = null,
    val password: String? = null,
)

/**
 * Nobody pastes a base URL. They paste whatever their provider sent them, which
 * in practice is one of:
 *
 * ```
 * line.example.com:8080
 * http://line.example.com:8080/
 * http://line.example.com:8080/c/
 * http://line.example.com:8080/player_api.php?username=u&password=p
 * http://line.example.com:8080/get.php?username=u&password=p&type=m3u_plus
 * ```
 *
 * The last two carry the credentials in the query string, so pasting one of
 * those into the address field is enough on its own and the username and
 * password fields can be left alone.
 *
 * Note the default scheme is http, not https. Panels are overwhelmingly plain
 * HTTP on a high port; assuming https instead fails the handshake rather than
 * falling back, and the resulting error names TLS, which sends you looking in
 * the wrong place entirely.
 */
object XtreamUrl {

    fun parse(input: String): XtreamEndpoint? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        val absolute = when {
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            else -> "http://$trimmed"
        }

        val url = absolute.toHttpUrlOrNull() ?: return null

        // Everything a panel exposes — player_api.php, get.php, xmltv.php, the
        // /c/ web player — hangs off the same origin, so the path is noise.
        val port = if (url.port == HttpUrl.defaultPort(url.scheme)) "" else ":${url.port}"

        return XtreamEndpoint(
            base = "${url.scheme}://${url.host}$port",
            username = url.queryParameter("username")?.takeIf { it.isNotBlank() },
            password = url.queryParameter("password")?.takeIf { it.isNotBlank() },
        )
    }
}
