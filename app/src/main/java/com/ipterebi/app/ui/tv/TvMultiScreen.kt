package com.ipterebi.app.ui.tv

import android.util.Log
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
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
import com.ipterebi.app.ui.deafUntilPressed
import com.ipterebi.app.ui.theme.Corners
import com.ipterebi.app.ui.theme.Night
import com.ipterebi.app.ui.theme.tabular
import com.ipterebi.core.LiveStream
import com.ipterebi.core.MAX_PANES
import com.ipterebi.core.Pane
import com.ipterebi.core.StreamFormat
import com.ipterebi.core.XtreamAccount
import com.ipterebi.core.describePaneRefusal
import com.ipterebi.core.lineKey
import com.ipterebi.core.paneGrid
import com.ipterebi.core.paneOpenDelayMillis
import com.ipterebi.core.withPaneAdded
import com.ipterebi.core.withPaneChanged
import com.ipterebi.core.withPaneRemoved
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

    // The grid as it was left, else the favourites. Somebody who has arranged
    // four panes should not have to do it again tomorrow; somebody who never
    // has still gets something on the screen.
    var panes by remember(channels, account.lineKey) {
        val kept = container.multi.panes(account)
        mutableStateOf(
            kept.ifEmpty {
                channels.map { Pane(it.streamId) }.withSoundOn(channels.first().streamId)
            }
        )
    }

    /** Changed and written down in one place, so the two cannot drift. */
    fun arrange(next: List<Pane>) {
        panes = next
        container.multi.set(account, next)
    }

    // **The grid takes focus when it opens.** Focus otherwise stays on the
    // rail, and a pane that never has it can never be held -- so the one
    // gesture this screen is driven by would do nothing at all. The same trap
    // the search screen fell into.
    val firstPane = remember { FocusRequester() }

    // Which pane a hold is asking about, and whether it is picking a channel.
    var holding by remember { mutableStateOf<Int?>(null) }
    var picking by remember { mutableStateOf<Int?>(null) }

    // On open, and again whenever a panel closes. A panel takes focus into
    // itself and leaves nothing holding it on the way out, and a grid with no
    // focus is one the remote cannot drive at all.
    LaunchedEffect(holding, picking) {
        if (holding == null && picking == null) runCatching { firstPane.requestFocus() }
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
                    // From the lineup, not the favourites: a stored pane may
                    // be on a channel that was never starred.
                    val channel = state.find(pane.streamId) ?: continue
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
                        onTakeSound = { arrange(panes.withSoundOn(pane.streamId)) },
                        onHold = { holding = pane.streamId },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(if (index == 0) Modifier.focusRequester(firstPane) else Modifier),
                    )
                }
            }
        }
    }

    // Over the grid. The players keep running underneath, because stopping
    // them to show a menu would give the line up and have to take it back.
    holding?.let { id ->
        PanePanel(
            channelName = state.find(id)?.name.orEmpty(),
            canAdd = panes.size < MAX_PANES,
            canRemove = panes.size > 1,
            onChange = { picking = id; holding = null },
            onAdd = { picking = 0; holding = null },
            onRemove = { arrange(panes.withPaneRemoved(id)); holding = null },
            onClose = { holding = null },
        )
    }

    picking?.let { id ->
        ChannelPicker(
            state = state,
            alreadyUp = panes.map { it.streamId }.toSet(),
            onPick = { chosen ->
                arrange(
                    // 0 is the "add" case: nothing is being replaced.
                    if (id == 0) panes.withPaneAdded(chosen.streamId)
                    else panes.withPaneChanged(id, chosen.streamId)
                )
                picking = null
            },
            onClose = { picking = null },
        )
    }
}

/**
 * One pane: a player, its name, and whatever the panel said when it refused.
 *
 * [openAfter] is why each pane is its own composable — every one waits its
 * turn before asking, so a line is never hit with four requests at once. See
 * `PANE_OPEN_GAP_MS` for the measurement behind that.
 */
