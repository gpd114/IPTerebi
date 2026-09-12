package com.ipterebi.app.ui.tv

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ipterebi.app.AppContainer
import com.ipterebi.app.ui.player.rememberProgrammeGuide
import com.ipterebi.core.LiveStream
import com.ipterebi.core.XtreamAccount
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where the rail at the list's left edge goes, besides back to the channel playing. */
enum class TvDestination(val label: String, val icon: ImageVector) {
    Films("Films", Icons.Filled.Movie),
    Series("Series", Icons.Filled.VideoLibrary),
    Settings("Settings", Icons.Filled.Settings),
}

/**
 * The channel list, over the picture: OK from full screen brings it up.
 *
 * Groups on the left, their channels beside them, and the focused channel's
 * programme on the right over the video, which carries on playing. Moving
 * through the groups changes the channels at once, as TiviMate's does —
 * clicking a group to see inside it would be a press per group for nothing.
 *
 * Left and right go between the columns directly, to the group or channel
 * that was current there, rather than to whatever row happens to sit
 * alongside: in two lists of different lengths "alongside" is arbitrary, and
 * on a line with hundreds of channels it is the wrong place almost always.
 */
@Composable
internal fun TvChannelList(
    state: TvLiveState,
    container: AppContainer,
    account: XtreamAccount,
    /** The group the remote is zapping through: the list opens on it. */
    zapGroup: TvGroup,
    tunedId: Int,
    onTune: (LiveStream, TvGroup) -> Unit,
    onFavourite: (LiveStream) -> Unit,
    onOpen: (TvDestination) -> Unit,
    onClose: () -> Unit,
    onRetry: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val groups = buildList {
        add(TvGroup.Favourites)
        add(TvGroup.Recent)
        add(TvGroup.All)
        state.lineup?.groups?.forEach { add(TvGroup.Category(it.id)) }
    }
    var shown by remember { mutableStateOf(zapGroup) }
    val channels = state.channelsIn(shown)

    // Where each column is entered: the row that was current there.
    val railEntry = remember { FocusRequester() }
    val groupEntry = remember { FocusRequester() }
    val channelEntry = remember { FocusRequester() }
    var channelIndex by remember(shown) {
        mutableIntStateOf(channels.indexOfFirst { it.streamId == tunedId }.coerceAtLeast(0))
    }
    val groupIndex = groups.indexOf(shown).coerceAtLeast(0)

    val groupList = rememberLazyListState(initialFirstVisibleItemIndex = (groupIndex - 4).coerceAtLeast(0))
    val channelList = rememberLazyListState(initialFirstVisibleItemIndex = (channelIndex - 4).coerceAtLeast(0))
    // A new group starts at its top, or at the channel playing if it is in there.
    LaunchedEffect(shown) { channelList.scrollToItem((channelIndex - 4).coerceAtLeast(0)) }

    fun focusIn(list: LazyListState, index: Int, requester: FocusRequester) {
        scope.launch {
            if (list.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                list.scrollToItem((index - 4).coerceAtLeast(0))
            }
            withFrameNanos { }
            runCatching { requester.requestFocus() }
        }
    }
    fun focusChannels() {
        if (channels.isEmpty()) return
        channelIndex = channelIndex.coerceIn(channels.indices)
        focusIn(channelList, channelIndex, channelEntry)
    }
    fun focusGroups() = focusIn(groupList, groupIndex, groupEntry)

    // Opens on the channel playing, or on its group when that is empty.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { if (channels.isNotEmpty()) channelEntry.requestFocus() else groupEntry.requestFocus() }
    }

    var focusedChannel by remember { mutableStateOf<LiveStream?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(note) { if (note != null) { delay(2_500); note = null } }

    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        0f to Color(0xF20B0C11), 0.8f to Color(0xE00B0C11), 1f to Color.Transparent,
                    )
                )
                .padding(start = 20.dp, end = 40.dp),
        ) {
            // The rail: back to the channel, or to the rest of the app.
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
                RailItem(Icons.Filled.LiveTv, "Live TV", selected = true, onClick = onClose, modifier = Modifier.focusRequester(railEntry))
                TvDestination.entries.forEach { d -> RailItem(d.icon, d.label, selected = false, onClick = { onOpen(d) }) }
            }

            // Groups.
            Column(
                Modifier
                    .width(222.dp)
                    .fillMaxHeight()
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionRight -> { focusChannels(); true }
                            Key.DirectionLeft -> { runCatching { railEntry.requestFocus() }; true }
                            else -> false
                        }
                    },
            ) {
                ColumnHeading("Groups")
                LazyColumn(state = groupList, contentPadding = PaddingValues(bottom = 24.dp)) {
                    itemsIndexed(groups, key = { _, g -> g.key }) { i, group ->
                        val count = state.channelsIn(group).size
                        TvRow(
                            onClick = { focusChannels() },
                            onFocusChange = { if (it) shown = group },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .then(if (i == groupIndex) Modifier.focusRequester(groupEntry) else Modifier),
                        ) { focused ->
                            Row(
                                Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
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

            Spacer(Modifier.width(12.dp))

            // Channels.
            Column(
                Modifier
                    .width(340.dp)
                    .fillMaxHeight()
                    .onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) { focusGroups(); true } else false
                    },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColumnHeading(state.nameOf(shown), Modifier.weight(1f))
                    note?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = TvPink, modifier = Modifier.padding(top = 18.dp))
                    }
                }
                if (channels.isEmpty()) {
                    Text(
                        when {
                            shown == TvGroup.Favourites -> "Nothing here yet. Hold OK on any channel to add it."
                            shown == TvGroup.Recent -> "Channels you watch will appear here."
                            state.lineup == null -> "Loading channels…"
                            else -> "No channels in this group."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TvInkSoft,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                LazyColumn(state = channelList, contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.weight(1f)) {
                    itemsIndexed(channels, key = { _, c -> c.streamId }) { i, channel ->
                        ChannelRow(
                            channel = channel,
                            number = state.lineup?.numberOf(channel.streamId) ?: 0,
                            playing = channel.streamId == tunedId,
                            favourite = state.isFavourite(channel.streamId),
                            onClick = { onTune(channel, shown) },
                            onLongClick = {
                                note = if (state.isFavourite(channel.streamId)) "Removed from Favourites" else "Added to Favourites"
                                onFavourite(channel)
                            },
                            onFocused = {
                                channelIndex = i
                                focusedChannel = channel
                            },
                            modifier = if (i == channelIndex) Modifier.focusRequester(channelEntry) else Modifier,
                        )
                    }
                }
                Text(
                    "Hold OK to add to or remove from Favourites",
                    style = MaterialTheme.typography.labelSmall,
                    color = TvInkSoft,
                    modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 18.dp),
                )
            }
        }

        // The focused channel's programme, over the picture at the right. Asked
        // for once the remote rests on a channel, not for every row passed on
        // the way down a list: each is a request to the panel.
        var settled by remember { mutableStateOf<LiveStream?>(null) }
        LaunchedEffect(focusedChannel) {
            delay(350)
            settled = focusedChannel
        }
        settled?.let { channel ->
            key(channel.streamId) {
                ProgrammeCard(
                    container = container,
                    account = account,
                    channel = channel,
                    number = state.lineup?.numberOf(channel.streamId) ?: 0,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 40.dp, bottom = 32.dp),
                )
            }
        }
    }
}

