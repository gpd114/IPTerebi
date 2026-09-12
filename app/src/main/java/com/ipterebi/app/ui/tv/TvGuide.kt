package com.ipterebi.app.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf

import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ipterebi.app.AppContainer
import com.ipterebi.core.GUIDE_STEP
import com.ipterebi.core.GuideSlot
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Two hours across: enough to plan an evening on a 960dp screen, big enough to read. */
internal const val VISIBLE_SECONDS = 2 * 3600L
private val ChannelColumn = 210.dp
private val RowHeight = 46.dp
/** Where the parent draws the live picture: the guide leaves this corner unpainted. */
internal val GuidePreviewWidth = 336.dp
internal val GuidePreviewHeight = 189.dp
internal val GuideTopHeight = 213.dp


/** Behind the guide: the same near-black as the panels, but solid — it is a page, not an overlay. */
internal val TvGround = Color(0xFF0B0C11)
private val CellFill = Color(0xFF191B23)
private val CellNowFill = Color(0xFF20263A)

@Composable
internal fun ProgrammeDetails(
    channel: LiveStream?,
    number: Int,
    slot: GuideSlot?,
    now: Long,
    zone: ZoneId,
    modifier: Modifier,
    /** A line along the bottom: the keys, or what a key just did. */
    footer: String? = null,
    footerColour: Color = TvInkSoft,
) {
    Column(modifier) {
        Text(
            listOfNotNull(number.takeIf { it > 0 }?.toString(), channel?.name).joinToString("  "),
            style = MaterialTheme.typography.labelLarge,
            color = TvAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val programme = slot?.programme
        Text(
            programme?.title?.ifBlank { null } ?: "No programme information",
            style = MaterialTheme.typography.headlineSmall,
            color = if (programme != null) TvInk else TvInkSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (slot != null) {
            Text(
                slotTime(slot, now, zone),
                style = MaterialTheme.typography.bodyMedium,
                color = TvInkSoft,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (slot.programme != null && slot.isOnAt(now)) {
                LinearProgressIndicator(
                    progress = { ((now - slot.start).toFloat() / (slot.stop - slot.start)).coerceIn(0f, 1f) },
                    modifier = Modifier.padding(top = 8.dp).width(260.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = TvAccent,
                    trackColor = Color(0x33FFFFFF),
                )
            }
        }
        programme?.description?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = TvInkSoft,
                maxLines = if (footer != null) 2 else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        if (footer != null) {
            Spacer(Modifier.weight(1f))
            Text(
                footer,
                style = MaterialTheme.typography.labelSmall,
                color = footerColour,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
    }
}

/** "Today 20:00 – 23:00 · on now", "Tomorrow 06:00 – 07:00". */
private fun slotTime(slot: GuideSlot, now: Long, zone: ZoneId): String {
    val clock = DateTimeFormatter.ofPattern("HH:mm")
    val start = Instant.ofEpochSecond(slot.start).atZone(zone)
    val stop = Instant.ofEpochSecond(slot.stop).atZone(zone)
    val today = LocalDate.now(zone)
    val day = when (start.toLocalDate()) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> start.format(DateTimeFormatter.ofPattern("EEE d MMM"))
    }
    val status = when {
        slot.isOnAt(now) -> " · on now"
        slot.stop <= now -> " · finished"
        else -> ""
    }
    return "$day ${start.format(clock)} – ${stop.format(clock)}$status"
}

@Composable
internal fun TimeHeader(groupName: String, windowStart: Long, now: Long, zone: ZoneId) {
    val clock = remember { DateTimeFormatter.ofPattern("HH:mm") }
    Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            groupName,
            style = MaterialTheme.typography.labelMedium,
            color = TvInkSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(ChannelColumn).padding(start = 12.dp),
        )
        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
            val perSecond = 1f / VISIBLE_SECONDS
            var mark = windowStart
            while (mark < windowStart + VISIBLE_SECONDS) {
                val fraction = (mark - windowStart) * perSecond
                TimeMark(fraction, Instant.ofEpochSecond(mark).atZone(zone).format(clock))
                mark += GUIDE_STEP
            }
            if (now in windowStart until windowStart + VISIBLE_SECONDS) {
                NowTick((now - windowStart) * perSecond)
            }
        }
    }
}

@Composable
private fun TimeMark(fraction: Float, label: String) {
    FractionOffset(fraction) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TvInk, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun NowTick(fraction: Float) {
    FractionOffset(fraction) {
        Box(Modifier.width(2.dp).fillMaxHeight().background(TvPink))
    }
}

/** Places [content] a [fraction] of the way across the parent's width. */
@Composable
private fun FractionOffset(fraction: Float, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(Modifier.offset(x = maxWidth * fraction).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
            content()
        }
    }
}

@Composable
internal fun GuideRow(
    channel: LiveStream,
    number: Int,
    programmes: List<XmltvProgramme>,
    windowStart: Long,
    now: Long,
    focusedSlot: GuideSlot?,
    tuned: Boolean,
    favourite: Boolean = false,
) {
    val windowEnd = windowStart + VISIBLE_SECONDS
    Row(Modifier.fillMaxWidth().height(RowHeight).padding(vertical = 3.dp)) {
        Row(
            Modifier
                .width(ChannelColumn)
                .fillMaxHeight()
                .padding(end = 6.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (focusedSlot != null) CellNowFill else CellFill)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (number > 0) "$number" else "",
                style = MaterialTheme.typography.labelMedium,
                color = TvInkSoft,
                modifier = Modifier.width(34.dp),
            )
            TvChannelLogo(channel, size = 28.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                channel.name,
                style = MaterialTheme.typography.labelLarge,
                color = if (tuned) TvAccent else TvInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (favourite) {
                Spacer(Modifier.width(4.dp))
                Text("★", style = MaterialTheme.typography.labelMedium, color = TvPink)
            }
        }
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
            val width = maxWidth
            fun x(seconds: Long): Dp = width * ((seconds - windowStart).toFloat() / VISIBLE_SECONDS)
            programmes.filter { it.stop > windowStart && it.start < windowEnd }.forEach { p ->
                val focused = focusedSlot?.programme == p
                Cell(
                    title = p.title,
                    left = x(maxOf(p.start, windowStart)),
                    width = x(minOf(p.stop, windowEnd)) - x(maxOf(p.start, windowStart)),
                    focused = focused,
                    onNow = p.start <= now && now < p.stop,
                )
            }
            if (focusedSlot != null && focusedSlot.programme == null) {
                Cell(
                    title = if (programmes.isEmpty()) "No guide for this channel" else "No information",
                    left = x(maxOf(focusedSlot.start, windowStart)),
                    width = x(minOf(focusedSlot.stop, windowEnd)) - x(maxOf(focusedSlot.start, windowStart)),
                    focused = true,
                    onNow = false,
                )
            }
            if (now in windowStart until windowEnd) {
                Box(Modifier.offset(x = x(now)).width(2.dp).fillMaxHeight().background(TvPink.copy(alpha = 0.7f)))
            }
        }
    }
}

@Composable
private fun Cell(title: String, left: Dp, width: Dp, focused: Boolean, onNow: Boolean) {
    if (width <= 0.dp) return
    Box(
        Modifier
            .offset(x = left)
            .width(width)
            .fillMaxHeight()
            .padding(end = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    focused -> TvFocusFill
                    onNow -> CellNowFill
                    else -> CellFill
                }
            )
            .then(if (focused) Modifier.border(2.dp, TvFocusFill, RoundedCornerShape(8.dp)) else Modifier)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = if (focused) TvFocusInk else TvInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
