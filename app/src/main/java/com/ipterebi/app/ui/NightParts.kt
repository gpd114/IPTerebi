package com.ipterebi.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ipterebi.app.R
import com.ipterebi.app.ui.theme.Night

/**
 * A Night-set card: filled, rounded, with the one-pixel gloss along its top
 * edge that the set in the icon has. Put [focusRing] and clickable after it.
 */
fun Modifier.nightCard(shape: Shape = RoundedCornerShape(16.dp), colour: Color = Night.card): Modifier =
    clip(shape)
        .background(colour)
        .drawWithContent {
            drawContent()
            drawLine(Night.gloss, Offset(12.dp.toPx(), 0.5f), Offset(size.width - 12.dp.toPx(), 0.5f), 1.dp.toPx())
        }

/**
 * The top bar of a section screen: Terebi-kun, the section's name, and the
 * settings cog in a round button. Transparent, so the screen's cobalt glow runs
 * up behind it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionTopBar(title: String, onSettings: () -> Unit) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_mascot),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.size(10.dp))
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
        },
        actions = {
            IconButton(
                onClick = onSettings,
                colors = IconButtonDefaults.iconButtonColors(containerColor = Night.button),
                modifier = Modifier
                    .padding(end = 4.dp)
                    .focusRing(CircleShape),
            ) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Night.onButton)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
    )
}

/**
 * The search field's look: frosted white with a white outline — white, like
 * the buttons, so it does not read as more blue on blue, but see-through so a
 * box across the top of a dark screen does not glare. Cobalt while typed in.
 */
val SearchFieldShape = RoundedCornerShape(26.dp)

@Composable
fun searchFieldColours(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = Color.White.copy(alpha = 0.10f),
    focusedContainerColor = Color.White.copy(alpha = 0.10f),
    unfocusedBorderColor = Color.White.copy(alpha = 0.45f),
    focusedBorderColor = Night.cobalt,
    unfocusedPlaceholderColor = Night.chipInk,
    focusedPlaceholderColor = Night.inkSoft,
    unfocusedLeadingIconColor = Night.chipInk,
    focusedLeadingIconColor = Night.ink,
)
