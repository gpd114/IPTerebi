package com.ipterebi.app.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.util.Log
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
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
import com.ipterebi.app.TAG_PLAY
import com.ipterebi.app.data.AccountState
import com.ipterebi.core.StreamFormat
import com.ipterebi.core.VodStream
import com.ipterebi.core.XtreamAccount
import com.ipterebi.core.describeFilmHttpError
import com.ipterebi.core.describeStreamHttpError
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(container: AppContainer, playable: Playable, onBack: () -> Unit) {
    val state by container.credentials.state.collectAsStateWithLifecycle(AccountState.Loading)

    when (val current = state) {
        is AccountState.SignedIn ->
            PlayerContent(
                container = container,
                account = current.account,
                playable = playable,
                onBack = onBack,
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
) {
    val context = LocalContext.current
    val isFilm = playable is Playable.Film

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
        }
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

    val url = remember(playable, account) {
        when (playable) {
            is Playable.Channel -> container.xtream.liveStreamUrl(account, playable.id)
            is Playable.Film -> container.xtream.vodStreamUrl(
                account,
                VodStream(streamId = playable.id, containerExtension = playable.extension),
            )
        }
    }
    var error by remember(url) { mutableStateOf<String?>(null) }

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

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
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
                                is Playable.Film -> null
                            }
                        )
                        .build()
                )
                playWhenReady = true
                prepare()
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
                },
            )
        }

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG_PLAY, "${playable.logName()} ${playbackStateName(playbackState, playable)}")
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
            }

            /** First proof that video is actually arriving, and at what size. */
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG_PLAY, "${playable.logName()} video ${videoSize.width}x${videoSize.height}")
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                error = when (val cause = e.cause) {
                    is HttpDataSource.InvalidResponseCodeException -> when (playable) {
                        is Playable.Channel -> describeStreamHttpError(cause.responseCode)
                        is Playable.Film -> describeFilmHttpError(cause.responseCode)
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
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        // Only true once the app has actually been away. addObserver replays the
        // owner's current state into a new observer, so ON_START arrives here
        // once at registration before anything has happened, and there is
        // nothing to rejoin yet.
        var wasStopped = false

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // Stopped rather than paused, for both kinds, for two reasons.
                //
                // pause() keeps the player prepared, and prepare() is a no-op on
                // a player that is not idle — so pausing here and "re-preparing"
                // on return does nothing at all. stop() is what makes the later
                // prepare() real.
                //
                // It also lets go of the stream. A paused player keeps its
                // connection open, and on a line that allows one stream that is
                // the whole line held by an app in the background — a film no
                // less than a channel.
                //
                // A film is paused first so that it comes back paused. Nobody
                // who pressed home in the middle of a film wants it to start
                // again the instant they return; they want it where they left it.
                Lifecycle.Event.ON_STOP -> {
                    wasStopped = true
                    if (isFilm) player.pause()
                    player.stop()
                }

                Lifecycle.Event.ON_START -> if (wasStopped) {
                    wasStopped = false
                    when (playable) {
                        // Rejoined at the live edge rather than resumed. stop()
                        // keeps the playback position, and for HLS that position
                        // has usually slid out of the live window while the app
                        // was away.
                        is Playable.Channel -> {
                            player.seekToDefaultPosition()
                            player.prepare()
                            player.play()
                        }
                        // Reconnected at the position stop() kept, and left
                        // paused for the user to resume.
                        is Playable.Film -> player.prepare()
                    }
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val activity = remember(context) { context.findActivity() }
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
                    setShowFastForwardButton(isFilm)
                    setShowRewindButton(isFilm)
                    // Everything this screen draws over the picture follows the
                    // player's own controls in and out. A title and a guide sat
                    // permanently across the top of a film would be the first
                    // thing anyone asked to have removed.
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controlsVisible = visibility == View.VISIBLE
                        }
                    )
                }
            },
            update = { view -> view.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        if (controlsVisible) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = if (isFilm) "Back to films" else "Back to channels",
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

        error?.let { message ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xCC000000))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = message,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = {
                        error = null
                        // An error leaves the player idle, so this prepare() is
                        // real. A channel rejoins the broadcast rather than the
                        // point it failed at; a film carries on from where it
                        // stopped, which is the point of it having a position.
                        if (!isFilm) player.seekToDefaultPosition()
                        player.prepare()
                        player.play()
                    },
                    modifier = Modifier.padding(top = 16.dp),
                ) { Text("Try again") }
            }
        }
    }
}

private fun Playable.logName(): String = when (this) {
    is Playable.Channel -> "channel $id"
    is Playable.Film -> "film $id"
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
        is Playable.Film -> "ended"
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
