package com.ipterebi.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ipterebi.app.ui.theme.Corners
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
 * Categories as chips: wrapped onto as many lines as they need on a screen
 * with the height for it, and one sideways row on a screen without.
 *
 * They used to be one row that scrolled sideways everywhere, and a real line
 * has dozens of categories on every tab — finding the one wanted meant
 * flicking along a long strip that showed four or five at a time. Wrapped, a
 * phone shows a dozen or more at once and a tablet most of them.
 *
 * **On a television the wrapped shelf ate the screen.** A 1080p box is 540dp
 * tall: the top bar, the search field and four rows of chips left about a
 * third of the height for the posters, so the row under it was cut off by the
 * bottom edge — and it stayed cut off when the remote moved into it, because
 * the shelf is pinned and only the grid scrolls. The owner reported it as
 * posters not being fully visible, which is exactly what it looked like. So a
 * short screen gets a single row that scrolls sideways, which costs it the
 * overview and gives back two thirds of the screen. Down from a chip then
 * lands in the grid rather than on more chips.
 *
 * Short means under [SHORT_SCREEN_HEIGHT]: a television, and a phone held
 * sideways, which has the same problem for the same reason. A phone upright
 * is around 800dp and a tablet more, so neither changes.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun CategoryShelf(chips: List<ShelfChip>, modifier: Modifier = Modifier) {
    if (chips.isEmpty()) return

    val wrapped = LocalConfiguration.current.screenHeightDp >= SHORT_SCREEN_HEIGHT
    val scroll = rememberScrollState()
    Box(modifier.padding(horizontal = 16.dp)) {
        if (wrapped) {
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
                chips.forEach { chip -> key(chip.key) { Chip(chip) } }
            }
        } else {
            // One row, sideways, on a screen too short to spare four. A focus
            // group so the remote goes in and out of it as one thing rather
            // than stepping chip by chip on its way down to the grid.
            LazyRow(
                modifier = Modifier.fillMaxWidth().focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(chips, key = { it.key }) { chip -> Chip(chip) }
            }
        }
        if (wrapped && scroll.canScrollForward) {
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

/**
 * One chip, drawn the same whichever way the shelf is arranged. The chosen one
 * is brought into view when the screen comes back, which is how a shelf that
 * has been scrolled — sideways or down — shows where you are.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Chip(chip: ShelfChip) {
    val bringIntoView = remember { BringIntoViewRequester() }
    // Focus is given to the chip as a *colour* rather than painted behind it.
    // Material draws the container itself, inside the chip, so a fill put down
    // by the modifier is covered by it — and on the chip you are in, which is
    // already the accent, the fill was the same colour anyway. Both go away if
    // the container is simply told what to be.
    val focus = rememberFocusFill(if (chip.selected) Night.cobalt else Night.quiet)
    // Debritsu's pattern: quiet white-tinted pills on the flat page, the one
    // you are in solid cobalt, like the tab you are on. The app's own shelves
    // are named in pink.
    FilterChip(
        modifier = Modifier
            .bringIntoViewRequester(bringIntoView)
            .then(focus.watch),
        selected = chip.selected,
        onClick = chip.onClick,
        label = {
            Text(
                chip.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (chip.selected) FontWeight.SemiBold else FontWeight.Medium,
            )
        },
        shape = ChipShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = focus.fill,
            labelColor = focus.ink(if (chip.special) Night.pink else Night.quietText),
            selectedContainerColor = focus.fill,
            selectedLabelColor = focus.ink(Color.White),
        ),
        border = null,
        elevation = FilterChipDefaults.filterChipElevation(elevation = 0.dp),
    )
    if (chip.selected) {
        LaunchedEffect(Unit) { bringIntoView.bringIntoView() }
    }
}

/**
 * A screen short enough that there is no height to spend: a television, and a
 * phone held sideways. A phone upright is around 800dp and a tablet more, so
 * neither counts as short.
 *
 * Two things read it. The shelf wraps onto four rows only above it, and the
 * navigation rail centres its items only below it — both because a short
 * screen and a tall one want opposite things, and both drawing the line in
 * the same place because it is the same line.
 */
const val SHORT_SCREEN_HEIGHT = 560

/** How tall a category shelf may grow before it scrolls. */
private const val SHELF_ROWS = 4

/** How much of the row after the last whole one shows, to say there is more. */
private val PEEK = 22.dp

private val ChipShape = Corners.control
