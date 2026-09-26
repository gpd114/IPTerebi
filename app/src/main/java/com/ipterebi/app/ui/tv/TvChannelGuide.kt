package com.ipterebi.app.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ipterebi.app.R
import com.ipterebi.app.AppContainer
import com.ipterebi.core.LiveStream
import com.ipterebi.core.XmltvProgramme
import com.ipterebi.core.XtreamAccount
import com.ipterebi.core.anchorOf
import com.ipterebi.core.floorToGuideStep
import com.ipterebi.core.nextSlot
import com.ipterebi.core.previousSlot
import com.ipterebi.core.slotAt
import com.ipterebi.core.windowFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** Where the rail at the groups' left edge goes, besides back to the channel playing. */
enum class TvDestination(val label: String) {
    // Home is here because the app opens on it now. Without it the guide was
    // a room with no door back to the screen the box starts on.
    Home("Home"),
    Films("Films"),
    Series("Series"),
    Settings("Settings"),
    ;

    /** The app's own nav icons, so this rail and the phone's are one set. */
    @Composable
    fun painter(): Painter = when (this) {
        Home -> painterResource(R.drawable.ic_nav_home)
        Films -> painterResource(R.drawable.ic_nav_films)
        Series -> painterResource(R.drawable.ic_nav_series)
        Settings -> rememberVectorPainter(Icons.Filled.Settings)
    }
}

/**
 * The channels, with their guide: OK from full screen brings it up.
 *
 * One screen rather than a channel list and a separate guide, as the owner
 * asked — a group is chosen and its channels are the guide's rows: channels
 * down, time across, the focused programme described at the top, and the
 * channel playing still on in the top right. That is the *same* player, resized
 * into a corner this screen leaves unpainted, so it never costs a second
 * stream on a one-connection line.
 *
 * - Up and down move between channels, right looks ahead along a channel's
 *   programmes, and the window follows (the rules are `GuideGrid` in `core/`).
 * - Left from the programme on now slides the groups out — and the rail to
 *   Films, Series and Settings beyond them. Moving through the groups changes
 *   the rows at once; OK or right goes back into them.
 * - OK watches the focused channel, full screen. Holding OK adds it to
 *   Favourites or takes it off. Back closes.
 *
 * The past is not reachable yet: until catch-up, there is nothing to do with a
 * programme that has finished, and stopping left at "now" is what makes left
 * the way to the groups.
 */
