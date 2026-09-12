package com.ipterebi.app.ui.tv

import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ipterebi.app.AppContainer
import com.ipterebi.app.BuildConfig
import com.ipterebi.app.TAG_PLAY
import com.ipterebi.app.playback.ActivePlayback
import com.ipterebi.app.ui.player.DelayedOpenFactory
import com.ipterebi.app.ui.player.MediaSessionFor
import com.ipterebi.app.ui.player.ProgrammeGuide
import com.ipterebi.app.ui.player.StreamRetryPolicy
import com.ipterebi.app.ui.player.rememberProgrammeGuide
import com.ipterebi.core.LineWait
import com.ipterebi.core.LiveStream
import com.ipterebi.core.StreamFormat
import com.ipterebi.core.StreamReconnect
import com.ipterebi.core.XtreamAccount
import com.ipterebi.core.describeStreamHttpError
import com.ipterebi.core.lineKey
import com.ipterebi.core.typedChannelNumber
import com.ipterebi.core.zap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Live television as a set-top box does it, and the TV app's home.
 *
 * Opens on the channel left on last time, already playing. The remote then
 * works the way a TV remote does — TiviMate's layout, which is what people
 * coming from it will reach for:
 *
 * - **Up / Down** (and Channel +/−): the next or previous channel in the group.
 * - **OK**: the channels with their guide, the picture still on in a corner.
 * - **Left**: back to the channel before — the flip between two.
 * - **Right**: what is on, and next.
 * - **Number keys**: straight to a channel.
 * - **Back**: twice to leave, so one stray press does not end the evening.
 *
 * One player for the whole screen, reused from channel to channel: each change
 * stops the stream first and asks for the next a moment later, so a line that
 * allows one connection never sees two — and a run of presses through ten
 * channels opens one stream, not ten.
 */
