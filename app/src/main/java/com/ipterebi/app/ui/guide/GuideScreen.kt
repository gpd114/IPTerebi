package com.ipterebi.app.ui.guide

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ipterebi.app.AppContainer
import com.ipterebi.app.data.AccountState
import com.ipterebi.app.ui.Panel
import com.ipterebi.app.ui.PrimaryButton
import com.ipterebi.app.ui.QuietPill
import com.ipterebi.app.ui.ScreenTopBar
import com.ipterebi.app.ui.SecondaryButton
import com.ipterebi.app.ui.channels.tileColour
import com.ipterebi.app.ui.focusRing
import com.ipterebi.app.ui.theme.Night
import com.ipterebi.core.GUIDE_STEP
import com.ipterebi.core.LiveStream
import com.ipterebi.core.XmltvProgramme
import com.ipterebi.core.XtreamAccount
import com.ipterebi.core.channelInitials
import com.ipterebi.core.floorToGuideStep
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** How wide a minute is. A half-hour programme fits its title on a phone. */
private val PerMinute = 4.dp
private val ChannelColumn = 64.dp
private val RowHeight = 58.dp

/**
 * The TV guide on a phone or tablet: the channels that were on screen in Live
 * TV down the side, time across, dragged sideways through the day and flung
 * like any list. Tap a programme for what it is, and to watch it when it is on.
 *
 * Everything comes from the full guide on the device (see `Guide`): a row is a
 * local query, never a request. Until the phone has fetched that guide — it
 * does so by itself on Wi-Fi, and it is large — the screen says so and offers
 * to fetch it now.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(container: AppContainer, onChannel: (Int) -> Unit, onBack: () -> Unit) {
    val accountState by container.credentials.state.collectAsStateWithLifecycle(AccountState.Loading)
    val account = (accountState as? AccountState.SignedIn)?.account
    val channels by container.channels.items.collectAsStateWithLifecycle()
    val title by container.channelsTitle.collectAsStateWithLifecycle()
    val guideVersion by container.guide.version.collectAsStateWithLifecycle()
    val downloading by container.guide.downloading.collectAsStateWithLifecycle()

    val zone = remember { ZoneId.systemDefault() }
    var now by remember { mutableLongStateOf(Instant.now().epochSecond) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = Instant.now().epochSecond
        }
    }
    val earliest = remember { floorToGuideStep(now) - 2 * 3600 }
    val latest = remember { now + 4 * 24 * 3600 }

    var fetchedAt by remember { mutableStateOf<Long?>(0L) }
    LaunchedEffect(account, guideVersion) { fetchedAt = account?.let { container.guide.fetchedAt(it) } }

    val schedules = remember(guideVersion) { mutableStateMapOf<String, List<XmltvProgramme>>() }
    var selected by remember { mutableStateOf<Pair<LiveStream, XmltvProgramme>?>(null) }

    Column(Modifier.fillMaxSize().background(Night.ground)) {
        ScreenTopBar(title = "TV guide", onBack = onBack)
        Text(
            if (channels.isEmpty()) "Pick a category in Live TV first." else "$title · ${channels.size} channels",
            style = MaterialTheme.typography.bodySmall,
            color = Night.inkSoft,
            modifier = Modifier.padding(start = 72.dp, bottom = 6.dp),
        )

        if (account != null && fetchedAt == null) {
            Panel(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text(
                    if (downloading) "Downloading the TV guide…" else "The TV guide hasn't been downloaded yet",
                    style = MaterialTheme.typography.titleSmall,
                    color = Night.ink,
                )
                Text(
                    "It downloads by itself on Wi-Fi, twice a day at most. It's the whole schedule for " +
                        "your line — tens of megabytes — so it waits for Wi-Fi unless you ask.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Night.inkSoft,
                )
                if (!downloading) {
                    PrimaryButton(onClick = { container.guide.refreshNow(account) }, height = 44.dp) {
                        Text("Download it now", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        if (account == null) return@Column

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val pxPerSecond = with(density) { PerMinute.toPx() } / 60f
            val gridWidth = maxWidth - ChannelColumn - 12.dp
            val visibleSeconds = (with(density) { gridWidth.toPx() } / pxPerSecond).toLong()

            // The time at the left edge, dragged and flung.
            val start = remember { Animatable((now - 20 * 60).toFloat()) }
            LaunchedEffect(visibleSeconds) {
                start.updateBounds(earliest.toFloat(), (latest - visibleSeconds).toFloat())
            }
            val scope = rememberCoroutineScope()
            val drag = rememberDraggableState { delta ->
                scope.launch { start.snapTo(start.value - delta / pxPerSecond) }
            }
            val windowStart = start.value.toLong()
            val windowEnd = windowStart + visibleSeconds

            Column(Modifier.fillMaxSize()) {
                TimeHeader(windowStart, visibleSeconds, now, zone, onNow = {
                    scope.launch { start.animateTo((now - 20 * 60).toFloat()) }
                })
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .draggable(
                            state = drag,
                            orientation = Orientation.Horizontal,
                            onDragStopped = { velocity ->
                                start.animateDecay(-velocity / pxPerSecond, exponentialDecay(frictionMultiplier = 1.4f))
                            },
                        ),
                    contentPadding = PaddingValues(start = 12.dp, bottom = 24.dp),
                ) {
                    items(channels, key = { it.streamId }) { channel ->
                        LaunchedEffect(channel.epgChannelId, guideVersion) {
                            if (!schedules.containsKey(channel.epgChannelId)) {
                                schedules[channel.epgChannelId] =
                                    container.guide.schedule(account, channel.epgChannelId, earliest, latest)
                            }
                        }
                        GuideRow(
                            channel = channel,
                            programmes = schedules[channel.epgChannelId].orEmpty(),
                            windowStart = windowStart,
                            windowEnd = windowEnd,
                            now = now,
                            onChannel = { onChannel(channel.streamId) },
                            onProgramme = { selected = channel to it },
                        )
                    }
                }
            }
        }
    }

    selected?.let { (channel, programme) ->
        ModalBottomSheet(onDismissRequest = { selected = null }, containerColor = Night.veil) {
            ProgrammeSheet(channel, programme, now, zone, onWatch = {
                selected = null
                onChannel(channel.streamId)
            })
        }
    }
}

@Composable
private fun TimeHeader(windowStart: Long, visibleSeconds: Long, now: Long, zone: ZoneId, onNow: () -> Unit) {
    val clock = remember { DateTimeFormatter.ofPattern("HH:mm") }
    Box(Modifier.fillMaxWidth().height(32.dp)) {
        Row(Modifier.fillMaxSize().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                dayName(windowStart + visibleSeconds / 2, zone),
                style = MaterialTheme.typography.labelMedium,
                color = Night.accent,
                modifier = Modifier.width(ChannelColumn),
            )
            // Exactly as wide as the rows' time below, so the marks sit over
            // the programmes they name.
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                val width = maxWidth
                var mark = floorToGuideStep(windowStart)
                while (mark < windowStart + visibleSeconds) {
                    if (mark >= windowStart) {
                        Text(
                            Instant.ofEpochSecond(mark).atZone(zone).format(clock),
                            style = MaterialTheme.typography.labelMedium,
                            color = Night.inkSoft,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = width * ((mark - windowStart).toFloat() / visibleSeconds))
                                .padding(start = 4.dp),
                        )
                    }
                    mark += GUIDE_STEP
                }
            }
        }
        // Back to now, when the guide has been dragged away from it — over
        // the marks, not beside them: beside, it narrowed the header's time
        // and the marks slid off the programmes below.
        if (now !in windowStart until windowStart + visibleSeconds) {
            QuietPill(
                "Now",
                fill = Night.cobalt,
                colour = Color.White,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onNow),
            )
        }
    }
}

@Composable
private fun GuideRow(
    channel: LiveStream,
    programmes: List<XmltvProgramme>,
    windowStart: Long,
    windowEnd: Long,
    now: Long,
    onChannel: () -> Unit,
    onProgramme: (XmltvProgramme) -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(RowHeight).padding(vertical = 3.dp)) {
        // The channel: its logo, or initials; tap to watch.
        Box(
            Modifier
                .width(ChannelColumn - 6.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .background(Night.veil)
                .focusRing(RoundedCornerShape(12.dp))
                .clickable(onClick = onChannel),
            contentAlignment = Alignment.Center,
        ) {
            GuideLogo(channel)
        }
        Spacer(Modifier.width(6.dp))
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
            val width = maxWidth
            val span = (windowEnd - windowStart).toFloat()
            fun x(seconds: Long): Dp = width * ((seconds - windowStart) / span)
            if (programmes.isEmpty()) {
                Text(
                    channel.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = Night.inkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp),
                )
            }
            programmes.filter { it.stop > windowStart && it.start < windowEnd }.forEach { p ->
                val left = x(maxOf(p.start, windowStart))
                val cellWidth = x(minOf(p.stop, windowEnd)) - left
                // The tail of a programme ending just inside the edge: a sliver
                // with dots in it says nothing, and cannot be tapped anyway.
                if (cellWidth < 20.dp) return@forEach
                val onNow = p.start <= now && now < p.stop
                Box(
                    Modifier
                        .offset(x = left)
                        .width(cellWidth)
                        .fillMaxHeight()
                        .padding(end = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (onNow) Night.quiet else Night.veil)
                        .focusRing(RoundedCornerShape(10.dp))
                        .clickable { onProgramme(p) }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    // Too narrow for even a few letters, a title is only "…";
                    // the block alone says something is on, and a tap tells what.
                    if (cellWidth >= 52.dp) {
                        Text(
                            p.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (onNow) Night.ink else Night.quietText,
                            // Two lines only where a word fits a line: a narrow
                            // cell broke "Through" into "Throug / h".
                            maxLines = if (cellWidth < 110.dp) 1 else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (now in windowStart until windowEnd) {
                Box(Modifier.offset(x = x(now)).width(2.dp).fillMaxHeight().background(Night.pink))
            }
        }
    }
}

@Composable
private fun GuideLogo(channel: LiveStream) {
    var failed by remember(channel.icon) { mutableStateOf(false) }
    if (channel.icon.isNotBlank() && !failed) {
        AsyncImage(
            model = channel.icon,
            contentDescription = channel.name,
            onError = { failed = true },
            modifier = Modifier.size(40.dp),
        )
    } else {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(tileColour(channel.name)),
            contentAlignment = Alignment.Center,
        ) {
            Text(channelInitials(channel.name), style = MaterialTheme.typography.labelMedium, color = Color.White)
        }
    }
}

@Composable
private fun ProgrammeSheet(channel: LiveStream, programme: XmltvProgramme, now: Long, zone: ZoneId, onWatch: () -> Unit) {
    val clock = DateTimeFormatter.ofPattern("HH:mm")
    val from = Instant.ofEpochSecond(programme.start).atZone(zone)
    val to = Instant.ofEpochSecond(programme.stop).atZone(zone)
    val onNow = programme.start <= now && now < programme.stop
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(channel.name, style = MaterialTheme.typography.labelLarge, color = Night.accent)
        Text(programme.title.ifBlank { "No title" }, style = MaterialTheme.typography.headlineSmall, color = Night.ink)
        Text(
            "${dayName(programme.start, zone)} ${from.format(clock)} – ${to.format(clock)}" +
                when {
                    onNow -> " · on now"
                    programme.stop <= now -> " · finished"
                    else -> ""
                },
            style = MaterialTheme.typography.bodyMedium,
            color = Night.inkSoft,
        )
        if (onNow) {
            LinearProgressIndicator(
                progress = { ((now - programme.start).toFloat() / (programme.stop - programme.start)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = Night.accent,
                trackColor = Night.quiet,
                drawStopIndicator = {},
                gapSize = 0.dp,
            )
        }
        if (programme.description.isNotBlank()) {
            Text(programme.description, style = MaterialTheme.typography.bodyMedium, color = Night.ink)
        }
        Spacer(Modifier.height(6.dp))
        if (onNow) {
            PrimaryButton(onClick = onWatch, modifier = Modifier.fillMaxWidth()) {
                Text("Watch ${channel.name}", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            // Not on yet, or finished: the channel is still on something now.
            SecondaryButton("Watch ${channel.name} now", onClick = onWatch, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** "Today", "Tomorrow", "Sat 13 Sep". */
private fun dayName(seconds: Long, zone: ZoneId): String {
    val day = Instant.ofEpochSecond(seconds).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (day) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofPattern("EEE d MMM"))
    }
}