@Composable
internal fun TvChannelGuide(
    state: TvLiveState,
    container: AppContainer,
    account: XtreamAccount,
    /** The group the remote is zapping through: the guide opens on it. */
    zapGroup: TvGroup,
    tunedId: Int,
    onTune: (LiveStream, TvGroup) -> Unit,
    onFavourite: (LiveStream) -> Unit,
    onOpen: (TvDestination) -> Unit,
    onClose: () -> Unit,
    onRetry: () -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    var now by remember { mutableLongStateOf(Instant.now().epochSecond) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = Instant.now().epochSecond
        }
    }
    val latest = remember { now + 4 * 24 * 3600 }

    val groups = buildList {
        add(TvGroup.Favourites)
        add(TvGroup.Recent)
        add(TvGroup.All)
        state.lineup?.groups?.forEach { add(TvGroup.Category(it.id)) }
    }
    var shown by remember { mutableStateOf(zapGroup) }
    val channels = state.channelsIn(shown)
    // Opens on the groups when there is nothing in this one to show.
    var groupsOpen by remember { mutableStateOf(channels.isEmpty()) }

    val guideVersion by container.guide.version.collectAsStateWithLifecycle()
    val schedules = remember(guideVersion) { mutableStateMapOf<String, List<XmltvProgramme>>() }
    fun programmesOf(channel: LiveStream) = schedules[channel.epgChannelId].orEmpty()

    // Where the cursor is, in the group shown; a new group starts afresh, on
    // the channel playing when it is in there.
    var row by remember(shown) { mutableIntStateOf(channels.indexOfFirst { it.streamId == tunedId }.coerceAtLeast(0)) }
    var windowStart by remember(shown) { mutableLongStateOf(floorToGuideStep(now)) }
    // The point in time focused; the slot is worked out from it each time, so
    // a row whose programmes arrive after the cursor lands still shows them.
    var anchor by remember(shown) { mutableLongStateOf(now) }
    val focusedChannel = channels.getOrNull(row)
    val currentSlot = focusedChannel?.let { slotAt(programmesOf(it), anchor) }

    val rows = remember(shown) { LazyListState((row - 2).coerceAtLeast(0)) }
    LaunchedEffect(row, shown) {
        val visible = rows.layoutInfo.visibleItemsInfo
        val first = visible.firstOrNull()?.index ?: 0
        val last = visible.lastOrNull()?.index ?: 0
        if (row <= first || row >= last) rows.animateScrollToItem((row - 2).coerceAtLeast(0))
    }
    // Rows near the cursor, from the full guide on the device: a quick local
    // query each, never a request.
    LaunchedEffect(row, guideVersion, channels) {
        if (channels.isEmpty()) return@LaunchedEffect
        val range = (row - 8).coerceAtLeast(0)..(row + 12).coerceAtMost(channels.lastIndex)
        for (i in range) {
            val channel = channels[i]
            if (channel.epgChannelId.isBlank() || schedules.containsKey(channel.epgChannelId)) continue
            schedules[channel.epgChannelId] = container.guide.schedule(account, channel.epgChannelId, now - 3600, latest)
        }
    }

    var note by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(note) { if (note != null) { delay(2_500); note = null } }

    // OK is acted on when it is let go, so that holding it can mean something
    // else: a first repeat while still held is a long press.
    val okHeld = remember { BooleanArray(1) }

    val gridFocus = remember { FocusRequester() }
    LaunchedEffect(groupsOpen) {
        if (!groupsOpen) {
            withFrameNanos { }
            runCatching { gridFocus.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .focusRequester(gridFocus)
                .focusable()
                .onKeyEvent { e ->
                    if (groupsOpen) return@onKeyEvent false
                    // From the state as it is now, not as last drawn: a held
                    // button sends keys faster than the grid redraws.
                    val channel = channels.getOrNull(row)
                    if (e.key in Select) {
                        if (channel == null) return@onKeyEvent true
                        when (e.type) {
                            KeyEventType.KeyDown -> {
                                val repeat = e.nativeKeyEvent.repeatCount
                                if (repeat == 0) {
                                    okHeld[0] = true
                                } else if (okHeld[0]) {
                                    okHeld[0] = false
                                    note = if (state.isFavourite(channel.streamId)) {
                                        "${channel.name} taken off Favourites"
                                    } else {
                                        "${channel.name} added to Favourites"
                                    }
                                    onFavourite(channel)
                                }
                            }
                            KeyEventType.KeyUp -> if (okHeld[0]) {
                                okHeld[0] = false
                                onTune(channel, shown)
                            }
                        }
                        return@onKeyEvent true
                    }
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent e.key in Arrows
                    if (e.key == Key.Menu) {
                        groupsOpen = true
                        return@onKeyEvent true
                    }
                    if (channel == null) {
                        if (e.key == Key.DirectionLeft) groupsOpen = true
                        return@onKeyEvent e.key in Arrows
                    }
                    val here = slotAt(programmesOf(channel), anchor)
                    when (e.key) {
                        Key.DirectionUp, Key.ChannelUp -> {
                            if (row > 0) {
                                row--
                                anchor = anchorOf(here, windowStart)
                            }
                            true
                        }
                        Key.DirectionDown, Key.ChannelDown -> {
                            if (row < channels.lastIndex) {
                                row++
                                anchor = anchorOf(here, windowStart)
                            }
                            true
                        }
                        Key.DirectionRight -> {
                            val next = nextSlot(programmesOf(channel), here)
                            if (next.start < latest) {
                                anchor = next.start
                                windowStart = windowFor(windowStart, VISIBLE_SECONDS, next, floorToGuideStep(now))
                            }
                            true
                        }
                        Key.DirectionLeft -> {
                            val previous = previousSlot(programmesOf(channel), here)
                            if (previous.stop > now) {
                                anchor = previous.start
                                windowStart = windowFor(windowStart, VISIBLE_SECONDS, previous, floorToGuideStep(now))
                            } else {
                                // At what is on now: left is the way to the groups.
                                groupsOpen = true
                            }
                            true
                        }
                        else -> false
                    }
                },
        ) {
            Row(Modifier.fillMaxWidth().height(GuideTopHeight)) {
                ProgrammeDetails(
                    channel = focusedChannel,
                    number = focusedChannel?.let { state.lineup?.numberOf(it.streamId) } ?: 0,
                    slot = currentSlot,
                    now = now,
                    zone = zone,
                    footer = note ?: "◀  Groups      OK  Watch      Hold OK  Favourite",
                    footerColour = if (note != null) TvPink else TvInkSoft,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(TvGround)
                        .padding(start = 48.dp, top = 28.dp, end = 24.dp),
                )
                // Unpainted: the player shows through here.
                Spacer(Modifier.width(GuidePreviewWidth + 48.dp))
            }

            Column(Modifier.fillMaxSize().background(TvGround).padding(start = 32.dp, end = 32.dp)) {
                TimeHeader(state.nameOf(shown), windowStart, now, zone)
                if (channels.isEmpty()) {
                    Text(
                        when {
                            shown == TvGroup.Favourites -> "No favourites yet. Hold OK on any channel to add it."
                            shown == TvGroup.Recent -> "Channels you watch will appear here."
                            state.lineup == null -> "Loading channels…"
                            else -> "No channels in this group."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = TvInkSoft,
                        modifier = Modifier.padding(start = 12.dp, top = 20.dp),
                    )
                }
                LazyColumn(state = rows, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(channels, key = { _, c -> c.streamId }) { i, channel ->
                        GuideRow(
                            channel = channel,
                            number = state.lineup?.numberOf(channel.streamId) ?: 0,
                            programmes = programmesOf(channel),
                            windowStart = windowStart,
                            now = now,
                            focusedSlot = if (i == row && !groupsOpen) currentSlot else null,
                            tuned = channel.streamId == tunedId,
                            favourite = state.isFavourite(channel.streamId),
                        )
                    }
                }
            }
        }

        if (groupsOpen) {
            GroupsPanel(
                state = state,
                groups = groups,
                shown = shown,
                zapGroup = zapGroup,
                onShow = { shown = it },
                onChoose = { groupsOpen = false },
                onOpen = onOpen,
                onClose = onClose,
                onRetry = onRetry,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
    }
}

/**
 * The groups, slid out over the guide's left side, with the rail beyond them.
 * Focus moving through them shows each group's channels behind at once, as
 * TiviMate does — clicking into a group to see inside it would be a press per
 * group for nothing.
 */
@Composable
private fun GroupsPanel(
    state: TvLiveState,
    groups: List<TvGroup>,
    shown: TvGroup,
    zapGroup: TvGroup,
    onShow: (TvGroup) -> Unit,
    onChoose: () -> Unit,
    onOpen: (TvDestination) -> Unit,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val railEntry = remember { FocusRequester() }
    val groupEntry = remember { FocusRequester() }
    val shownIndex = groups.indexOf(shown).coerceAtLeast(0)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (shownIndex - 4).coerceAtLeast(0))

    /**
     * Puts the remote on the group you are in.
     *
     * Tried over several frames rather than once, and the panel gives up and
     * closes if it never lands. One attempt a frame after scrolling was enough
     * on the fake panel's handful of groups and not enough on a real line:
     * on the box, with hundreds of groups, the item at [shownIndex] was often
     * not composed yet, requestFocus threw into a runCatching that swallowed
     * it, and *nothing* held focus. The grid had already stopped taking keys
     * because the panel was open, so the remote went dead until the guide was
     * closed and opened again — which is what a real line actually did.
     */
    fun focusGroups() {
        scope.launch {
            if (list.layoutInfo.visibleItemsInfo.none { it.index == shownIndex }) {
                list.scrollToItem((shownIndex - 4).coerceAtLeast(0))
            }
            repeat(FOCUS_TRIES) {
                withFrameNanos { }
                if (runCatching { groupEntry.requestFocus() }.isSuccess) return@launch
            }
            // Nothing took it. Better to close than to strand the remote:
            // the guide takes focus back when the panel goes.
            onChoose()
        }
    }
    LaunchedEffect(Unit) { focusGroups() }

    Row(
        modifier
            .fillMaxHeight()
            .background(Brush.horizontalGradient(0f to Color(0xF80B0C11), 0.85f to Color(0xF00B0C11), 1f to Color.Transparent))
            .padding(start = 20.dp, end = 36.dp),
    ) {
        Column(
            Modifier
                .width(72.dp)
                .fillMaxHeight()
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight) { focusGroups(); true } else false
                },
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RailItem(painterResource(R.drawable.ic_nav_live), "Live TV", selected = true, onClick = onClose, modifier = Modifier.focusRequester(railEntry))
            TvDestination.entries.forEach { d -> RailItem(d.painter(), d.label, selected = false, onClick = { onOpen(d) }) }
        }

        Column(
            Modifier
                .width(250.dp)
                .fillMaxHeight()
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionRight -> { onChoose(); true }
                        Key.DirectionLeft -> { runCatching { railEntry.requestFocus() }; true }
                        else -> false
                    }
                },
        ) {
            Text(
                "Groups",
                style = MaterialTheme.typography.labelMedium,
                color = TvInkSoft,
                modifier = Modifier.padding(start = 12.dp, top = 24.dp, bottom = 8.dp),
            )
            LazyColumn(state = list, contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(groups, key = { _, g -> g.key }) { i, group ->
                    val count = state.channelsIn(group).size
                    TvRow(
                        onClick = onChoose,
                        onFocusChange = { if (it) onShow(group) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .then(if (i == shownIndex) Modifier.focusRequester(groupEntry) else Modifier),
                    ) { focused ->
                        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                state.nameOf(group),
                                style = MaterialTheme.typography.titleSmall,
                                color = when {
                                    focused -> TvFocusInk
                                    group == zapGroup -> TvAccent
                                    else -> TvInk
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (count > 0) {
                                Text(
                                    "$count",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (focused) TvFocusInk.copy(alpha = 0.6f) else TvInkSoft,
                                )
                            }
                        }
                    }
                }
                if (state.lineup == null) {
                    item(key = "status") { LineupStatus(state, onRetry) }
                }
            }
        }
    }
}