@Composable
fun TvLiveScreen(container: AppContainer, onOpen: (TvDestination) -> Unit) {
    val context = LocalContext.current
    val model: TvLiveViewModel = viewModel(factory = TvLiveViewModel.factory(container, context))
    val state by model.state.collectAsStateWithLifecycle()
    val account = state.account
    if (account == null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = TvAccent)
        }
        return
    }
    TvLive(container, model, state, account, onOpen)
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
private fun TvLive(
    container: AppContainer,
    model: TvLiveViewModel,
    state: TvLiveState,
    account: XtreamAccount,
    onOpen: (TvDestination) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // What is on, the channel before it, and the group being zapped through.
    // Saveable, so Films and back returns to the same channel.
    var tunedId by rememberSaveable { mutableIntStateOf(0) }
    var previousId by rememberSaveable { mutableIntStateOf(0) }
    var groupKey by rememberSaveable { mutableStateOf<String?>(null) }
    // Bumped to ask for the same channel again: Try again, Play here, coming back.
    var attempt by remember { mutableIntStateOf(0) }

    val tuned = state.find(tunedId)
    val group: TvGroup = TvGroup.fromKey(groupKey)
        ?: state.savedGroup?.takeIf { state.channelsIn(it).any { c -> c.streamId == tunedId } }
        ?: tuned?.categoryId?.takeIf { state.lineup?.group(it) != null }?.let { TvGroup.Category(it) }
        ?: TvGroup.All

    var listOpen by remember { mutableStateOf(false) }
    // It covers the picture (bar the corner it plays in), and takes the remote.
    val covered = listOpen
    var bannerAt by remember { mutableLongStateOf(0L) }
    var bannerShown by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var noSuchNumber by remember { mutableStateOf<String?>(null) }
    var armedToLeave by remember { mutableStateOf(false) }

    fun showBanner() {
        bannerShown = true
        bannerAt = SystemClock.elapsedRealtime()
    }

    fun tune(channel: LiveStream, inGroup: TvGroup? = null) {
        if (inGroup != null && inGroup.key != groupKey) {
            groupKey = inGroup.key
            model.rememberGroup(inGroup)
        }
        if (channel.streamId != tunedId) {
            previousId = tunedId
            tunedId = channel.streamId
        }
        showBanner()
    }

    // The channel to open on: the last one watched, else the top of the list.
    // Waits for recents to be read — an empty list before then means "not read
    // yet", and the first channel would be opened over last night's.
    LaunchedEffect(state.recentsRead, state.lineup) {
        if (tunedId != 0 || !state.recentsRead) return@LaunchedEffect
        val start = state.recents.firstOrNull()
            ?: state.savedGroup?.let { state.channelsIn(it).firstOrNull() }
            ?: state.lineup?.all?.firstOrNull()
        if (start != null) {
            tunedId = start.streamId
            showBanner()
        }
    }

    // --- The player -------------------------------------------------------

    val reconnect = remember { StreamReconnect() }
    val lineWait = remember { LineWait() }
    val reconnectDelay = remember { AtomicLong(0) }
    val flags = remember { TuneFlags() }
    var error by remember { mutableStateOf<String?>(null) }
    var reconnecting by remember { mutableStateOf(false) }
    var waitingForLine by remember { mutableStateOf(false) }
    var released by remember { mutableStateOf(false) }
    val currentChannel by rememberUpdatedState(tuned)
    /** The channel whose guide the banner shows; see below. */
    var guideFor by remember { mutableIntStateOf(0) }

    val player = remember(account.lineKey, account.userAgent, account.format) {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(account.userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val sources = DefaultMediaSourceFactory(DelayedOpenFactory(http, reconnectDelay)).apply {
            if (account.format == StreamFormat.TS) setLoadErrorHandlingPolicy(StreamRetryPolicy(live = true))
        }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(sources)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus = */ true,
            )
            .build()
            .apply { playWhenReady = true }
    }

    DisposableEffect(player) {
        /** Asks again after [wait], at the live edge, reading as buffering meanwhile. */
        fun askAgainIn(wait: Long) {
            reconnectDelay.set(wait)
            player.stop()
            player.seekToDefaultPosition()
            player.prepare()
        }

        /** Stopped by itself after playing: reconnect, or say why. See PlayerScreen's dropped(). */
        fun dropped(why: String) {
            val wait = if (player.playWhenReady) reconnect.onDropped(SystemClock.elapsedRealtime()) else null
            if (wait == null) {
                reconnecting = false
                waitingForLine = false
                error = why
                return
            }
            if (BuildConfig.DEBUG) Log.d(TAG_PLAY, "tv channel $tunedId dropped; reconnecting in $wait ms")
            reconnecting = true
            askAgainIn(wait)
        }

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (BuildConfig.DEBUG) Log.d(TAG_PLAY, "tv channel $tunedId state $playbackState")
                if (playbackState == Player.STATE_ENDED) {
                    dropped("The channel stopped, and reconnecting did not bring it back. It may be off air, or the panel may be down.")
                }
                val channel = currentChannel
                if (playbackState == Player.STATE_READY && !flags.recorded && channel != null) {
                    flags.recorded = true
                    scope.launch { container.channelLists.recordWatched(account, channel) }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) return
                flags.played = true
                reconnect.onPlaying(SystemClock.elapsedRealtime())
                lineWait.reset()
                reconnecting = false
                waitingForLine = false
            }

            override fun onPlayerError(e: PlaybackException) {
                val code = (e.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
                if (BuildConfig.DEBUG) Log.w(TAG_PLAY, "tv channel $tunedId failed: ${e.errorCodeName} ${code ?: ""}")
                // Refused before it ever played, for the connection limit: the
                // line is about to be free — the phone just let go, or the
                // panel has not yet noticed the last channel close — so wait.
                if (code != null && !flags.played) {
                    val wait = lineWait.onRefused(code, SystemClock.elapsedRealtime())
                    if (wait != null) {
                        waitingForLine = true
                        askAgainIn(wait)
                        return
                    }
                    if (waitingForLine) {
                        waitingForLine = false
                        error = LineWait.STILL_IN_USE
                        return
                    }
                }
                dropped(
                    when (val cause = e.cause) {
                        is HttpDataSource.InvalidResponseCodeException -> describeStreamHttpError(cause.responseCode)
                        is HttpDataSource.HttpDataSourceException ->
                            "Could not reach the stream. The panel may be down, or the connection dropped."
                        else -> e.localizedMessage ?: "Playback failed (${e.errorCodeName})."
                    }
                )
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Changing channel. The stream playing is let go at once; the next is asked
    // for after a pause, which a further press cancels — so the panel has
    // closed one connection before being asked for the next, and holding
    // channel-up through ten channels opens one stream at the end.
    LaunchedEffect(player, tunedId, attempt) {
        if (tunedId == 0) return@LaunchedEffect
        player.stop()
        error = null
        reconnecting = false
        waitingForLine = false
        released = false
        reconnect.reset()
        lineWait.reset()
        reconnectDelay.set(0)
        flags.played = false
        flags.recorded = false
        delay(ZAP_SETTLE_MS)
        guideFor = tunedId
        val channel = currentChannel
        if (BuildConfig.DEBUG) {
            // The URL carries the credentials, so only its shape is logged.
            Log.d(TAG_PLAY, "tv open channel $tunedId as ${account.format.label}: ${account.base}/live/***/***/$tunedId.${account.format.extension}")
        }
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(container.xtream.liveStreamUrl(account, tunedId))
                .setMimeType(if (account.format == StreamFormat.HLS) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP2T)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(channel?.name)
                        .setArtist("Live TV")
                        .setArtworkUri(channel?.icon?.takeIf { it.isNotBlank() }?.toUri())
                        .build()
                )
                .build()
        )
        player.prepare()
        player.play()
    }

    MediaSessionFor(player, live = true)

    // The tuned channel's guide, for the banner: asked for once a change of
    // channel has settled, not for every channel passed on the way.
    val guide = if (guideFor != 0) {
        rememberProgrammeGuide(container, account, guideFor, state.find(guideFor)?.epgChannelId)
    } else {
        ProgrammeGuide()
    }

    // Stop from the session, or Free the line in Settings: stopped until asked.
    DisposableEffect(player) {
        val unregister = ActivePlayback.register {
            val wasPlaying = player.playbackState != Player.STATE_IDLE
            released = true
            error = null
            reconnecting = false
            waitingForLine = false
            player.stop()
            wasPlaying
        }
        onDispose { unregister() }
    }

    // Home, another app, or the TV going to standby: the line is let go, and
    // coming back asks for the channel afresh, at the live edge.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        var wasStopped = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    wasStopped = true
                    player.stop()
                }
                Lifecycle.Event.ON_START -> if (wasStopped) {
                    wasStopped = false
                    if (!released) attempt++
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // --- The remote -------------------------------------------------------

    fun zap(step: Int) {
        val next = state.channelsIn(group).zap(tunedId, step) ?: return
        tune(next)
    }

    fun commitNumber() {
        val number = typed.toIntOrNull()
        typed = ""
        val channel = number?.let { state.lineup?.byNumber(it) }
        if (channel == null) {
            noSuchNumber = number?.let { "No channel $it" } ?: return
            return
        }
        // Numbers are the whole line's, so channel up from here goes on by number.
        tune(channel, TvGroup.All)
    }

    LaunchedEffect(typed) {
        if (typed.isEmpty()) return@LaunchedEffect
        delay(NUMBER_WAIT_MS)
        commitNumber()
    }
    LaunchedEffect(noSuchNumber) {
        if (noSuchNumber != null) {
            delay(1_500)
            noSuchNumber = null
        }
    }
    LaunchedEffect(bannerAt) {
        if (bannerAt == 0L) return@LaunchedEffect
        delay(BANNER_MS)
        bannerShown = false
    }
    LaunchedEffect(armedToLeave) {
        if (armedToLeave) {
            delay(2_500)
            armedToLeave = false
        }
    }

    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(covered, error, released) {
        if (!covered && error == null && !released) runCatching { rootFocus.requestFocus() }
    }

    // Back, taken before focus sees it. Compose treats Back as "leave this
    // focus group": pressed in the channel list or on Try again, it moved
    // focus out to the screen and was used up doing so — the list stayed open
    // and the press did nothing visible. So this screen answers Back itself:
    // the list closes, then the banner, then one press arms leaving and the
    // second, within a moment, leaves.
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    fun onBack() {
        when {
            listOpen -> listOpen = false
            bannerShown || typed.isNotEmpty() -> {
                bannerShown = false
                typed = ""
            }
            !armedToLeave -> armedToLeave = true
            // Nothing on this screen is listening now, and live television
            // is the bottom of the stack: Android takes the app to the back.
            else -> {
                armedToLeave = false
                backDispatcher?.onBackPressed()
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { e ->
                if (e.key != Key.Back) return@onPreviewKeyEvent false
                if (e.type == KeyEventType.KeyUp) onBack()
                true
            }
            .onKeyEvent { e ->
                if (covered) return@onKeyEvent false
                val down = e.type == KeyEventType.KeyDown
                val digit = e.digit()
                when {
                    digit != null -> {
                        if (down) typed = typedChannelNumber(typed, digit)
                        true
                    }
                    e.key in ChannelUp -> { if (down) zap(+1); true }
                    e.key in ChannelDown -> { if (down) zap(-1); true }
                    e.key == Key.DirectionLeft || e.key == Key.LastChannel -> {
                        if (down) state.find(previousId)?.let { tune(it) }
                        true
                    }
                    e.key == Key.DirectionRight || e.key == Key.Info -> {
                        // The banner, and again to put it away.
                        if (down) { if (bannerShown) bannerShown = false else showBanner() }
                        true
                    }
                    // On release, not press: the list opens under the remote,
                    // and a row that saw only the release will not take it as a click.
                    e.key in Select -> {
                        if (!down) {
                            if (typed.isNotEmpty()) commitNumber() else listOpen = true
                        }
                        // Left to a focused button — Try again — to press.
                        error == null && !released
                    }
                    e.key == Key.Menu -> { if (!down) listOpen = true; true }
                    e.key == Key.Guide -> { if (!down) listOpen = true; true }
                    else -> false
                }
            }
            .focusRequester(rootFocus)
            .focusable(),
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    // The screen draws its own banner and list; the player's
                    // controls would take the remote.
                    useController = false
                    isFocusable = false
                    keepScreenOn = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                }
            },
            update = { view ->
                view.player = player
                // The player's own spinner, but not behind a message or the
                // list: two things turning in one place reads as a fault.
                val quiet = waitingForLine || error != null || released || covered
                view.setShowBuffering(if (quiet) PlayerView.SHOW_BUFFERING_NEVER else PlayerView.SHOW_BUFFERING_ALWAYS)
            },
            // Under the channel guide, the same player in the corner it leaves:
            // one stream, never a second for a preview.
            modifier = if (listOpen) {
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = GuideTopHeight - GuidePreviewHeight - 12.dp, end = 32.dp)
                    .size(GuidePreviewWidth, GuidePreviewHeight)
            } else {
                Modifier.fillMaxSize()
            },
        )

        if (reconnecting && error == null && !covered) {
            Text(
                "Reconnecting…",
                color = TvInk,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 110.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(TvPanel)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (waitingForLine && error == null && !covered) {
            TvMessage(Modifier.align(Alignment.Center)) {
                CircularProgressIndicator(color = TvAccent, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(16.dp))
                Text("Waiting for your line to free up", style = MaterialTheme.typography.titleLarge, color = TvInk)
                Spacer(Modifier.height(6.dp))
                Text(
                    "It allows one stream at a time, and the provider is still counting one — " +
                        "the channel before, or another device that has just stopped. " +
                        "Trying again every few seconds.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TvInkSoft,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // With nothing tuned: the list could not be had, or the line is empty.
        if (tunedId == 0 && state.recentsRead && !state.loading && !covered &&
            (state.error != null || state.lineup?.all?.isEmpty() == true)
        ) {
            TvMessage(Modifier.align(Alignment.Center)) {
                Text(
                    state.error ?: "This line has no channels.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TvInk,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val retry = remember { FocusRequester() }
                    TvButton("Try again", onClick = model::retry, modifier = Modifier.focusRequester(retry))
                    TvButton("Settings", onClick = { onOpen(TvDestination.Settings) }, primary = false)
                    LaunchedEffect(Unit) { runCatching { retry.requestFocus() } }
                }
            }
        }

        error?.takeIf { !covered }?.let { message ->
            TvMessage(Modifier.align(Alignment.Center)) {
                Text(tuned?.name ?: "This channel", style = MaterialTheme.typography.titleMedium, color = TvInkSoft)
                Spacer(Modifier.height(6.dp))
                Text(message, style = MaterialTheme.typography.bodyLarge, color = TvInk, textAlign = TextAlign.Center)
                Spacer(Modifier.height(18.dp))
                val retry = remember { FocusRequester() }
                TvButton("Try again", onClick = { attempt++ }, modifier = Modifier.focusRequester(retry))
                Spacer(Modifier.height(10.dp))
                Text("▲ ▼ another channel", style = MaterialTheme.typography.labelSmall, color = TvInkSoft)
                LaunchedEffect(message) { runCatching { retry.requestFocus() } }
            }
        }

        if (released && !covered) {
            TvMessage(Modifier.align(Alignment.Center)) {
                Text("Stopped", style = MaterialTheme.typography.titleLarge, color = TvInk)
                Spacer(Modifier.height(6.dp))
                Text("Your line is free for another device.", style = MaterialTheme.typography.bodyMedium, color = TvInkSoft)
                Spacer(Modifier.height(18.dp))
                val play = remember { FocusRequester() }
                TvButton("Play here", onClick = { released = false; attempt++ }, modifier = Modifier.focusRequester(play))
                LaunchedEffect(Unit) { runCatching { play.requestFocus() } }
            }
        }

        AnimatedVisibility(
            // Not under a message, which names the channel itself.
            visible = bannerShown && !covered && tuned != null && error == null && !waitingForLine && !released,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            tuned?.let { channel ->
                InfoBanner(
                    guide = if (guideFor == channel.streamId) guide else ProgrammeGuide(),
                    channel = channel,
                    number = state.lineup?.numberOf(channel.streamId) ?: 0,
                    groupName = state.nameOf(group),
                    favourite = state.isFavourite(channel.streamId),
                )
            }
        }

        if (typed.isNotEmpty() || noSuchNumber != null) {
            Text(
                noSuchNumber ?: typed,
                style = MaterialTheme.typography.displaySmall.copy(fontSize = if (noSuchNumber != null) 24.sp else 44.sp),
                color = TvInk,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 32.dp, end = 48.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(TvPanel)
                    .padding(horizontal = 22.dp, vertical = 10.dp),
            )
        }

        if (armedToLeave) {
            Text(
                "Press Back again to leave",
                style = MaterialTheme.typography.labelLarge,
                color = TvInk,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 32.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(TvPanel)
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            )
        }

        if (listOpen) {
            TvChannelGuide(
                state = state,
                container = container,
                account = account,
                zapGroup = group,
                tunedId = tunedId,
                onTune = { channel, inGroup ->
                    listOpen = false
                    tune(channel, inGroup)
                    // Chosen again after it failed: that is a retry.
                    if (channel.streamId == tunedId && (error != null || released)) attempt++
                },
                onFavourite = model::toggleFavourite,
                onOpen = { listOpen = false; onOpen(it) },
                onClose = { listOpen = false },
                onRetry = model::retry,
            )
        }
    }
}

/**
 * The banner along the bottom on every change of channel, and on Right: the
 * number, the logo and the name, what is on with how far through it is, and
 * what is next — with the remote's keys spelt out, since a set-top box's
 * layout is not something anyone should have to guess.
 */
@Composable
private fun InfoBanner(
    guide: ProgrammeGuide,
    channel: LiveStream,
    number: Int,
    groupName: String,
    favourite: Boolean,
) {
    val clock = rememberClock()
    Column(
        Modifier
            .padding(horizontal = 48.dp, vertical = 28.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(TvPanel)
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (number > 0) {
                Text(
                    "$number",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TvAccent,
                    modifier = Modifier.widthIn(min = 64.dp),
                )
            }
            TvChannelLogo(channel, size = 52.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        channel.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = TvInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (favourite) {
                        Spacer(Modifier.width(8.dp))
                        Text("★", color = TvPink, style = MaterialTheme.typography.titleMedium)
                    }
                }
                Text(groupName, style = MaterialTheme.typography.bodySmall, color = TvInkSoft, maxLines = 1)
            }
            Text(clock, style = MaterialTheme.typography.headlineSmall, color = TvInk, fontWeight = FontWeight.Bold)
        }

        val now = guide.now
        if (now != null) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("NOW", style = MaterialTheme.typography.labelSmall, color = TvAccent, modifier = Modifier.width(52.dp))
                Text(
                    now.titleText,
                    style = MaterialTheme.typography.titleMedium,
                    color = TvInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(now.timeLabel(), style = MaterialTheme.typography.bodySmall, color = TvInkSoft)
            }
            guide.progress?.let { p ->
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { p },
                    modifier = Modifier.padding(start = 52.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = TvAccent,
                    trackColor = Color(0x33FFFFFF),
                )
            }
            guide.next?.let { next ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("NEXT", style = MaterialTheme.typography.labelSmall, color = TvInkSoft, modifier = Modifier.width(52.dp))
                    Text(
                        next.titleText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TvInkSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(next.timeLabel(), style = MaterialTheme.typography.bodySmall, color = TvInkSoft)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "▲ ▼  Channels     OK  Channels and guide     ◀  Previous channel     ▶  Info     0–9  Number",
            style = MaterialTheme.typography.labelSmall,
            color = TvInkSoft,
        )
    }
}

/** A message over the picture, centred, with room to read it from across a room. */
@Composable
private fun TvMessage(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .padding(48.dp)
            .widthIn(max = 560.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(TvPanel)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/** Per tune, read from the player's thread-free callbacks; not state, since nothing draws them. */
private class TuneFlags {
    var played = false
    var recorded = false
}

private val ChannelUp = setOf(Key.DirectionUp, Key.ChannelUp)
private val ChannelDown = setOf(Key.DirectionDown, Key.ChannelDown)
private val Select = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

private fun androidx.compose.ui.input.key.KeyEvent.digit(): Char? = when (key) {
    Key.Zero, Key.NumPad0 -> '0'
    Key.One, Key.NumPad1 -> '1'
    Key.Two, Key.NumPad2 -> '2'
    Key.Three, Key.NumPad3 -> '3'
    Key.Four, Key.NumPad4 -> '4'
    Key.Five, Key.NumPad5 -> '5'
    Key.Six, Key.NumPad6 -> '6'
    Key.Seven, Key.NumPad7 -> '7'
    Key.Eight, Key.NumPad8 -> '8'
    Key.Nine, Key.NumPad9 -> '9'
    else -> null
}

/**
 * The pause between a change of channel and asking for it. Long enough for the
 * panel to see the last connection close and for a second press to cancel;
 * short against the second or two a stream takes to start anyway.
 */
private const val ZAP_SETTLE_MS = 300L

/** How long typed digits wait for another before jumping, as on a set-top box. */
private const val NUMBER_WAIT_MS = 1_600L

private const val BANNER_MS = 5_000L
