package com.ipterebi.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ipterebi.app.ui.theme.Night

/** One chip on a [CategoryShelf]. [key] must be unique on the shelf. */
class ShelfChip(
    val key: String,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    /** The app's own shelves — favourites, recents — rather than the panel's. */
    val special: Boolean = false,
)

/**
 * Categories as chips that wrap onto as many lines as they need, in a box at
 * most [SHELF_ROWS] rows tall that scrolls downwards.
 *
 * They used to be one row that scrolled sideways, and a real line has dozens of
 * categories on every tab — finding the one wanted meant flicking along a long
 * strip that showed four or five at a time. Wrapped, a phone shows a dozen or
 * more at once and a tablet most of them. The box is capped so the list under
 * it keeps the screen; a fade along its bottom edge says there is more, and
 * whichever chip is selected is scrolled into view when the screen comes back.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun CategoryShelf(chips: List<ShelfChip>, modifier: Modifier = Modifier) {
    if (chips.isEmpty()) return

    val scroll = rememberScrollState()
    Box(modifier.padding(horizontal = 16.dp)) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                // Each chip takes a 48dp touch target, so this is SHELF_ROWS
                // rows of them and the top of the next, under the fade. A box
                // that ends cleanly on a whole row looks like all there is —
                // measured: the fade fell in the gap between rows and nobody
                // would have known to scroll.
                .heightIn(max = 48.dp * SHELF_ROWS + PEEK)
                .verticalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            chips.forEach { chip ->
                key(chip.key) {
                    val bringIntoView = remember { BringIntoViewRequester() }
                    // Debritsu's pattern: quiet white-tinted pills on the flat
                    // page, the one you are in solid cobalt, like the tab you are
                    // on. The app's own shelves are named in pink.
                    FilterChip(
                        modifier = Modifier
                            .bringIntoViewRequester(bringIntoView)
                            .focusRing(ChipShape),
                        selected = chip.selected,
                        onClick = chip.onClick,
                        label = {
                            Text(
                                chip.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        shape = ChipShape,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Night.quiet,
                            labelColor = if (chip.special) Night.pink else Night.quietText,
                            selectedContainerColor = Night.cobalt,
                            selectedLabelColor = Color.White,
                        ),
                        border = null,
                        elevation = FilterChipDefaults.filterChipElevation(elevation = 0.dp),
                    )
                    if (chip.selected) {
                        LaunchedEffect(Unit) { bringIntoView.bringIntoView() }
                    }
                }
            }
        }
        if (scroll.canScrollForward) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(PEEK + 8.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                        )
                    )
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

/** How tall a category shelf may grow before it scrolls. */
private const val SHELF_ROWS = 4

/** How much of the row after the last whole one shows, to say there is more. */
private val PEEK = 22.dp

private val ChipShape = RoundedCornerShape(12.dp)
