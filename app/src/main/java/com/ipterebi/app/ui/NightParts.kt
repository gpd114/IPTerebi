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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ipterebi.app.R
import com.ipterebi.app.ui.theme.Night

/*
 * The pieces every screen is built from, on the pattern of Debritsu's
 * Components.kt: flat fills by role, no gradients or sheen. See Night for the
 * roles. Anything pressable here is focusable and ringed, for a remote.
 */

/** A card: a channel row, the carry-on card. Flat, one step up from the page. */
fun Modifier.nightCard(shape: Shape = RoundedCornerShape(18.dp), colour: Color = Night.veil): Modifier =
    clip(shape).background(colour)

/**
 * A panel that groups related things — a settings section, a message over the
 * video. Content in a column with room between rows.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: Dp = 18.dp,
    spacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
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
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(height / 2)
    Row(
        modifier
            .height(height)
            .clip(shape)
            .background(if (enabled) Night.cobalt else Night.quiet)
            .focusRing(shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp),
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
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier
            .height(height)
            .clip(shape)
            .background(Night.glass)
            .focusRing(shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp),
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
    val shape = RoundedCornerShape(size * 0.34f)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(Night.glass)
            .focusRing(shape)
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
            .clip(RoundedCornerShape(10.dp))
            .background(fill)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = colour, maxLines = 1)
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
            val shape = RoundedCornerShape(14.dp)
            Box(
                Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(shape)
                    .background(if (on) Night.cobalt else Night.quiet)
                    .focusRing(shape)
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
fun SectionTopBar(title: String, onSettings: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_mascot),
            contentDescription = null,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 28.sp),
            color = Night.ink,
            modifier = Modifier.weight(1f),
        )
        SquareIconButton(Icons.Filled.Settings, "Settings", onSettings)
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
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
    ) {
        SquareIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
        Spacer(Modifier.width(14.dp))
        Text(
            title,
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 26.sp),
            color = Night.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The search field's shape: a rounded well. */
val SearchFieldShape = RoundedCornerShape(24.dp)

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