@Composable
private fun RailItem(icon: Painter, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Named under the icon, small, as the phone's rail is: an icon alone is a
    // guess, and a name only on focus is found by pressing.
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        var focusedHere by remember { mutableStateOf(false) }
        TvRow(
            onClick = onClick,
            onFocusChange = { focusedHere = it },
            modifier = modifier.size(48.dp),
            radius = 16.dp,
        ) { focused ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(if (selected && !focused) TvCobalt else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (focused) TvFocusInk else if (selected) Color.White else TvInkSoft,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (focusedHere || selected) TvInk else TvInkSoft,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Why the lineup is not there yet, below the groups that do not need it. */
@Composable
private fun LineupStatus(state: TvLiveState, onRetry: () -> Unit) {
    if (state.error != null) {
        Column(Modifier.padding(12.dp)) {
            Text(state.error, style = MaterialTheme.typography.bodySmall, color = TvPink)
            Spacer(Modifier.height(8.dp))
            TvButton("Try again", onClick = onRetry)
        }
    } else {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), color = TvAccent, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("Loading every channel…", style = MaterialTheme.typography.bodySmall, color = TvInkSoft)
        }
    }
}

/**
 * How many frames the groups panel will wait for its list to compose before
 * giving up on focus. Ten is about a sixth of a second on the box, which is
 * long enough for a list of hundreds and short enough not to be seen.
 */
private const val FOCUS_TRIES = 10

private val Select = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)
private val Arrows = setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.ChannelUp, Key.ChannelDown)
