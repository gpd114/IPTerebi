package com.ipterebi.app.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import com.ipterebi.app.ui.channels.tileColour
import com.ipterebi.core.LiveStream
import com.ipterebi.core.channelInitials
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/*
 * What the TV screens are drawn with. Everything here sits over live video, so
 * it is dark whichever theme is chosen — the same reasoning as OverVideo.
 *
 * Focus is the loudest thing on screen, as in Debritsu's TV app: whatever the
 * remote is on becomes a near-white pill with dark text. From across a room an
 * accent-coloured highlight on a dark panel is not enough.
 */

/** The panel behind lists and banners: near-black, a little of the picture through it. */
internal val TvPanel = Color(0xE60B0C11)
internal val TvInk = Color(0xFFF1F3F8)
internal val TvInkSoft = Color(0xFFA3A8B8)
/** Where something is, rather than what is focused: the playing channel, the group being zapped. */
internal val TvAccent = Color(0xFF9DB5FF)
internal val TvCobalt = Color(0xFF2F5FE0)
internal val TvPink = Color(0xFFFF8FA3)
internal val TvFocusFill = Color(0xFFF1F3F8)
internal val TvFocusInk = Color(0xFF141A30)

/**
 * A row in a list the remote moves through: transparent until focused, then
 * the focus pill. [content] is told whether it is focused, to colour itself —
 * one composable whose colours change, not two swapped, so focus is never
 * dropped with a node that left the tree.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun TvRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onFocusChange: (Boolean) -> Unit = {},
    radius: Dp = 12.dp,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val focusChanged by rememberUpdatedState(onFocusChange)
    LaunchedEffect(focused) { focusChanged(focused) }
    val shape = RoundedCornerShape(radius)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = TvInk,
            focusedContainerColor = TvFocusFill,
            focusedContentColor = TvFocusInk,
            pressedContainerColor = TvFocusFill,
            pressedContentColor = TvFocusInk,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        interactionSource = source,
    ) { content(focused) }
}

/**
 * A button over the picture: cobalt, or faint white when [primary] is false,
 * and the focus pill when the remote is on it. The phone's buttons mark focus
 * with a ring in the theme's accent, which in the light theme is dark blue —
 * invisible on a dark panel across a room.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun TvButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(26.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) TvCobalt else Color(0x26FFFFFF),
            contentColor = Color.White,
            focusedContainerColor = TvFocusFill,
            focusedContentColor = TvFocusInk,
            pressedContainerColor = TvFocusFill,
            pressedContentColor = TvFocusInk,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        interactionSource = source,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (focused) TvFocusInk else Color.White,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 26.dp),
        )
    }
}

/** A channel's logo, or its initials on a tile when it has none — as on the phone. */
@Composable
internal fun TvChannelLogo(channel: LiveStream?, size: Dp = 44.dp) {
    val name = channel?.name.orEmpty()
    val icon = channel?.icon.orEmpty()
    var failed by remember(icon) { mutableStateOf(false) }
    val showLogo = icon.isNotBlank() && !failed
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(if (showLogo) Color(0x1FFFFFFF) else tileColour(name)),
        contentAlignment = Alignment.Center,
    ) {
        if (showLogo) {
            AsyncImage(
                model = icon,
                contentDescription = null,
                onError = { failed = true },
                modifier = Modifier.size(size * 0.86f),
            )
        } else {
            Text(
                channelInitials(name),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
            )
        }
    }
}

/** The time of day, as a set-top box shows it, moving on the minute. */
@Composable
internal fun rememberClock(): String {
    val format = remember { DateTimeFormatter.ofPattern("HH:mm") }
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
            now = LocalTime.now()
        }
    }
    return now.format(format)
}