@Composable
private fun ColumnHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = TvInkSoft,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(start = 12.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun RailItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
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

@Composable
private fun ChannelRow(
    channel: LiveStream,
    number: Int,
    playing: Boolean,
    favourite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvRow(
        onClick = onClick,
        onLongClick = onLongClick,
        onFocusChange = { if (it) onFocused() },
        modifier = modifier.fillMaxWidth().height(56.dp),
    ) { focused ->
        val ink = if (focused) TvFocusInk else if (playing) TvAccent else TvInk
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (number > 0) "$number" else "",
                style = MaterialTheme.typography.labelLarge,
                color = if (focused) TvFocusInk.copy(alpha = 0.7f) else TvInkSoft,
                textAlign = TextAlign.End,
                modifier = Modifier.width(44.dp),
            )
            Spacer(Modifier.width(12.dp))
            TvChannelLogo(channel, size = 38.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                channel.name,
                style = MaterialTheme.typography.titleSmall,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (favourite) {
                Icon(Icons.Filled.Star, contentDescription = "Favourite", tint = if (focused) TvFocusInk else TvPink, modifier = Modifier.size(18.dp))
            }
            if (playing) {
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.GraphicEq, contentDescription = "Playing", tint = if (focused) TvFocusInk else TvAccent, modifier = Modifier.size(20.dp))
            }
        }
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

/** What is on the focused channel, now and next. Nothing at all when the line has no guide for it. */
@Composable
private fun ProgrammeCard(
    container: AppContainer,
    account: XtreamAccount,
    channel: LiveStream,
    number: Int,
    modifier: Modifier = Modifier,
) {
    val guide = rememberProgrammeGuide(container, account, channel.streamId, channel.epgChannelId)
    val now = guide.now ?: return
    Column(
        modifier
            .widthIn(max = 250.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(TvPanel)
            .padding(16.dp),
    ) {
        Text(
            if (number > 0) "$number  ${channel.name}" else channel.name,
            style = MaterialTheme.typography.labelMedium,
            color = TvInkSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(now.titleText, style = MaterialTheme.typography.titleMedium, color = TvInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(now.timeLabel(), style = MaterialTheme.typography.bodySmall, color = TvInkSoft)
        guide.progress?.let { p ->
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { p },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                color = TvAccent,
                trackColor = Color(0x33FFFFFF),
            )
        }
        now.descriptionText.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = TvInkSoft, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        guide.next?.let { next ->
            Spacer(Modifier.height(8.dp))
            Text(
                "Next  ${next.timeLabel().substringBefore('–').trim()}  ${next.titleText}",
                style = MaterialTheme.typography.bodySmall,
                color = TvInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
