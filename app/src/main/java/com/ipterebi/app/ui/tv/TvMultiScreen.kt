package com.ipterebi.app.ui.tv

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.ipterebi.app.AppContainer
import com.ipterebi.app.BuildConfig
import com.ipterebi.app.TAG_PLAY
import com.ipterebi.app.ui.player.StreamRetryPolicy
import com.ipterebi.app.ui.theme.Corners
import com.ipterebi.app.ui.theme.Night
import com.ipterebi.core.LiveStream
import com.ipterebi.core.MAX_PANES
import com.ipterebi.core.Pane
import com.ipterebi.core.StreamFormat
import com.ipterebi.core.XtreamAccount
import com.ipterebi.core.describePaneRefusal
import com.ipterebi.core.lineKey
import com.ipterebi.core.paneGrid
import com.ipterebi.core.paneOpenDelayMillis
import com.ipterebi.core.withSoundOn
import kotlinx.coroutines.delay

/**
 * Several channels at once, on a line that would rather give you one.
 *
 * **Read `core/MultiView.kt` before changing this.** Every pane here is its
 * own `ExoPlayer` and so its own connection to the panel, and a line usually
 * allows one — so on most lines this screen shows one picture and three
 * refusals, and that is the provider's answer rather than a fault to fix. The
 * whole design is about saying so plainly: panes are asked for in turn rather
 * than together, and a refused one names the viewer's own panes as what is
 * using the line.
 *
 * The remote:
 *
 * - **Arrows** move between panes.
 * - **OK** moves the sound to the pane with focus. Exactly one is ever heard;
 *   four commentaries is nobody's idea of a feature.
 * - **Back** leaves, which stops every player and gives the line back.
 *
 * It opens on the favourites, in their order, because on this owner's line
 * those are all sports channels and side by side is what they are for. No
 * picker: choosing four channels on a D-pad is a bigger feature than this
 * one, and Favourites is already the list somebody curated.
 */
