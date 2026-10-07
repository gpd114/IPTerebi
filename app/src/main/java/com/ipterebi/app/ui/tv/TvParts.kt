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
import com.ipterebi.app.ui.theme.Night
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
 * What the TV screens are drawn with.
 *
 * **These read the palette rather than holding colours of their own**, and
 * that is the whole of why a light theme used to look like two apps stitched
 * together. Home, Settings, Films and Series were drawn from `Night.*`; the
 * screens that make this a television — the channel list, the guide, the
 * recordings — were drawn from a second set of constants that happened to be
 * copies of the dark palette's values. Turn the theme over and half the app
 * followed and half did not. The owner's words: "it just looked disjointed
 * and not the same app as it was not the same colour."
 *
 * So there is one palette now and these are names for parts of it. What was
 * written here about *why* each colour is what it is still holds; it is just
 * said in terms of a role instead of a hex value.
 *
 * The exception is anything genuinely over moving video, which stays dark in
 * both themes because the picture is dark and a white panel over it glares —
 * see [OverVideo], and the multiview grid, which is black behind four
 * pictures. The guide is *not* that exception: it covers the screen with the
 * player shrunk into a corner, so it is a page, and the owner asked for it
 * light along with everything else.
 *
 * Focus is the loudest thing on screen, as in Debritsu's TV app: whatever the
 * remote is on becomes a block of the accent with ink that reads on it. From
 * across a room an outline is not enough.
 */

/** The panel behind lists and banners: the page, with a little of the picture through it. */
internal val TvPanel: Color get() = Night.ground.copy(alpha = 0.9f)
internal val TvInk: Color get() = Night.ink
internal val TvInkSoft: Color get() = Night.inkSoft
/** Where something is, rather than what is focused: the playing channel, the group being zapped. */
internal val TvAccent: Color get() = Night.accent
internal val TvCobalt: Color get() = Night.cobalt
/** Ink on a [TvAccent] fill: the page, so the chosen thing reads as a block. */
internal val TvOnAccent: Color get() = Night.onChosen
internal val TvPink: Color get() = Night.pink
// Focus is the accent, the same block the phone screens fill with: the owner's
// word on the white one it used to be was that it is ugly, and white was two
// things at once anyway — the focus pill here and the ring over there.
//
// So the focus colour answers one question and one only: where is the remote.
// What is chosen — the section you are in, the group you are zapping — is the
// accent instead, as a fill with [TvOnAccent] on it or as ink where there is
// no fill. They used to share cobalt, and on the rail that put two identical
// blue blocks on the screen at once with nothing to say which was which.
internal val TvFocusFill: Color get() = Night.focus
internal val TvFocusInk: Color get() = Night.onFocus

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
