package com.ipterebi.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ipterebi.core.StreamFormat
import com.ipterebi.core.UserAgents
import com.ipterebi.core.XtreamAccount
import com.ipterebi.core.XtreamClient
import com.ipterebi.core.XtreamUrl
import com.ipterebi.core.connectionsLabel
import com.ipterebi.core.describeNetworkFailure
import com.ipterebi.core.expiryLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Signing in, which is the one screen that had to exist before anything else
 * could be tried.
 *
 * It does no parsing and no diagnosing of its own: `XtreamUrl.parse` takes the
 * `host:port`, the `player_api.php` link or the `get.php?...&type=m3u_plus`
 * playlist link people are actually sent and lifts the credentials out of the
 * last two, and `describeNetworkFailure` turns a failure into the thing to
 * try. Both are in `:core` and already tested, which is the entire argument
 * for this module existing at all.
 */
@Composable
fun SignInScreen(
    client: XtreamClient,
    onSignedIn: (XtreamAccount) -> Unit,
) {
    var address by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var format by remember { mutableStateOf(StreamFormat.TS) }
    var agent by remember { mutableStateOf(XtreamAccount.DEFAULT_USER_AGENT) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val vlcMissing = remember { !VlcPlayer.vlcLooksInstalled() }

    fun signIn() {
        val endpoint = XtreamUrl.parse(address)
        if (endpoint == null) {
            problem = "That does not look like an address. Paste what your " +
                "provider sent you — a host and port, a player_api.php link, " +
                "or an m3u playlist link."
            return
        }
        // A playlist link carries the credentials in its query string, so the
        // two fields are a fallback rather than the source.
        val user = endpoint.username?.takeIf { it.isNotBlank() } ?: username.trim()
        val pass = endpoint.password?.takeIf { it.isNotBlank() } ?: password
        if (user.isBlank() || pass.isBlank()) {
            problem = "A username and password are needed, unless the link you " +
                "pasted already has them in it."
            return
        }

        val account = XtreamAccount(
            base = endpoint.base,
            username = user,
            password = pass,
            format = format,
            userAgent = agent,
        )

        busy = true
        problem = null
        note = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching { client.authenticate(account) }
            }
            busy = false
            outcome.fold(
                onSuccess = { status ->
                    // A rejected login is HTTP 200 with auth: 0 in the body,
                    // never a 401, so the status code has told us nothing and
                    // this is where the answer actually is.
                    if (status.info.auth != 1) {
                        problem = status.info.message.takeIf { it.isNotBlank() }
                            ?: "The panel refused that username and password."
                        return@fold
                    }
                    note = buildString {
                        append(status.info.status.ifBlank { "Signed in" })
                        append(" · ")
                        append(status.info.connectionsLabel())
                        append(" · ")
                        append(status.info.expiryLabel())
                    }
                    Prefs.save(account)
                    onSignedIn(account)
                },
                onFailure = { failure ->
                    problem = describeNetworkFailure(
                        failure = failure,
                        host = endpoint.base,
                        knownGood = false,
                    )
                },
            )
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Panel(Modifier.widthIn(max = 560.dp)) {
            Text("IPTerebi", color = Ink.ink, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Your line on an Xtream Codes panel.",
                color = Ink.inkSoft,
                fontSize = 14.sp,
            )

            if (vlcMissing) {
                Spacer(Modifier.height(18.dp))
                Text(
                    "VLC is not installed, so nothing will play yet. Install the " +
                        "64-bit VLC for Windows and restart this app — it is looked " +
                        "for in ${VlcPlayer.usualPlace}. Signing in works without it.",
                    color = Ink.pink,
                    fontSize = 13.sp,
                )
            }

            Spacer(Modifier.height(22.dp))
            Field("Address", address, { address = it; problem = null })
            Spacer(Modifier.height(14.dp))
            Row {
                Field("Username", username, { username = it; problem = null }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Field("Password", password, { password = it; problem = null }, secret = true, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(18.dp))
            ChoiceRow(
                label = "Stream format",
                options = StreamFormat.entries.toList(),
                chosen = format,
                name = { it.label },
                onChoose = { format = it },
            )
            Spacer(Modifier.height(16.dp))
            ChoiceRow(
                label = "Identify as",
                options = listOf(UserAgents.VLC, UserAgents.FFMPEG, UserAgents.EXOPLAYER),
                chosen = agent,
                name = ::agentLabel,
                onChoose = { agent = it },
            )

            problem?.let {
                Spacer(Modifier.height(18.dp))
                Text(it, color = Ink.pink, fontSize = 13.sp)
            }
            note?.let {
                Spacer(Modifier.height(18.dp))
                Text(it, color = Ink.accent, fontSize = 13.sp)
            }

            Spacer(Modifier.height(24.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                PrimaryButton(
                    label = if (busy) "Checking the line…" else "Sign in",
                    onClick = ::signIn,
                    enabled = !busy,
                )
            }
        }
    }
}

/**
 * The short name for a user-agent string, for the row of choices.
 *
 * Local rather than in `:core` because it is wording for one screen, and the
 * Android build says the same thing in its own resources. The string itself is
 * what matters and that is shared.
 */
private fun agentLabel(agent: String): String = when (agent) {
    UserAgents.VLC -> "VLC"
    UserAgents.FFMPEG -> "ffmpeg"
    UserAgents.EXOPLAYER -> "ExoPlayer"
    UserAgents.HONEST -> "IPTerebi"
    else -> "Custom"
}
