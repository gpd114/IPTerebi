package com.ipterebi.app.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.util.Rational
import android.widget.Toast
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Stop
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.ipterebi.app.AppContainer
import com.ipterebi.app.BuildConfig
import com.ipterebi.app.MainActivity
import com.ipterebi.app.TAG_PLAY
import com.ipterebi.app.data.AccountState
import com.ipterebi.app.playback.ActivePlayback
import com.ipterebi.app.ui.PrimaryButton
import com.ipterebi.app.ui.focusRing
import com.ipterebi.app.ui.theme.Night
import com.ipterebi.app.ui.theme.OverVideo
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import com.ipterebi.core.playerMimeType
import com.ipterebi.core.StreamReconnect
import com.ipterebi.core.StreamFormat
import com.ipterebi.core.VodStream
import com.ipterebi.core.XtreamAccount
import com.ipterebi.core.describeEpisodeHttpError
import com.ipterebi.core.describeFilmHttpError
import com.ipterebi.core.describeStreamHttpError
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(container: AppContainer, playable: Playable, onBack: () -> Unit) {
    val state by container.credentials.state.collectAsStateWithLifecycle(AccountState.Loading)

    // The channel being watched, which swiping changes. Held here rather than
    // by navigating to a new player: a new screen animates in while the old one
    // is still playing, so for a moment two players would hold two connections
    // — and a line that allows one refuses the channel being switched to.
    // Saveable, so rotation or a process death comes back to the channel that
    // was on rather than the one first opened.
    var channelId by rememberSaveable { mutableIntStateOf((playable as? Playable.Channel)?.id ?: 0) }

    // Through the list the channel was opened from — a category, favourites,
    // recents or search results — wrapping at either end as a television remote
    // does. Nothing happens when that list is not to hand, after a process death.
    val zapThroughList: (Int) -> Unit = { step ->
        val list = container.channels.items.value
        val here = list.indexOfFirst { it.streamId == channelId }
        if (here >= 0 && list.size > 1) {
            channelId = list[(here + step).mod(list.size)].streamId
        }
    }

    when (val current = state) {
        is AccountState.SignedIn ->
            PlayerContent(
                container = container,
                account = current.account,
                playable = if (playable is Playable.Channel) Playable.Channel(channelId) else playable,
                onBack = onBack,
                // Channels only: films and episodes have nothing to switch to.
                onZap = if (playable is Playable.Channel) zapThroughList else null,
            )

        else -> Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
    }
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
private fun PlayerContent(
    container: AppContainer,
    account: XtreamAccount,
    playable: Playable,
    onBack: () -> Unit,
    /** Moves to the next (+1) or previous (-1) channel. Null for films and episodes. */
    onZap: ((Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    // The view's touch listener is created once, so it reads the latest of this.
    val zap by rememberUpdatedState(onZap)
    val onDemand = playable.isOnDemand

    // Looked up from whichever list was on screen, because the id is all that
    // travels through navigation. Null after a process death, when that list is
    // gone — the overlay then shows no title rather than a wrong one.
    val channel = remember(playable) {
        (playable as? Playable.Channel)?.let { container.channels.find(it.id) }
    }
    val title = remember(playable) {
        when (playable) {
            is Playable.Channel -> channel?.name
            is Playable.Film -> container.films.find(playable.id)?.name
            is Playable.Episode -> container.episodes.find(playable.id)?.title
        }
    }
    // For the notification and the lock screen. Often blank and often a dead
    // link; either way the notification simply goes without.
    val artwork = remember(playable) {
        when (playable) {
            is Playable.Channel -> channel?.icon
            is Playable.Film -> container.films.find(playable.id)?.icon
            is Playable.Episode -> null
        }?.takeIf { it.isNotBlank() }
    }

    // A film has no guide, and asking the panel for one by a film's id would
    // spend a request on an answer that cannot exist.
    val guide = if (playable is Playable.Channel) {
        rememberProgrammeGuide(container, account, playable.id)
    } else {
        ProgrammeGuide()
    }

    val scope = rememberCoroutineScope()
    var recorded by remember(playable) { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var videoAspect by remember { mutableStateOf<Rational?>(null) }

    // A plain reference rather than state: nothing redraws when it is set, and
    // setting state from inside a view factory would be a write mid-composition.
    val playerView = remember { ViewRef<PlayerView>() }
    val retryFocus = remember { FocusRequester() }

    val url = remember(playable, account) {
        when (playable) {
            is Playable.Channel -> container.xtream.liveStreamUrl(account, playable.id)
            is Playable.Film -> container.xtream.vodStreamUrl(
                account,
                VodStream(streamId = playable.id, containerExtension = playable.extension),
            )
            // Qualified: core's Episode, not Playable.Episode, which shares the name.
            is Playable.Episode -> container.xtream.episodeStreamUrl(
                account,
                com.ipterebi.core.Episode(id = playable.id, containerExtension = playable.extension),
            )
        }
    }
    var error by remember(url) { mutableStateOf<String?>(null) }

    // Anything that drops after playing is reconnected; see StreamReconnect for
    // when and how often. The delay is read by the loading thread, hence atomic.
    val reconnect = remember(url) { StreamReconnect() }
    val reconnectDelay = remember(url) { AtomicLong(0) }
    var reconnecting by remember(url) { mutableStateOf(false) }

    // Let go of the line on purpose — handed to another player, or stopped from
    // the notification or Settings so another device can have it. Either way
    // this player stays stopped until asked: coming back here must not take the
    // line from whatever took it over, which may well still be playing.
    var released by remember(url) { mutableStateOf<Released?>(null) }

    val player = remember(url) {
        val httpFactory = DefaultHttpDataSource.Factory()
            // Same reasoning as the API calls: a default user agent gets the
            // stream refused by a fair number of panels, and the failure looks
            // like a dead channel rather than a rejected client.
            .setUserAgent(account.userAgent)
            // Panels redirect between http and https mid-request. ExoPlayer
            // refuses to follow a scheme change unless told to, and the symptom
            // is a channel failing instantly that plays fine in VLC.
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)

        val sources = DefaultMediaSourceFactory(DelayedOpenFactory(httpFactory, reconnectDelay)).apply {
            when (playable) {
                // HLS is left to ExoPlayer: its segments are separate requests
                // with nothing to range over, and it has never met a real line.
                is Playable.Channel ->
                    if (account.format == StreamFormat.TS) setLoadErrorHandlingPolicy(StreamRetryPolicy(live = true))
                is Playable.Film, is Playable.Episode ->
                    setLoadErrorHandlingPolicy(StreamRetryPolicy(live = false))
            }
        }

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(sources)
            // Takes audio focus: a call or a voice note pauses it, a
            // navigation prompt ducks it, and it no longer plays over music.
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            // Headphones coming out pause it, rather than carrying on through
            // the phone's speaker in someone's pocket on a bus.
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                // Holds the network up while the screen is off. Without it a
                // stream dies a few seconds after the display sleeps.
                setWakeMode(C.WAKE_MODE_NETWORK)
                setMediaItem(
                    MediaItem.Builder()
                        .setUri(url)
                        .setMimeType(
                            when (playable) {
                                // Stated rather than sniffed. Live paths do not
                                // always end in a usable extension, and guessing
                                // wrong picks the wrong extractor and fails with
                                // nothing useful in the log.
                                is Playable.Channel -> when (account.format) {
                                    StreamFormat.HLS -> MimeTypes.APPLICATION_M3U8
                                    StreamFormat.TS -> MimeTypes.VIDEO_MP2T
                                }
                                // Left to ExoPlayer. A film URL does end in its
                                // real extension, mp4 and mkv need different
                                // extractors, and sniffing the container is
                                // exactly what the progressive extractors do.
                                is Playable.Film, is Playable.Episode -> null
                            }
                        )
                        // What the notification and the lock screen show.
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(title)
                                // The line under the title. Left unset, the lock
                                // screen read it out as "Test News HD by null".
                                .setArtist(
                                    when (playable) {
                                        is Playable.Channel -> "Live TV"
                                        is Playable.Film -> "Film"
                                        is Playable.Episode -> "Series"
                                    }
                                )
                                .setArtworkUri(artwork?.toUri())
                                .build()
                        )
                        .build()
                )
                // Not prepared here: see the effect below.
                playWhenReady = true
            }
    }

    DisposableEffect(player) {
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG_PLAY,
                when (playable) {
                    is Playable.Channel ->
                        "open channel ${playable.id} as ${account.format.label}, ua=${account.userAgent}"
                    is Playable.Film ->
                        "open film ${playable.id} as .${playable.extension}, ua=${account.userAgent}"
                    is Playable.Episode ->
                        "open episode ${playable.id} as .${playable.extension}, ua=${account.userAgent}"
                },
            )
            // The real URL carries the credentials in its path, so only its
            // shape is logged. If the panel is serving from somewhere other
            // than /live or /movie, this is where that becomes visible.
            Log.d(
                TAG_PLAY,
                when (playable) {
                    is Playable.Channel ->
                        "  ${account.base}/live/***/***/${playable.id}.${account.format.extension}"
                    is Playable.Film ->
                        "  ${account.base}/movie/***/***/${playable.id}.${playable.extension}"
                    is Playable.Episode ->
                        "  ${account.base}/series/***/***/${playable.id}.${playable.extension}"
                },
            )
        }

        /**
         * Playback stopped by itself — the panel hung up, or the connection
         * failed. Reconnects, or gives up and shows [why].
         *
         * A channel rejoins at the live edge. A film or an episode carries on
         * from where it was: stop() keeps the position, so preparing again
         * picks up at the same second, and nobody sits through the last ten
         * minutes twice.
         *
         * Stop, seek and prepare all happen here, inside the listener, so the
         * stopped state never outlives this call: the media session only sees
         * where the player ends up, which is buffering. The back-off is waited
         * out while buffering, in [DelayedOpenFactory]. Both are for the screen
         * being off — see there.
         *
         * Not while paused: something paused that drops has nobody waiting on
         * it, and reconnecting would hold the line for them.
         */
        fun dropped(why: String) {
            val wait = if (player.playWhenReady) reconnect.onDropped(SystemClock.elapsedRealtime()) else null
            if (wait == null) {
                reconnecting = false
                error = why
                if (BuildConfig.DEBUG) Log.d(TAG_PLAY, "${playable.logName()} not reconnecting")
                return
            }
            if (BuildConfig.DEBUG) {
                val at = if (onDemand) " at ${player.currentPosition / 1000} s" else ""
                Log.d(TAG_PLAY, "${playable.logName()} dropped$at; reconnecting in $wait ms")
            }
            reconnecting = true
            reconnectDelay.set(wait)
            player.stop()
            if (!onDemand) player.seekToDefaultPosition()
            player.prepare()
        }

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG_PLAY, "${playable.logName()} ${playbackStateName(playbackState, playable)}")
                }

                // Live has no end. A channel that "ends" is a panel that hung up.
                if (playbackState == Player.STATE_ENDED && playable is Playable.Channel) {
                    dropped(
                        "The channel stopped, and reconnecting did not bring it back. " +
                            "It may be off air, or the panel may be down."
                    )
                }

                // Recorded when the channel actually plays, not when it is
                // tapped: a list of things that were opened and then refused by
                // the connection limit is not a list of things watched. Skipped
                // when the channel record is not to hand — after a process death
                // the list it came from is gone, and storing an entry with no
                // name would put an unreadable row in the recents shelf. Films
                // are not recorded: the recents shelf is a list of channels.
                if (playbackState == Player.STATE_READY && !recorded && channel != null) {
                    recorded = true
                    scope.launch { container.channelLists.recordWatched(account, channel) }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG_PLAY, "${playable.logName()} ${if (isPlaying) "playing" else "stopped"}")
                }
                if (isPlaying) {
                    reconnect.onPlaying(SystemClock.elapsedRealtime())
                    reconnecting = false
                }
            }

            /** First proof that video is actually arriving, and at what size. */
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG_PLAY, "${playable.logName()} video ${videoSize.width}x${videoSize.height}")
                }
                // So a picture-in-picture window is the shape of the picture.
                pictureInPictureAspect(videoSize.width, videoSize.height, videoSize.pixelWidthHeightRatio)
                    ?.let { videoAspect = it }
            }

            override fun onPlayerError(e: PlaybackException) {
                val message = when (val cause = e.cause) {
                    is HttpDataSource.InvalidResponseCodeException -> when (playable) {
                        is Playable.Channel -> describeStreamHttpError(cause.responseCode)
                        is Playable.Film -> describeFilmHttpError(cause.responseCode)
                        is Playable.Episode -> describeEpisodeHttpError(cause.responseCode)
                    }

                    is HttpDataSource.HttpDataSourceException ->
                        "Could not reach the stream. The panel may be down, or " +
                            "the connection dropped."

                    else -> e.localizedMessage ?: "Playback failed (${e.errorCodeName})."
                }
                // The URL carries the credentials in its path, so it is never
                // logged — only what was playing and how it failed.
                if (BuildConfig.DEBUG) {
                    Log.w(TAG_PLAY, "${playable.logName()} failed: ${e.errorCodeName}", e)
                }
                // Anything that was playing is reconnected, and only says why
                // if that fails; anything that never started says why at once.
                dropped(message)
            }
        }
        player.addListener(listener)
        // Prepared here rather than where the player is built, because of the
        // order Compose runs things in. A new player is built during composition,
        // but the old one is released in this effect's onDispose, which runs
        // afterwards. Preparing in the builder would open the new channel's
        // connection while the old one was still open; preparing here, after
        // the old effect has been disposed, closes one before opening the next.
        // On a one-connection line that is the difference between switching
        // channel and being refused.
        player.prepare()
        // Show the controls — and with them the name and the guide — whenever
        // the player is new, so a swipe to the next channel says where it went.
        playerView.value?.showController()
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    MediaSessionFor(player, live = playable is Playable.Channel)

    val lifecycleOwner = LocalLifecycleOwner.current
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    DisposableEffect(lifecycleOwner, player) {
        // Only true once the app has actually been away. addObserver replays the
        // owner's current state into a new observer, so ON_START arrives here
        // once at registration before anything has happened, and there is
        // nothing to rejoin yet.
        var wasStopped = false

        // Paused while away — from the notification, the lock screen, or by
        // headphones coming out — is the line held by nobody listening. It is
        // let go after a while, not at once: the earbud taken out to answer
        // someone goes back in, and play should still be there when it does.
        // Stopping ends the notification, so after that it is the app or nothing.
        val mainThread = Handler(Looper.getMainLooper())
        val letGo = Runnable { player.stop() }
        val pausedWhileAway = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                mainThread.removeCallbacks(letGo)
                val away = !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                if (!playWhenReady && away) mainThread.postDelayed(letGo, PAUSED_AWAY_GRACE_MS)
            }
        }
        player.addListener(pausedWhileAway)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // The screen went off with something playing: it plays on, as
                // sound, with the notification to stop it. That is the one way
                // of being stopped that means "keep going" — the phone into a
                // pocket. Picture-in-picture is not stopped at all, so it never
                // arrives here; closing its window does, with the screen on.
                Lifecycle.Event.ON_STOP -> {
                    wasStopped = true
                    if (power?.isInteractive == false && player.playWhenReady) return@LifecycleEventObserver

                    // Anything else — home, another app, the window closed — is
                    // someone who has finished, and it stops rather than pauses,
                    // for two reasons.
                    //
                    // pause() keeps the player prepared, and prepare() is a no-op
                    // on a player that is not idle — so pausing here and
                    // "re-preparing" on return does nothing at all. stop() is
                    // what makes the later prepare() real.
                    //
                    // It also lets go of the stream. A paused player keeps its
                    // connection open, and on a line that allows one stream that
                    // is the whole line held by an app in the background — a film
                    // no less than a channel.
                    //
                    // A film or episode is paused first so it comes back paused.
                    // Nobody who pressed home halfway through one wants it to
                    // start again the instant they return; they want it where
                    // they left it.
                    if (onDemand) player.pause()
                    player.stop()
                }

                Lifecycle.Event.ON_START -> if (wasStopped) {
                    wasStopped = false
                    mainThread.removeCallbacks(letGo)
                    // Back from the player this was handed to. It may still
                    // hold the line; "Play here" is the way back in.
                    if (released != null) return@LifecycleEventObserver
                    // Played on through the screen being off, and still is.
                    if (player.playWhenReady && player.playbackState != Player.STATE_IDLE) {
                        return@LifecycleEventObserver
                    }
                    when (playable) {
                        // Rejoined at the live edge rather than resumed. stop()
                        // keeps the playback position, and for HLS that position
                        // has usually slid out of the live window while the app
                        // was away. Stopped first, because a channel paused from
                        // the lock screen is still prepared, minutes behind.
                        is Playable.Channel -> {
                            // A fresh start: whatever went wrong while away —
                            // reconnecting given up on, say — is not current.
                            error = null
                            reconnect.reset()
                            player.stop()
                            player.seekToDefaultPosition()
                            player.prepare()
                            player.play()
                        }
                        // Reconnected at the position stop() kept, and left
                        // paused for the user to resume. One paused from the lock
                        // screen and not yet let go is still connected, and fine.
                        // A reconnect given up on while away is not current
                        // either, as for a channel.
                        is Playable.Film, is Playable.Episode ->
                            if (player.playbackState == Player.STATE_IDLE) {
                                error = null
                                reconnect.reset()
                                player.prepare()
                            }
                    }
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.removeListener(pausedWhileAway)
            mainThread.removeCallbacks(letGo)
        }
    }

    val activity = remember(context) { context.findActivity() }
    val inPictureInPicture = rememberPictureInPicture(activity, videoAspect, offered = released == null)

    // The notification's Stop and Settings' "Free the line" reach this player
    // through ActivePlayback: stopped as when handed to another app, so the line
    // is let go at once and coming back does not take it again.
    DisposableEffect(player) {
        val unregister = ActivePlayback.register {
            val wasPlaying = player.playbackState != Player.STATE_IDLE
            if (released == null) released = Released.ByYou
            error = null
            reconnecting = false
            (activity as? MainActivity)?.pictureInPicture = null
            if (onDemand) player.pause()
            player.stop()
            wasPlaying
        }
        onDispose { unregister() }
    }

    /**
     * Stops here and opens the stream in another player. Stopped first, and
     * before the other app is even started: on a line that allows one
     * connection, the other player asking while this one still streams is
     * refused — and a real line went on refusing for fifteen seconds after.
     */
    val openElsewhere: () -> Unit = openElsewhere@{
        val intent = anotherPlayerIntent(
            url = url,
            mimeType = playerMimeType(
                when (playable) {
                    is Playable.Channel -> account.format.extension
                    is Playable.Film -> playable.extension
                    is Playable.Episode -> playable.extension
                }
            ),
            title = title,
            userAgent = account.userAgent,
            positionMs = if (onDemand) player.currentPosition else 0L,
        )
        // Checked before anything stops: with no other player installed, the
        // stream would be lost for a chooser that says so.
        if (!context.canOpenInAnotherPlayer(intent)) {
            Toast.makeText(
                context,
                "No other video player is installed. VLC or MX Player from the Play Store will do.",
                Toast.LENGTH_LONG,
            ).show()
            return@openElsewhere
        }
        released = Released.ToAnotherApp
        error = null
        reconnecting = false
        // Withdrawn now rather than when the state above recomposes, which is
        // after the other app has already opened over this one.
        (activity as? MainActivity)?.pictureInPicture = null
        if (onDemand) player.pause()
        player.stop()
        context.openInAnotherPlayer(intent)
    }
    DisposableEffect(activity) {
        val previousOrientation = activity?.requestedOrientation
        val controller = activity?.window?.let { window ->
            WindowInsetsControllerCompat(window, window.decorView)
        }

        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            previousOrientation?.let { activity.requestedOrientation = it }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    useController = true
                    keepScreenOn = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    // One item at a time, so there is never a next or previous.
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    // A live stream has no duration and no seekable window, so
                    // skipping could only ever be inert. A film is the opposite:
                    // skipping is most of what its controls are for.
                    setShowFastForwardButton(onDemand)
                    setShowRewindButton(onDemand)
                    // The same goes for the clock and the progress bar. For a
                    // channel they read "00:16 · 00:00" over an empty bar — a
                    // position with nothing to be a position in — which looks
                    // like a fault. Media3 animates both but never sets their
                    // visibility, so hiding them here stays hidden.
                    if (!onDemand) {
                        findViewById<View>(androidx.media3.ui.R.id.exo_time)?.visibility = View.GONE
                        findViewById<View>(androidx.media3.ui.R.id.exo_progress)?.visibility = View.GONE
                    }
                    // Everything this screen draws over the picture follows the
                    // player's own controls in and out. A title and a guide sat
                    // permanently across the top of a film would be the first
                    // thing anyone asked to have removed.
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controlsVisible = visibility == View.VISIBLE
                            // Media3 moves focus to play/pause when its controls
                            // appear. When they time out, that button goes with
                            // them and focus falls back to Compose, where nothing
                            // takes key presses — so every press after the first
                            // auto-hide went nowhere, and the remote was dead
                            // until the screen was left. Handing focus back to
                            // the player means the next press shows the controls
                            // again, as it did the first time.
                            if (visibility != View.VISIBLE) playerView.value?.requestFocus()
                        }
                    )
                    // The remote belongs to the player's own controls: OK shows
                    // them and pauses, left and right skip, and the media keys
                    // work. They only receive keys while the player view holds
                    // focus, so it is given it.
                    isFocusable = true
                    playerView.value = this

                    // Swipe up for the next channel, down for the previous. On
                    // the view itself rather than a Compose layer over it: a
                    // Compose layer would take every touch, and tapping to show
                    // the controls would stop working.
                    //
                    // Judged by how far the finger travelled, not by how fast
                    // it was going when it lifted. Android's fling detection
                    // needs speed at the moment of lifting, and people often
                    // swipe, slow, and then let go — measured on an emulator,
                    // where most swipes were never reported as flings at all.
                    val swipeDistance = 60 * resources.displayMetrics.density
                    var downX = 0f
                    var downY = 0f
                    var tracking = false
                    setOnTouchListener { view, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                downX = event.x
                                downY = event.y
                                tracking = true
                                // The rest of a gesture only comes to a view that
                                // claims its first touch, and the player stops
                                // claiming touches when its controls are off —
                                // which they are while an error is up. Without
                                // this, a refused channel could not be swiped
                                // past: the one place a swipe is most wanted.
                                // So the touch is claimed here, and still handed
                                // to the player so that a tap stays a tap. Not
                                // for films, which have nothing to swipe to:
                                // returning false leaves them to the player as
                                // before, and handing the touch over as well
                                // would give it the same touch twice.
                                if (zap != null) {
                                    view.onTouchEvent(event)
                                    true
                                } else {
                                    false
                                }
                            }
                            // A second finger is a pinch, never a swipe.
                            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> {
                                tracking = false
                                false
                            }
                            MotionEvent.ACTION_UP -> {
                                val step = zap
                                val dy = event.y - downY
                                val dx = event.x - downX
                                // Clearly vertical and clearly deliberate: a
                                // sideways drag, or a nudge, changes nothing.
                                val swiped = tracking && step != null &&
                                    abs(dy) >= swipeDistance && abs(dy) >= 2 * abs(dx)
                                tracking = false
                                if (swiped) {
                                    // The player saw the touch go down and would
                                    // take this lift as a tap, hiding the
                                    // controls about to be shown. A cancel ends
                                    // its gesture cleanly instead.
                                    val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                                    view.onTouchEvent(cancel)
                                    cancel.recycle()
                                    step?.invoke(if (dy < 0) 1 else -1)
                                }
                                swiped
                            }
                            else -> false
                        }
                    }
                    // Once, when created: asking on every update would pull focus back
                    // off the Try again button the moment an error drew it.
                    post { requestFocus() }
                }
            },
            update = { view -> view.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        // Also while an error is up: the controls are switched off then, and
        // the way out and the name of what failed should not go with them. Never
        // in picture-in-picture, where the window is the size of a stamp.
        if ((controlsVisible || error != null || released != null) && !inPictureInPicture) {
            // Opposite the back button, and like it a tap target only: see
            // there for why nothing over the video may take a remote's focus.
            if (released == null) {
                IconButton(
                    onClick = openElsewhere,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .focusProperties { canFocus = false },
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open in another player",
                        tint = Color.White,
                    )
                }
            }

            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    // A tap target, never a focus target. Left focusable, it is
                    // the first thing a remote lands on — so the first press of
                    // OK, which everyone uses to pause, left the film instead.
                    // The remote has its own Back key for leaving.
                    .focusProperties { canFocus = false },
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = when (playable) {
                        is Playable.Channel -> "Back to channels"
                        is Playable.Film -> "Back to films"
                        is Playable.Episode -> "Back to episodes"
                    },
                    tint = Color.White,
                )
            }

            ChannelInfoOverlay(
                channelName = title,
                guide = guide,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
            )

            ProgrammeProgress(
                guide = guide,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        // While an error is up, the player's own controls are switched off.
        // Media3 raises them when playback fails and they take focus, so on a
        // remote OK went to their settings gear rather than to Try again —
        // measured on an emulator, not supposed. They have nothing to offer
        // here anyway: there is nothing to play or seek.
        //
        // Keyed on whether an error is showing, not switched on and off by
        // hand. It used to be: off when the error appeared, on again only from
        // Try again — so swiping away from a refused channel left them off for
        // good, and a tap did nothing on every channel after.
        //
        // Off in picture-in-picture too, where the system draws its own.
        val showingError = error != null
        val controlsWanted = !showingError && released == null && !inPictureInPicture
        LaunchedEffect(controlsWanted) {
            playerView.value?.apply {
                useController = controlsWanted
                isFocusable = controlsWanted
                if (controlsWanted) requestFocus()
            }
        }

        // Under the player's own buffering spinner, which is already turning:
        // the wait before a reconnect reads as buffering, deliberately.
        if (reconnecting && error == null && !inPictureInPicture) {
            Text(
                text = "Reconnecting…",
                color = OverVideo.ink,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 96.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(OverVideo.panel)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }

        released?.takeIf { !inPictureInPicture }?.let { why ->
            OverVideoPanel(Modifier.align(Alignment.Center)) {
                Icon(
                    when (why) {
                        Released.ToAnotherApp -> Icons.AutoMirrored.Filled.OpenInNew
                        Released.ByYou -> Icons.Filled.Stop
                    },
                    contentDescription = null,
                    tint = OverVideo.accent,
                )
                Text(
                    text = when (why) {
                        Released.ToAnotherApp -> "Playing in another app"
                        Released.ByYou -> "Stopped"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = OverVideo.ink,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = when (why) {
                        Released.ToAnotherApp -> "Stopped here, so your line is free for it."
                        Released.ByYou -> "Your line is free for another device."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = OverVideo.inkSoft,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
                PrimaryButton(
                    fill = OverVideo.button,
                    onClick = {
                        released = null
                        reconnect.reset()
                        reconnectDelay.set(0)
                        // As Try again: a channel rejoins the broadcast, a film
                        // carries on from where it was handed over.
                        if (!onDemand) player.seekToDefaultPosition()
                        player.prepare()
                        player.play()
                    },
                    modifier = Modifier
                        .padding(top = 18.dp)
                        .focusRequester(retryFocus),
                ) { Text("Play here", style = MaterialTheme.typography.labelLarge) }
            }
            LaunchedEffect(Unit) { retryFocus.requestFocus() }
        }

        error?.takeIf { !inPictureInPicture }?.let { message ->
            OverVideoPanel(Modifier.align(Alignment.Center)) {
                Text(
                    text = message,
                    color = OverVideo.ink,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                PrimaryButton(
                    fill = OverVideo.button,
                    onClick = {
                        error = null
                        reconnect.reset()
                        reconnectDelay.set(0)
                        // An error leaves the player idle, so this prepare() is
                        // real. A channel rejoins the broadcast rather than the
                        // point it failed at; a film carries on from where it
                        // stopped, which is the point of it having a position.
                        // Stopped first for a channel, which may have given up
                        // on reconnecting from ended rather than from idle —
                        // and prepare() on an ended player does nothing.
                        if (!onDemand) {
                            player.stop()
                            player.seekToDefaultPosition()
                        }
                        player.prepare()
                        player.play()
                        // Clearing the error brings the controls, and with
                        // them the remote, back — see showingError above.
                    },
                    modifier = Modifier
                        .padding(top = 18.dp)
                        .focusRequester(retryFocus),
                ) { Text("Try again", style = MaterialTheme.typography.labelLarge) }
            }
            // Focus to the button when the error appears, so on a remote OK
            // means "try again".
            LaunchedEffect(message) {
                retryFocus.requestFocus()
            }
        }
    }
}

/**
 * How long something paused while the app is away keeps the line before it is
 * let go. Long enough for an earbud out and back in; short of the minute after
 * which Android starts winding down a backgrounded app's services.
 */
private const val PAUSED_AWAY_GRACE_MS = 30_000L

/**
 * A message over the video — an error, "playing in another app" — as a panel
 * in the page's colours, centred and no wider than it needs to be read.
 */
@Composable
private fun OverVideoPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .padding(24.dp)
            .widthIn(max = 420.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(OverVideo.panel)
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

private fun Playable.logName(): String = when (this) {
    is Playable.Channel -> "channel $id"
    is Playable.Film -> "film $id"
    is Playable.Episode -> "episode $id"
}

/**
 * `ended` means opposite things for the two kinds. A film ends; that is the
 * credits. A live stream never should — when it does, the panel closed the
 * connection, usually the line's connection limit being enforced a few seconds
 * late rather than anything about the channel.
 */
private fun playbackStateName(state: Int, playable: Playable): String = when (state) {
    Player.STATE_IDLE -> "idle"
    Player.STATE_BUFFERING -> "buffering"
    Player.STATE_READY -> "ready"
    Player.STATE_ENDED -> when (playable) {
        is Playable.Channel -> "ended (source closed the connection)"
        is Playable.Film, is Playable.Episode -> "ended"
    }
    else -> "state $state"
}

private fun Context.findActivity(): Activity? {
    var context: Context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

/** Holds a view created inside an AndroidView factory, for calls made outside it. */
private class ViewRef<T : View>(var value: T? = null)

/** Why a player let go of the line on purpose; see `released` in PlayerContent. */
private enum class Released {
    /** Handed to another player on the phone. */
    ToAnotherApp,
    /** Stopped from the notification or Settings, to free the line for another device. */
    ByYou,
}
