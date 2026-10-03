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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.Tune
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
import androidx.media3.common.Tracks
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
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
import com.ipterebi.app.ui.focusFill
import com.ipterebi.app.ui.theme.Corners
import com.ipterebi.app.ui.theme.Night
import com.ipterebi.app.ui.theme.OverVideo
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import com.ipterebi.core.playerMimeType
import com.ipterebi.core.StreamReconnect
import com.ipterebi.core.StreamFormat
import com.ipterebi.core.catchUpStartLabel
import com.ipterebi.core.lineKey
import com.ipterebi.core.WatchKind
import com.ipterebi.core.WatchedItem
import com.ipterebi.core.displayTitle
import com.ipterebi.core.VodStream
import com.ipterebi.core.XtreamAccount
import com.ipterebi.core.describeEpisodeHttpError
import com.ipterebi.core.describeFilmHttpError
import com.ipterebi.app.ui.SecondaryButton
import com.ipterebi.core.LiveStream
import com.ipterebi.core.hiddenIds
import com.ipterebi.core.streams
import com.ipterebi.core.withoutHidden
import kotlinx.coroutines.flow.first
import com.ipterebi.core.describeStreamHttpError
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlinx.coroutines.delay
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
                onTune = if (playable is Playable.Channel) {
                    { id -> channelId = id }
                } else null,
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
    /** Switches to a named channel, for the other feeds carrying a programme. */
    onTune: ((Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    // The view's touch listener is created once, so it reads the latest of this.
    val zap by rememberUpdatedState(onZap)
    val onDemand = playable.isOnDemand

    // Whether *this* stream can be skipped in, which is not the same as what
    // kind of thing it is. A film always can, a live channel never can, and a
    // catch-up depends entirely on the panel: the owner's serves a recording
    // as a finite body — asked for 45 minutes, answered with a duration of 40
    // and `seekable=true`, measured on the box — while another fork may stream
    // it endlessly the way it streams live television. So the controls follow
    // the stream once it is ready, rather than its type. The type is only the
    // opening guess, for the moment before anything has loaded.
    var seekable by remember(playable) { mutableStateOf(onDemand) }

    // Looked up from whichever list was on screen, because the id is all that
    // travels through navigation. Null after a process death, when that list is
    // gone — the overlay then shows no title rather than a wrong one.
    val channel = remember(playable) {
        // A catch-up is a recording of a channel, and wears that channel's
        // name and logo throughout.
        playable.channelOrNull?.let { container.channels.find(it) }
    }
    val title = remember(playable) {
        when (playable) {
            is Playable.Channel, is Playable.CatchUp -> channel?.name
            is Playable.Film -> container.films.find(playable.id)?.name
            is Playable.Episode -> container.episodes.find(playable.id)?.title
            // Its own name, from the row that opened it: no list to look it
            // up in, because this one never came from the panel.
            is Playable.Recorded -> playable.name
        }
    }
    // For the notification and the lock screen. Often blank and often a dead
    // link; either way the notification simply goes without.
    val artwork = remember(playable) {
        when (playable) {
            is Playable.Channel, is Playable.CatchUp -> channel?.icon
            is Playable.Film -> container.films.find(playable.id)?.icon
            is Playable.Episode -> null
            is Playable.Recorded -> null
        }?.takeIf { it.isNotBlank() }
    }

    // A film has no guide, and asking the panel for one by a film's id would
    // spend a request on an answer that cannot exist.
    val guide = if (playable is Playable.Channel) {
        rememberProgrammeGuide(container, account, playable.id, channel?.epgChannelId)
    } else {
        ProgrammeGuide()
    }

    val scope = rememberCoroutineScope()
    var recorded by remember(playable) { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var videoAspect by remember { mutableStateOf<Rational?>(null) }
    // What the stream turned out to carry, for the audio-and-subtitles panel.
    var tracks by remember(playable) { mutableStateOf(Tracks.EMPTY) }
    var tracksOpen by remember(playable) { mutableStateOf(false) }
    // A plain reference rather than state: nothing redraws when it is set, and
    // setting state from inside a view factory would be a write mid-composition.
    val playerView = remember { ViewRef<PlayerView>() }
    val retryFocus = remember { FocusRequester() }

    val url = remember(playable, account) {
        when (playable) {
            // Addressed by when it was on, not by an id of its own.
            is Playable.CatchUp -> container.xtream.catchUpUrl(
                account = account,
                streamId = playable.channelId,
                startSeconds = playable.startSeconds,
                minutes = playable.minutes,
                shiftSeconds = container.guide.clock.shiftFor(account.lineKey),
            )
            is Playable.Channel -> container.xtream.liveStreamUrl(account, playable.id)
            is Playable.Film -> container.xtream.vodStreamUrl(
                account,
                VodStream(streamId = playable.id, containerExtension = playable.extension),
            )
            // A file on this box. No account, no panel, no credentials in a
            // path — the one playable that is nobody else's business.
            is Playable.Recorded -> android.net.Uri.fromFile(java.io.File(playable.path)).toString()
            // Qualified: core's Episode, not Playable.Episode, which shares the name.
            is Playable.Episode -> container.xtream.episodeStreamUrl(
                account,
                com.ipterebi.core.Episode(id = playable.id, containerExtension = playable.extension),
            )
        }
    }
    var error by remember(url) { mutableStateOf<String?>(null) }

    // The other feeds carrying what this channel has on, for when this one
    // dies. Entirely a local query — the guide is on the device and the whole
    // line is cached — which is the point: the panel refusing things is
    // usually why there is an error card at all, so working around it must
    // not need the panel's help.
    //
    // Looked up only once an error is up. On a healthy stream it would be a
    // guide scan per channel change that nothing ever reads.
    var otherFeeds by remember(url) { mutableStateOf<List<LiveStream>>(emptyList()) }
    var otherFeedsTitle by remember(url) { mutableStateOf("") }
    LaunchedEffect(error, playable) {
        if (error == null || onTune == null) return@LaunchedEffect
        val id = (playable as? Playable.Channel)?.id ?: return@LaunchedEffect
        val found = runCatching {
            val line = container.lineChannels.all(account)
            val visible = line.withoutHidden(container.channelLists.hidden(account).first().hiddenIds())
            val here = visible.firstOrNull { it.streamId == id } ?: return@runCatching null
            val showing = container.guide.elsewhere(
                account,
                here.epgChannelId,
                System.currentTimeMillis() / 1000,
            ) ?: return@runCatching null
            // Everything carrying it except the stream that just failed —
            // which may well leave another stream on this same guide channel,
            // the provider's own backup feed, and that is often the best one.
            showing to showing.streams(visible).filterNot { it.streamId == id }
        }.getOrNull()
        otherFeedsTitle = found?.first?.title.orEmpty()
        otherFeeds = found?.second?.take(MAX_OTHER_FEEDS).orEmpty()
    }

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

        // A recording is read off the disk, and the HTTP data source cannot
        // do that: handed a file:// uri it casts the connection to
        // HttpURLConnection and throws, which reads as "Source error" and
        // says nothing about the real reason. DefaultDataSource handles
        // file, content and http, so it is the one to give a local file.
        val upstream = if (playable is Playable.Recorded) {
            DefaultDataSource.Factory(context)
        } else {
            DelayedOpenFactory(httpFactory, reconnectDelay)
        }
        val sources = DefaultMediaSourceFactory(upstream).apply {
            when (playable) {
                // HLS is left to ExoPlayer: its segments are separate requests
                // with nothing to range over, and it has never met a real line.
                is Playable.Channel, is Playable.CatchUp ->
                    if (account.format == StreamFormat.TS) setLoadErrorHandlingPolicy(StreamRetryPolicy(live = true))
                // A recording is a file like any other, and the one on local
                // storage that cannot fail for a reason the panel knows about.
                is Playable.Film, is Playable.Episode, is Playable.Recorded ->
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
                // The audio language chosen last time, and whether subtitles
                // were wanted — set before anything is prepared, so the first
                // frame already has the right track rather than switching a
                // second in. See TrackChoice for why it is a language and not
                // a track number.
                trackSelectionParameters =
                    TrackChoice.applyTo(context, trackSelectionParameters.buildUpon()).build()
                setMediaItem(
                    MediaItem.Builder()
                        .setUri(url)
                        .setMimeType(
                            when (playable) {
                                // Stated rather than sniffed. Live paths do not
                                // always end in a usable extension, and guessing
                                // wrong picks the wrong extractor and fails with
                                // nothing useful in the log.
                                is Playable.Channel, is Playable.CatchUp -> when (account.format) {
                                    StreamFormat.HLS -> MimeTypes.APPLICATION_M3U8
                                    StreamFormat.TS -> MimeTypes.VIDEO_MP2T
                                }
                                // Left to ExoPlayer. A film URL does end in its
                                // real extension, mp4 and mkv need different
                                // extractors, and sniffing the container is
                                // exactly what the progressive extractors do.
                                is Playable.Film, is Playable.Episode, is Playable.Recorded -> null
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
                                        is Playable.CatchUp -> "Catch-up"
                                        is Playable.Film -> "Film"
                                        is Playable.Episode -> "Series"
                                        is Playable.Recorded -> "Recording"
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


    // Where you got to, for the home screen's Continue watching rows. Only
    // films and episodes: a channel is rejoined at the live edge, so a
    // position in one means nothing.
    //
    // Launched on the container's scope rather than this screen's, because the
    // most important moment to write is the one where this screen is going
    // away — a scope tied to the composition is cancelled exactly then.
    val saveProgress: () -> Unit = save@{
        if (!onDemand) return@save
        val position = player.currentPosition
        if (position <= 0) return@save
        val length = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        val film = (playable as? Playable.Film)?.let { container.films.find(it.id) }
        val episode = (playable as? Playable.Episode)?.let { container.episodes.find(it.id) }
        val item = when (playable) {
            is Playable.Film -> WatchedItem(
                kind = WatchKind.FILM,
                id = playable.id.toString(),
                name = film?.name.orEmpty(),
                poster = film?.icon.orEmpty(),
                extension = playable.extension,
                positionMs = position,
                durationMs = length,
                watchedAt = System.currentTimeMillis(),
            )
            is Playable.Episode -> WatchedItem(
                kind = WatchKind.EPISODE,
                id = playable.id,
                name = episode?.seriesName.orEmpty(),
                detail = listOfNotNull(
                    episode?.entry?.code?.takeIf { it.isNotBlank() },
                    episode?.entry?.displayTitle(episode.seriesName)?.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                poster = episode?.entry?.details?.image.orEmpty(),
                extension = playable.extension,
                positionMs = position,
                durationMs = length,
                watchedAt = System.currentTimeMillis(),
            )
            is Playable.Channel, is Playable.CatchUp -> return@save
            // Not kept: Home's part-watched row is built from the panel's
            // ids, and a file on this box has none. The recording is in its
            // own list either way, which is where someone would look for it.
            is Playable.Recorded -> return@save
        }
        if (BuildConfig.DEBUG) Log.d(TAG_PLAY, "${playable.logName()} noting ${position / 1000} s of ${length / 1000} s")
        container.scope.launch { container.watched.record(account, item) }
    }

    // Carry on where this was left, and keep a note of where it gets to.
    //
    // The seek happens before the effect below prepares the player, which is
    // what stops a resumed film showing a second of its opening before it
    // jumps: ExoPlayer holds a seek made while it is idle and starts there.
    //
    // The position is written as it goes, not only on the way out, because a
    // process killed from the task switcher never reaches onDispose — and
    // losing the last half hour of a film to that is exactly what this is
    // meant to prevent.
    LaunchedEffect(player, playable) {
        val kind = when (playable) {
            is Playable.Film -> WatchKind.FILM
            is Playable.Episode -> WatchKind.EPISODE
            is Playable.Channel, is Playable.CatchUp, is Playable.Recorded ->
                return@LaunchedEffect
        }
        val id = when (playable) {
            is Playable.Film -> playable.id.toString()
            is Playable.Episode -> playable.id
            is Playable.Channel, is Playable.CatchUp, is Playable.Recorded ->
                return@LaunchedEffect
        }
        container.watched.resumeAt(account, kind, id).takeIf { it > 0 }?.let { at ->
            if (BuildConfig.DEBUG) Log.d(TAG_PLAY, "${playable.logName()} resuming at ${at / 1000} s")
            player.seekTo(at)
        }
        while (true) {
            delay(SAVE_PROGRESS_EVERY_MS)
            if (player.isPlaying) saveProgress()
        }
    }

    // The sleep timer, watched here because this is the only screen that has
    // anything to stop. Stopped rather than paused: a paused player keeps the
    // line, and someone who has fallen asleep is not about to free it.
    LaunchedEffect(player, playable) {
        while (true) {
            delay(1_000)
            if (!SleepTimer.hasFired(SystemClock.elapsedRealtime())) continue
            SleepTimer.cancel()
            // The panel goes with it: the card that replaces the picture is
            // the only thing worth reading now, and a panel still open over
            // it is two answers to the same question.
            tracksOpen = false
            saveProgress()
            if (BuildConfig.DEBUG) Log.d(TAG_PLAY, "${playable.logName()} stopped by the sleep timer")
            player.stop()
            error = null
            reconnecting = false
            released = Released.BySleepTimer
        }
    }

    DisposableEffect(player) {
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG_PLAY,
                when (playable) {
                    is Playable.Channel ->
                        "open channel ${playable.id} as ${account.format.label}, ua=${account.userAgent}"
                    is Playable.CatchUp ->
                        "open catch-up on channel ${playable.channelId}, ${playable.minutes} min, " +
                            "as ${account.format.label}, ua=${account.userAgent}"
                    is Playable.Film ->
                        "open film ${playable.id} as .${playable.extension}, ua=${account.userAgent}"
                    is Playable.Episode ->
                        "open episode ${playable.id} as .${playable.extension}, ua=${account.userAgent}"
                    is Playable.Recorded -> "open recording from this box"
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
                    is Playable.CatchUp ->
                        // The time is in the path, and it is the thing worth
                        // seeing: a catch-up that plays the wrong hour is a
                        // wrong clock, and this is where that shows.
                        "  ${account.base}/timeshift/***/***/${playable.minutes}/" +
                            catchUpStartLabel(
                                playable.startSeconds,
                                container.guide.clock.shiftFor(account.lineKey),
                            ) + "/${playable.channelId}.${account.format.extension}"
                    is Playable.Film ->
                        "  ${account.base}/movie/***/***/${playable.id}.${playable.extension}"
                    is Playable.Episode ->
                        "  ${account.base}/series/***/***/${playable.id}.${playable.extension}"
                    // No credentials to hide: this one never left the box.
                    is Playable.Recorded -> "  " + playable.path
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
                val at = if (seekable) " at " + player.currentPosition / 1000 + " s" else ""
                Log.d(TAG_PLAY, "${playable.logName()} dropped$at; reconnecting in $wait ms")
            }
            reconnecting = true
            reconnectDelay.set(wait)
            player.stop()
            // Back where it was on anything with a position: a recording is
            // a file on this panel, and rejoining a file at its "live edge"
            // means starting the programme again. A channel has no position
            // worth keeping and is rejoined at the edge, as before.
            if (!seekable) player.seekToDefaultPosition()
            player.prepare()
        }

        val listener = object : Player.Listener {
            // A stream announces its tracks a moment after it opens, and
            // again after every reconnect, so this is read as it changes
            // rather than once.
            override fun onTracksChanged(available: Tracks) {
                tracks = available
            }

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
                // What the panel actually sent, now that there is something
                // to ask. A recording that came back with a length can be
                // skipped in; one streamed like live television cannot.
                if (playbackState == Player.STATE_READY) {
                    seekable = player.isCurrentMediaItemSeekable && player.duration > 0
                    if (BuildConfig.DEBUG && playable is Playable.CatchUp) {
                        Log.d(
                            TAG_PLAY,
                            "  recording: " + player.duration / 1000 + " s, " +
                                (if (seekable) "seekable" else "not seekable"),
                        )
                    }
                }

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
                        // A catch-up is refused the same way and for the same
                        // reasons as a channel — the connection limit above all.
                        is Playable.Channel, is Playable.CatchUp -> describeStreamHttpError(cause.responseCode)
                        is Playable.Film -> describeFilmHttpError(cause.responseCode)
                        is Playable.Episode -> describeEpisodeHttpError(cause.responseCode)
                        // A file on this box answers no HTTP code at all, so
                        // this is unreachable rather than unlikely — but the
                        // compiler wants it and a sentence is better than a
                        // crash if it ever is reached.
                        is Playable.Recorded -> "That recording could not be read."
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
            // Leaving by the back button, or swiping to another channel: the
            // last chance to write where this one got to.
            saveProgress()
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
                    // Before anything stops: a stopped player reports position 0,
                    // and this is the usual way a film is left.
                    saveProgress()
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
                    // Which of these it gets is the stream's shape, not its
                    // kind. A recording this panel serves as a file has a
                    // position worth keeping; one another panel streams like
                    // live television does not, and neither does a channel.
                    if (!seekable) {
                        // Rejoined at the live edge rather than resumed. stop()
                        // keeps the playback position, and for HLS that position
                        // has usually slid out of the live window while the app
                        // was away. Stopped first, because a channel paused from
                        // the lock screen is still prepared, minutes behind.
                        //
                        // A fresh start: whatever went wrong while away —
                        // reconnecting given up on, say — is not current.
                        error = null
                        reconnect.reset()
                        player.stop()
                        player.seekToDefaultPosition()
                        player.prepare()
                        player.play()
                    } else {
                        // Reconnected at the position stop() kept, and left
                        // paused for the user to resume. One paused from the lock
                        // screen and not yet let go is still connected, and fine.
                        // A reconnect given up on while away is not current
                        // either, as for a channel.
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
                    is Playable.Channel, is Playable.CatchUp -> account.format.extension
                    is Playable.Film -> playable.extension
                    is Playable.Episode -> playable.extension
                    // Written as transport stream, whatever the channel was.
                    is Playable.Recorded -> "ts"
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
                    // The shape chosen last time, before the first frame, so a
                    // 4:3 channel does not show its bars for a moment first.
                    resizeMode = Picture.fit.resizeMode
                    // One item at a time, so there is never a next or previous.
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    // A live stream has no duration and no seekable window, so
                    // skipping could only ever be inert. A film is the opposite:
                    // skipping is most of what its controls are for.
                    showSeekControls(seekable)
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
            update = { view ->
                view.player = player
                // Read here rather than only in the factory: choosing a shape
                // in the panel has to change the picture while it plays, which
                // is the only way to judge it.
                view.resizeMode = Picture.fit.resizeMode
                // And here too: a recording only says whether it can be sought
                // in once it is ready, which is after the view was built.
                view.showSeekControls(seekable)
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Also while an error is up: the controls are switched off then, and
        // the way out and the name of what failed should not go with them. Never
        // in picture-in-picture, where the window is the size of a stamp.
        if ((controlsVisible || error != null || released != null) && !inPictureInPicture) {
            // Opposite the back button, and like it a tap target only: see
            // there for why nothing over the video may take a remote's focus.
            if (released == null) {
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Always here now, where the CC button used to appear only
                    // when a stream carried a choice: it holds the picture
                    // shape and the sleep timer too, and both of those are
                    // wanted on a stream that has one audio track and no
                    // subtitles — often exactly that stream, when it is the
                    // 4:3 channel with bars down the sides.
                    IconButton(
                        onClick = { tracksOpen = !tracksOpen },
                        modifier = Modifier.focusProperties { canFocus = false },
                    ) {
                        Icon(
                            Icons.Filled.Tune,
                            contentDescription = "Picture, sleep timer, audio and subtitles",
                            tint = if (tracksOpen || SleepTimer.running) OverVideo.accent else Color.White,
                        )
                    }

                    IconButton(
                        onClick = openElsewhere,
                        modifier = Modifier.focusProperties { canFocus = false },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open in another player",
                            tint = Color.White,
                        )
                    }
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
                        is Playable.Channel, is Playable.CatchUp -> "Back to channels"
                        is Playable.Film -> "Back to films"
                        is Playable.Episode -> "Back to episodes"
                        is Playable.Recorded -> "Back to recordings"
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

        // Outside the block above on purpose: the player's own controls time
        // out after a few seconds, and a panel someone is reading should not
        // go with them. It closes on a choice, or on a tap beside it.
        if (tracksOpen && !inPictureInPicture) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { tracksOpen = false },
            )
            PlaybackPanel(
                player = player,
                context = context,
                live = playable is Playable.Channel,
                onDismiss = { tracksOpen = false },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 56.dp, end = 12.dp),
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
                    .clip(Corners.card)
                    .background(OverVideo.panel)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }

        released?.takeIf { !inPictureInPicture }?.let { why ->
            OverVideoPanel(Modifier.align(Alignment.Center)) {
                Icon(
                    when (why) {
                        Released.ToAnotherApp -> Icons.AutoMirrored.Filled.OpenInNew
                        Released.ByYou, Released.BySleepTimer -> Icons.Filled.Stop
                    },
                    contentDescription = null,
                    tint = OverVideo.accent,
                )
                Text(
                    text = when (why) {
                        Released.ToAnotherApp -> "Playing in another app"
                        Released.ByYou -> "Stopped"
                        Released.BySleepTimer -> "Sleep timer"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = OverVideo.ink,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = when (why) {
                        Released.ToAnotherApp -> "Stopped here, so your line is free for it."
                        Released.ByYou -> "Your line is free for another device."
                        Released.BySleepTimer -> "Stopped, and your line is free."
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
                        if (!seekable) player.seekToDefaultPosition()
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

                // The whole reason this feature exists: one connection means
                // the way out of a dead feed is another channel showing the
                // same thing, and on a line of twenty thousand channels
                // nobody is going to find it by browsing.
                if (otherFeeds.isNotEmpty() && onTune != null) {
                    Text(
                        text = otherFeedsTitle.ifBlank { "Also showing on" }
                            .let { "$it — also on ${otherFeeds.size}" },
                        color = OverVideo.inkSoft,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
                    )
                    otherFeeds.forEach { feed ->
                        SecondaryButton(
                            text = feed.name,
                            // Switching happens here, by changing which
                            // channel this player is on — never by navigating,
                            // which would animate a second player in over the
                            // top and hold two connections against a line that
                            // allows one.
                            onClick = {
                                error = null
                                reconnect.reset()
                                reconnectDelay.set(0)
                                onTune(feed.streamId)
                            },
                            modifier = Modifier.padding(top = 6.dp),
                            colour = OverVideo.accent,
                        )
                    }
                }
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
/**
 * How many other feeds the error card offers.
 *
 * A real line can carry one match on dozens of channels — 127 US affiliates
 * shared one slot on the line this was measured against — and a card listing
 * them all would be a scroll over the video rather than a way out of a dead
 * stream. The first few, in the provider’s own order, is a decision.
 */
private const val MAX_OTHER_FEEDS = 5

private const val PAUSED_AWAY_GRACE_MS = 30_000L

/**
 * How often the position is written while a film plays. Often enough that a
 * process killed from the task switcher loses seconds rather than an hour,
 * rarely enough that it is a disk write every frame.
 */
private const val SAVE_PROGRESS_EVERY_MS = 15_000L

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
            .clip(Corners.panel)
            .background(OverVideo.panel)
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

private fun Playable.logName(): String = when (this) {
    is Playable.Channel -> "channel $id"
    is Playable.CatchUp -> "catch-up on channel $channelId"
    is Playable.Film -> "film $id"
    is Playable.Episode -> "episode $id"
    // The name, not the path: a path across a log is noise, and this one is
    // long enough to wrap.
    is Playable.Recorded -> "recording \"$name\""
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
        is Playable.Channel, is Playable.CatchUp -> "ended (source closed the connection)"
        is Playable.Film, is Playable.Episode, is Playable.Recorded -> "ended"
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
    /** The sleep timer went off. */
    BySleepTimer,
}

/**
 * Shows or hides everything to do with moving about in a stream: the skip
 * buttons, the clock and the progress bar.
 *
 * On a live channel they are worse than useless — the clock reads
 * "00:16 · 00:00" over an empty bar, a position with nothing to be a position
 * in, which looks like a fault — and on anything with a length they are most
 * of what the controls are for. Media3 animates the clock and the bar but
 * never sets their visibility itself, so what is set here stays set.
 *
 * Called from the view's factory *and* its update, because a recording only
 * admits to having a length once it is ready, which is after the view exists.
 */
private fun PlayerView.showSeekControls(seekable: Boolean) {
    setShowFastForwardButton(seekable)
    setShowRewindButton(seekable)
    val visibility = if (seekable) View.VISIBLE else View.GONE
    findViewById<View>(androidx.media3.ui.R.id.exo_time)?.visibility = visibility
    findViewById<View>(androidx.media3.ui.R.id.exo_progress)?.visibility = visibility
}
