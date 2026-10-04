package com.ipterebi.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ipterebi.app.R
import com.ipterebi.app.ui.theme.Corners
import com.ipterebi.app.ui.theme.Night
import com.ipterebi.app.ui.theme.tabular

/*
 * The pieces every screen is built from, on the pattern of Debritsu's
 * Components.kt: flat fills by role, no gradients or sheen. See Night for the
 * roles. Anything pressable here is focusable and ringed, for a remote.
 */

/** A card: a channel row, the carry-on card. Flat, one step up from the page. */
fun Modifier.nightCard(shape: Shape = Corners.card, colour: Color = Night.veil): Modifier =
    clip(shape).background(colour)

/**
 * A panel that groups related things — a settings section, a message over the
 * video. Content in a column with room between rows.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    spacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(Corners.panel)
            .background(Night.veil)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** The main button — Play here, Try again, Sign in — solid cobalt. */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 52.dp,
    /** The fill; the theme's accent unless the button sits somewhere the theme does not reach. */
    fill: Color = Night.cobalt,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = Corners.control
    Row(
        modifier
            .height(height)
            .clip(shape)
            .background(if (enabled) fill else Night.quiet)
            .focusFill(shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (enabled) Color.White else Night.inkSoft) {
            content()
        }
    }
}

/**
 * A button that sits beside the main one without competing with it — Apply,
 * Sign out. White-tinted glass with the label in [colour].
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    colour: Color = Night.accent,
) {
    val shape = Corners.control
    Box(
        modifier
            .height(height)
            .clip(shape)
            .background(Night.glass)
            .focusFill(shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
            color = if (enabled) colour else Night.inkSoft,
        )
    }
}

/** A rounded-square button for an icon: settings, back. */
@Composable
fun SquareIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
) {
    val shape = Corners.control
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(Night.glass)
            .focusFill(shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Night.glassIcon, modifier = Modifier.size(21.dp))
    }
}

/** A small rounded label: a channel number, a status. Quiet unless [fill] is given. */
@Composable
fun QuietPill(text: String, modifier: Modifier = Modifier, fill: Color = Night.quiet, colour: Color = Night.quietText) {
    Box(
        modifier
            .clip(Corners.tag)
            .background(fill)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall.tabular(), color = colour, maxLines = 1)
    }
}

/**
 * One choice out of a few, as a row of equal-width pills — the chosen one solid
 * cobalt, the rest quiet. In place of Material's filter chips, whose outlined
 * look belonged to a different app.
 */
@Composable
fun <T> ChoiceRow(
    options: List<Pair<T, String>>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val on = isSelected(value)
            val shape = Corners.control
            Box(
                Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(shape)
                    .background(if (on) Night.cobalt else Night.quiet)
                    .focusFill(shape)
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                    color = if (on) Color.White else Night.quietText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The top of a section screen: Terebi-kun, the section's name set large, and
 * the settings button. No app-bar strip — the page runs up behind it.
 */
@Composable
fun SectionTopBar(
    title: String,
    onSettings: () -> Unit,
    /** Buttons before the settings one — Live TV's guide. */
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp + overscanInset().second,
                bottom = 8.dp,
            ),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_mascot),
            contentDescription = null,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.displaySmall,
            color = Night.ink,
            modifier = Modifier.weight(1f),
        )
        actions()
        // Only where there is no rail to hold it. On a television the rail
        // carries Settings at its foot, and a second way in at the far corner
        // of the screen is a trip a remote should not have to make.
        if (!railShowing()) {
            SquareIconButton(Icons.Filled.Settings, "Settings", onSettings)
        }
    }
}

/** The top of a secondary screen: a back button and the screen's name. */
@Composable
fun ScreenTopBar(title: String, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp + overscanInset().second,
                bottom = 8.dp,
            ),
    ) {
        SquareIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
        Spacer(Modifier.width(14.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            color = Night.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The search field's shape: a rounded well. */
val SearchFieldShape = Corners.control

/** Text fields: a well a step below the page, with a faint rim that turns cobalt while typing. */
@Composable
fun fieldColours(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = Night.field,
    focusedContainerColor = Night.field,
    unfocusedBorderColor = Night.edge,
    focusedBorderColor = Night.cobalt,
    cursorColor = Night.accent,
    focusedLabelColor = Night.accent,
    unfocusedLabelColor = Night.inkSoft,
    unfocusedPlaceholderColor = Night.inkSoft,
    focusedPlaceholderColor = Night.inkSoft,
    unfocusedLeadingIconColor = Night.inkSoft,
    focusedLeadingIconColor = Night.ink,
    unfocusedTrailingIconColor = Night.inkSoft,
    focusedTrailingIconColor = Night.ink,
    focusedTextColor = Night.ink,
    unfocusedTextColor = Night.ink,
    unfocusedSupportingTextColor = Night.inkSoft,
    focusedSupportingTextColor = Night.inkSoft,
)

/**
 * How far in from the edges a television needs its content.
 *
 * Televisions crop what they are sent — usually 2 to 5% a side — and the
 * app had no margin at all. On a 1920x1080 box the navigation rail's
 * longest label, "Recordings", sat **13 px** from the physical left edge and
 * a screen title sat **29 px** from the top, measured identically on the
 * emulator and on the owner's own box: that is how it was shown to be the
 * television cropping rather than the layout. The R disappeared.
 *
 * 24dp and 12dp are 2.5% of a 960x540dp screen, which clears the usual
 * case without stranding everything in the middle.
 *
 * Only on a television. A tablet shows the same rail and the same bars at
 * 600dp and wider, loses nothing off its edges, and should not carry a
 * margin for a fault it does not have — so this asks the system what it is
 * rather than measuring the screen, because a tablet held sideways is the
 * same shape as a television.
 */
@Composable
fun overscanInset(): Pair<Dp, Dp> =
    if (isTelevision(LocalContext.current)) OVERSCAN_SIDE to OVERSCAN_TOP else 0.dp to 0.dp

private val OVERSCAN_SIDE = 24.dp
private val OVERSCAN_TOP = 12.dp

/**
 * Whether this is a television, which crops the edges of what it is sent.
 *
 * Leanback is the question the launcher itself asks, and it is the right
 * one: screen size would call a sideways tablet a television.
 */
fun isTelevision(context: Context): Boolean =
    context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