@Composable
fun TvMultiScreen(container: AppContainer) {
    // The same model Live TV uses, for the same favourites and recents: this
    // screen is a different arrangement of that list, not a different list.
    val model: TvLiveViewModel =
        viewModel(factory = TvLiveViewModel.factory(container, LocalContext.current))
    val state by model.state.collectAsStateWithLifecycle()
    val account = state.account ?: return

    // Favourites in order, up to what a box will decode; recents when nothing
    // is starred, so the screen is never blank for somebody who has not got
    // round to it.
    val channels: List<LiveStream> = remember(state.favourites, state.recents) {
        (state.favourites.takeIf { it.isNotEmpty() } ?: state.recents).take(MAX_PANES)
    }

    if (channels.isEmpty()) {
        Box(
            Modifier.fillMaxSize().background(Night.ground),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Star a few channels on Live TV and they will appear here, side by side.",
                style = MaterialTheme.typography.titleMedium,
                color = Night.inkSoft,
                modifier = Modifier.padding(48.dp),
            )
        }
        return
    }

    var panes by remember(channels) {
        mutableStateOf(
            channels.map { Pane(it.streamId) }.withSoundOn(channels.first().streamId)
        )
    }
    val grid = paneGrid(panes.size)

    // Why each pane could not start -- the panel's code, not a sentence.
    //
    // **The code, because the sentence depends on the others.** Rendering it
    // when a pane fails counts the panes that have not failed *yet*: the
    // first refusal of four said "3 panes are already using it" when nothing
    // was playing at all, because the other three had not had their turn.
    // Kept as a code and worded at draw time, it settles on the truth.
    val trouble = remember(channels) { mutableStateMapOf<Int, Int>() }
    val playing = panes.count { !trouble.containsKey(it.streamId) }

    Column(
        Modifier.fillMaxSize().background(Color.Black).padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (row in 0 until grid.rows) {
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (column in 0 until grid.columns) {
                    val index = row * grid.columns + column
                    val pane = panes.getOrNull(index)
                    if (pane == null) {
                        // The spare cell of a three-pane grid. Black, not a
                        // placeholder: a box inviting a fourth stream is an
                        // invitation to be refused.
                        Box(Modifier.weight(1f).fillMaxHeight())
                        continue
                    }
                    val channel = channels.first { it.streamId == pane.streamId }
                    MultiPane(
                        container = container,
                        account = account,
                        channel = channel,
                        sound = pane.sound,
                        openAfter = paneOpenDelayMillis(index),
                        // What this pane says for itself, worded now rather
                        // than when it failed. A refusal only has to count
                        // the others, so this pane is not in its own total.
                        trouble = trouble[pane.streamId]?.let { code ->
                            describePaneRefusal(code, playing)
                        },
                        onTrouble = { code -> trouble[pane.streamId] = code },
                        onPlaying = { trouble.remove(pane.streamId) },
                        onTakeSound = { panes = panes.withSoundOn(pane.streamId) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/**
 * One pane: a player, its name, and whatever the panel said when it refused.
 *
 * [openAfter] is why each pane is its own composable — every one waits its
 * turn before asking, so a line is never hit with four requests at once. See
 * `PANE_OPEN_GAP_MS` for the measurement behind that.
 */
@Composable
private fun MultiPane(
    container: AppContainer,
    account: XtreamAccount,
    channel: LiveStream,
    sound: Boolean,
    openAfter: Long,
    trouble: String?,
    onTrouble: (Int) -> Unit,
    onPlaying: () -> Unit,
    onTakeSound: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }

    val player = remember(channel.streamId, account.lineKey, account.userAgent, account.format) {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(account.userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val sources = DefaultMediaSourceFactory(http).apply {
            if (account.format == StreamFormat.TS) {
                setLoadErrorHandlingPolicy(StreamRetryPolicy(live = true))
            }
        }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(sources)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                // Not this player's to grab. Four panes competing for audio
                // focus would duck one another endlessly; which one is heard
                // is decided here, not by Android.
                /* handleAudioFocus = */ false,
            )
            .build()
    }

    // Muted rather than stopped. A muted pane is still a connection, and
    // pausing one to save the line would freeze its picture, which is the
    // opposite of what this screen is for.
    LaunchedEffect(player, sound) { player.volume = if (sound) 1f else 0f }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // 0 for anything that is not an HTTP refusal, which
                // describePaneRefusal words as "did not start".
                val code = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode ?: 0
                if (BuildConfig.DEBUG) {
                    Log.d(TAG_PLAY, "multi pane ${channel.streamId}: ${code.takeIf { it > 0 } ?: error.errorCodeName}")
                }
                onTrouble(code)
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) onPlaying()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            // Stopped before released: a paused player keeps its connection,
            // and on a line that allows one that is the whole screen's worth.
            player.stop()
            player.release()
        }
    }

    // Its turn to ask.
    LaunchedEffect(player) {
        delay(openAfter)
        player.setMediaItem(
            MediaItem.fromUri(container.xtream.liveStreamUrl(account, channel.streamId))
        )
        player.playWhenReady = true
        player.prepare()
    }

    Box(
        modifier
            .clip(Corners.card)
            .background(Color.Black)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                val centre = event.key == Key.DirectionCenter || event.key == Key.Enter
                if (event.type == KeyEventType.KeyUp && centre) {
                    onTakeSound()
                    true
                } else {
                    false
                }
            }
            .focusable(),
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    // The pane draws its own name; the player's controls would
                    // take the remote away from the grid.
                    useController = false
                    isFocusable = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        // What the panel said, over the pane that could not start. The others
        // carry on: one refusal is not the screen's failure.
        trouble?.let { why ->
            Box(
                Modifier.fillMaxSize().background(Night.veil),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    why,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Night.ink,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        // The name, and which one you can hear. Small on purpose: the picture
        // is the point, and four labels should not read as a menu.
        Text(
            (if (sound) "♪  " else "") + channel.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (focused) Night.accent else Night.inkSoft,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
        )
    }
}