@OptIn(ExperimentalFoundationApi::class)
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
    onHold: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    // True from the first key repeat until the release that ends the hold.
    var held by remember { mutableStateOf(false) }

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
            // OK moves the sound; holding OK asks what else to do with this
            // pane. Holding is the only gesture left -- arrows move between
            // panes and the owner's remote has no number keys -- and it is
            // what this app already means "do something to this thing" by.
            //
            // **Read as a key repeat rather than as a long click.** A remote
            // whose OK key repeats -- the owner's box does -- sends a press,
            // a stream of repeats and then a release, so the first repeat is
            // the hold and arrives while the key is still down.
            // combinedClickable measures elapsed time instead, which a
            // repeating remote never gives it cleanly and which no synthetic
            // key event gives it at all.
            //
            // The release that ends a hold is swallowed, or it would land on
            // the panel the hold just opened -- the bug deafUntilPressed
            // exists for, met from the other side.
            .onKeyEvent { event ->
                val centre = event.key == Key.DirectionCenter || event.key == Key.Enter
                if (!centre) return@onKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        // A fresh press has a repeat count of zero, so it
                        // always starts a new gesture. Without this the flag
                        // could be left set -- the release that ends a hold
                        // is eaten by the panel the hold opened, never by the
                        // pane -- and the next ordinary press would do
                        // nothing at all.
                        if (event.nativeKeyEvent.repeatCount == 0) {
                            held = false
                        } else if (!held) {
                            held = true
                            onHold()
                        }
                        true
                    }
                    KeyEventType.KeyUp -> {
                        if (held) held = false else onTakeSound()
                        true
                    }
                    else -> false
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

/**
 * What a hold on a pane offers.
 *
 * **A hold, because the remote has nothing else to give.** The owner's has no
 * number keys, so a pane cannot be tuned the way a channel is; arrows move
 * between panes and OK moves the sound, which leaves holding OK as the only
 * free gesture. It is also the one this app already means "do something to
 * this thing" by, on a channel and on a poster.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PanePanel(
    channelName: String,
    canAdd: Boolean,
    canRemove: Boolean,
    onChange: () -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val entry = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { entry.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .padding(horizontal = 160.dp, vertical = 72.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                // The release that opened this must not land on the first
                // row: tv-material fires a long click while the key is still
                // down and then acts on the release regardless. See
                // deafUntilPressed.
                .deafUntilPressed()
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup()
                .clip(Corners.panel)
                .background(TvPanel)
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                channelName,
                style = MaterialTheme.typography.titleLarge,
                color = TvInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            TvRow(onClick = onChange, radius = 10.dp, modifier = Modifier.focusRequester(entry)) { f ->
                PanelLabel("Change channel", f)
            }
            if (canAdd) {
                TvRow(onClick = onAdd, radius = 10.dp) { f -> PanelLabel("Add a pane", f) }
            }
            if (canRemove) {
                TvRow(onClick = onRemove, radius = 10.dp) { f -> PanelLabel("Remove this pane", f) }
            }
        }
    }
}

@Composable
private fun PanelLabel(text: String, focused: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (focused) TvFocusInk else TvInk,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/**
 * Choosing a channel for a pane: the groups, then their channels.
 *
 * The same structure Live TV browses, because it is the line the viewer
 * already knows the shape of — and because the whole lineup is in the view
 * model already, so none of this costs a request.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ChannelPicker(
    state: TvLiveState,
    alreadyUp: Set<Int>,
    onPick: (LiveStream) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val entry = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { entry.requestFocus() } }

    val groups = remember(state.lineup, state.favourites, state.recents) {
        buildList {
            if (state.favourites.isNotEmpty()) add(TvGroup.Favourites)
            if (state.recents.isNotEmpty()) add(TvGroup.Recent)
            state.lineup?.groups?.forEach { add(TvGroup.Category(it.id)) }
            add(TvGroup.All)
        }
    }
    var group by remember(groups) { mutableStateOf(groups.firstOrNull() ?: TvGroup.All) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .padding(32.dp),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .deafUntilPressed()
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            LazyColumn(
                Modifier.width(320.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(groups, key = { state.nameOf(it) }) { one ->
                    TvRow(
                        onClick = { group = one },
                        onFocusChange = { if (it) group = one },
                        radius = 10.dp,
                        modifier = if (one == groups.first()) Modifier.focusRequester(entry) else Modifier,
                    ) { f ->
                        Text(
                            state.nameOf(one),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (f) TvFocusInk else if (one == group) TvAccent else TvInkSoft,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            }

            val channels = state.channelsIn(group)
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(channels, key = { it.streamId }) { channel ->
                    // Something already up is shown and refused rather than
                    // hidden: a channel missing from a list you are looking
                    // straight at reads as a fault, and the reason it cannot
                    // be picked is worth saying once.
                    val up = channel.streamId in alreadyUp
                    TvRow(onClick = { if (!up) onPick(channel) }, radius = 10.dp) { f ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                channel.number.toString(),
                                style = MaterialTheme.typography.bodySmall.tabular(),
                                color = if (f) TvFocusInk else TvInkSoft,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                            Text(
                                if (up) "${channel.name}   ·   already up" else channel.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (up) TvInkSoft else if (f) TvFocusInk else TvInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
