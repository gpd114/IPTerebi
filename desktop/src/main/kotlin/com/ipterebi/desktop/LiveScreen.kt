package com.ipterebi.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ipterebi.core.LiveStream
import com.ipterebi.core.XtreamAccount
import com.ipterebi.core.XtreamCategory
import com.ipterebi.core.XtreamClient
import com.ipterebi.core.describeNetworkFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Live television: the provider's groups down the left, that group's channels
 * beside them, and the picture filling the rest.
 *
 * **One category at a time**, which is the rule the Android build follows for
 * the reason written down in CLAUDE.md: `get_live_streams` with no category is
 * several megabytes on a real line — 7.8 MB on the owner's — so the whole line
 * is never pulled to draw a list.
 *
 * **One player for the screen**, as `TvLiveScreen` has. Changing channel stops
 * the old stream before asking for the next, because a line usually allows one
 * connection and a panel takes a moment to notice the previous one has gone.
 */
@Composable
fun LiveScreen(
    account: XtreamAccount,
    client: XtreamClient,
    onSignOut: () -> Unit,
) {
    var groups by remember { mutableStateOf<List<XtreamCategory>>(emptyList()) }
    var openGroup by remember { mutableStateOf<XtreamCategory?>(null) }
    var channels by remember { mutableStateOf<List<LiveStream>>(emptyList()) }
    var tuned by remember { mutableStateOf<LiveStream?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var state by remember { mutableStateOf("Nothing playing") }

    val scope = rememberCoroutineScope()

    // Built once for the screen and released with it. Null when VLC is not
    // installed, which the sign-in screen has already said out loud.
    val player = remember { VlcPlayer.createOrNull() }
    DisposableEffect(player) {
        player?.onPlaying = { state = "Playing" }
        player?.onFailure = {
            state = "That channel would not start. Another may; the line may " +
                "also be in use somewhere else."
        }
        onDispose {
            player?.stop()
            player?.release()
        }
    }

    LaunchedEffect(account) {
        val outcome = withContext(Dispatchers.IO) {
            runCatching { client.liveCategories(account) }
        }
        outcome.fold(
            onSuccess = { groups = it },
            onFailure = {
                problem = describeNetworkFailure(it, account.base, knownGood = true)
            },
        )
    }

    fun open(group: XtreamCategory) {
        openGroup = group
        channels = emptyList()
        problem = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching { client.liveStreams(account, group.id) }
            }
            outcome.fold(
                onSuccess = { channels = it },
                onFailure = {
                    problem = describeNetworkFailure(it, account.base, knownGood = true)
                },
            )
        }
    }

    fun tune(channel: LiveStream) {
        // Stopped before the next is asked for, never left paused: a paused
        // player holds the connection and the next request is then refused by
        // our own line.
        player?.stop()
        tuned = channel
        state = "Opening ${channel.name}…"
        player?.play(client.liveStreamUrl(account, channel.streamId), account.userAgent)
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(account = account, state = state, onSignOut = onSignOut)

        Row(Modifier.fillMaxSize()) {
            // The provider's groups.
            Column(
                Modifier.width(260.dp).fillMaxHeight()
                    .background(Ink.veil)
                    .padding(8.dp)
            ) {
                Text(
                    "GROUPS",
                    color = Ink.inkSoft,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(8.dp),
                )
                LazyColumn {
                    items(groups, key = { it.id }) { group ->
                        PickRow(
                            label = group.name,
                            picked = group.id == openGroup?.id,
                            onClick = { open(group) },
                        )
                    }
                }
            }

            // That group's channels.
            Column(
                Modifier.width(320.dp).fillMaxHeight()
                    .background(Ink.ground)
                    .padding(8.dp)
            ) {
                Text(
                    openGroup?.name?.uppercase() ?: "PICK A GROUP",
                    color = Ink.inkSoft,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(8.dp),
                )
                problem?.let {
                    Text(it, color = Ink.pink, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                }
                LazyColumn {
                    items(channels, key = { it.streamId }) { channel ->
                        PickRow(
                            // The provider's own number where it has one.
                            label = if (channel.number > 0) {
                                "${channel.number}  ${channel.name}"
                            } else channel.name,
                            picked = channel.streamId == tuned?.streamId,
                            onClick = { tune(channel) },
                            mark = channel.hasCatchUp,
                        )
                    }
                }
            }

            // The picture.
            Box(
                Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (player == null) {
                    Text(
                        "VLC is not installed, so there is nothing to play with. " +
                            "Install the 64-bit VLC for Windows and restart.",
                        color = Ink.inkSoft,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(32.dp),
                    )
                } else {
                    // libvlc draws into a native component, so the video is a
                    // Swing island inside Compose rather than something Compose
                    // paints. That is why the state line lives in the bar above
                    // rather than over the picture: nothing Compose draws can
                    // sit on top of this.
                    SwingPanel(
                        background = Color.Black,
                        factory = { player.component },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    account: XtreamAccount,
    state: String,
    onSignOut: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .background(Ink.veil)
            .border(1.dp, Ink.hairline)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("IPTerebi", color = Ink.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(16.dp))
        Text(state, color = Ink.inkSoft, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        // The host only. The username is half of a credential pair and has no
        // business being drawn on a screen somebody might screenshot.
        Text(account.base, color = Ink.inkSoft, fontSize = 12.sp)
        Spacer(Modifier.width(16.dp))
        SecondaryButton("Sign out", onSignOut)
    }
}

/** A row in either list: filled when it is the one in use. */
@Composable
private fun PickRow(
    label: String,
    picked: Boolean,
    onClick: () -> Unit,
    mark: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (picked) Ink.cobalt else Color.Transparent, Corners.card)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (picked) Color.White else Ink.ink,
            fontSize = 14.sp,
            maxLines = 2,
            modifier = Modifier.weight(1f),
        )
        if (mark) {
            Spacer(Modifier.width(6.dp))
            // The same thing the phone's channel row says with a replay icon:
            // this is one of the copies that keeps a recording, and on a real
            // line the identical channel next to it very often does not.
            Text("⟲", color = if (picked) Color.White else Ink.accent, fontSize = 14.sp)
        }
    }
}
