package com.ipterebi.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ipterebi.core.cleanListName
import com.ipterebi.core.ListedItem
import com.ipterebi.app.ui.CategoryShelf
import com.ipterebi.app.ui.DpadTextField
import com.ipterebi.app.ui.PrimaryButton
import com.ipterebi.app.ui.QuietPill
import com.ipterebi.app.ui.SecondaryButton
import com.ipterebi.app.ui.SearchFieldShape
import com.ipterebi.app.ui.SectionTopBar
import com.ipterebi.app.ui.ShelfChip
import com.ipterebi.app.ui.focusFill
import com.ipterebi.app.ui.fieldColours
import com.ipterebi.app.ui.theme.Corners
import com.ipterebi.app.ui.theme.Night
import java.util.Locale

/**
 * A library of posters: films, or series.
 *
 * Search along the top, categories under it, and a poster grid below.
 * [key] must be unique across the list — pass an id that has been through the
 * core hygiene functions, which is what guarantees it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : Any> LibraryScreen(
    title: String,
    viewModel: LibraryViewModel<T>,
    key: (T) -> Any,
    onSettings: () -> Unit,
    /** Opening something from one of the viewer's own lists, which has only what was stored. */
    onOpenListed: (ListedItem) -> Unit,
    /** Drawn under a poster that will not load — and every list entry, which has no rating either. */
    placeholder: ImageVector,
    /**
     * [onLongPress] is handed in rather than built by the caller: holding a
     * poster is how something is put in a list, and that has to mean the same
     * on both screens.
     */
    tile: @Composable (T, onLongPress: () -> Unit) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Held a poster: which one, and which sheet it wants. Adding comes from
    // the panel's own grid, taking out from inside a list, and they are two
    // different questions asked the same way.
    var adding by remember { mutableStateOf<T?>(null) }
    var removing by remember { mutableStateOf<ListedItem?>(null) }

    Scaffold(
        topBar = { SectionTopBar(title = title, onSettings = onSettings) },
        // The page is drawn once, under the NavHost.
        containerColor = Color.Transparent,
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // Click-to-edit under a remote, for the same reason as the channel
            // list's search box. See DpadTextField.
            DpadTextField(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { fieldModifier ->
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = { Text("Search this category") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = SearchFieldShape,
                    colors = fieldColours(),
                    modifier = fieldModifier.fillMaxWidth(),
                )
            }

            if (state.categories.isNotEmpty() || state.lists.isNotEmpty()) {
                CategoryShelf(
                    buildList {
                        add(
                            ShelfChip(
                                key = "all",
                                label = "All",
                                selected = state.selectedCategoryId == null && state.selectedList == null,
                                onClick = { viewModel.selectCategory(null) },
                            )
                        )
                        // The viewer's own lists first and in pink, as
                        // Favourites is on the channel list: they are a
                        // different kind of thing from the provider's filing,
                        // and there are a handful of them against its dozens.
                        state.lists.forEach { own ->
                            add(
                                ShelfChip(
                                    key = "l:" + own.name,
                                    label = own.name,
                                    selected = state.selectedList == own.name,
                                    onClick = { viewModel.selectList(own.name) },
                                    special = true,
                                )
                            )
                        }
                        state.categories.forEach { category ->
                            add(
                                ShelfChip(
                                    key = "c:${category.id}",
                                    label = category.name,
                                    selected = state.selectedCategoryId == category.id,
                                    onClick = { viewModel.selectCategory(category.id) },
                                )
                            )
                        }
                    }
                )
            }

            when {
                // One of the viewer's own lists. Nothing is fetched and
                // nothing can fail: everything it needs was stored when each
                // entry was put in, which is what lets a list open instantly
                // on a cold start with the panel not yet spoken to.
                state.selectedList != null -> {
                    val own = state.lists.firstOrNull { it.name == state.selectedList }
                    val entries = own?.items.orEmpty()
                    if (entries.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "Nothing in \"${state.selectedList}\" yet. " +
                                    "Hold a poster to put it in a list.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 112.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(entries, key = { it.kind.name + ":" + it.id }) { entry ->
                                PosterTile(
                                    title = entry.name,
                                    image = entry.poster,
                                    placeholder = placeholder,
                                    ratingOutOfTen = null,
                                    onClick = { onOpenListed(entry) },
                                    onLongPress = { removing = entry },
                                )
                            }
                        }
                    }
                }

                state.error != null -> ErrorPanel(
                    message = state.error.orEmpty(),
                    onRetry = viewModel::retry,
                    fullLoadLabel = "Load every ${viewModel.noun}",
                    onLoadEverything = if (state.offerFullLoad) {
                        { viewModel.selectCategory(null) }
                    } else null,
                )

                state.busy && state.items.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                state.visible.isEmpty() ->
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (state.query.isBlank()) "No ${viewModel.nouns} in this category."
                            else "Nothing here matches \"${state.query}\".",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }

                // Adaptive rather than a fixed column count: two across on a
                // phone in portrait, six or seven on a tablet in landscape, from
                // one rule.
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 112.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.visible, key = key) { item -> tile(item) { adding = item } }
                }
            }
        }
    }

    // Holding a poster in the panel's own grid: which of the viewer's lists
    // should it go in, and is there a new one to make?
    adding?.let { held ->
        val entry = viewModel.listedForm(held)
        ModalBottomSheet(onDismissRequest = { adding = null }, containerColor = Night.veil) {
            Column(
                Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(entry.name, style = MaterialTheme.typography.titleMedium, color = Night.ink, maxLines = 2)
                Text(
                    if (state.allLists.isEmpty()) "Make a list to put it in" else "Put it in a list",
                    style = MaterialTheme.typography.labelLarge,
                    color = Night.accent,
                )
                state.allLists.forEach { own ->
                    val inIt = own.holds(entry.kind, entry.id)
                    SecondaryButton(
                        text = if (inIt) own.name + "  ·  take out" else own.name,
                        onClick = {
                            if (inIt) viewModel.removeFromList(own.name, entry.id)
                            else viewModel.addToList(own.name, held)
                            adding = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // Click-to-edit under a remote, as every field in this app is.
                var newName by remember { mutableStateOf("") }
                DpadTextField { fieldModifier ->
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text("New list") },
                        singleLine = true,
                        shape = SearchFieldShape,
                        colors = fieldColours(),
                        modifier = fieldModifier.fillMaxWidth(),
                    )
                }
                PrimaryButton(
                    onClick = {
                        viewModel.addToList(newName, held)
                        adding = null
                    },
                    enabled = cleanListName(newName) != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Make the list and add", style = MaterialTheme.typography.labelLarge) }
            }
        }
    }

    // Holding a poster *inside* a list: the only thing to ask is whether it
    // should stay there.
    removing?.let { entry ->
        val from = state.selectedList
        ModalBottomSheet(onDismissRequest = { removing = null }, containerColor = Night.veil) {
            Column(
                Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(entry.name, style = MaterialTheme.typography.titleMedium, color = Night.ink, maxLines = 2)
                PrimaryButton(
                    onClick = {
                        if (from != null) viewModel.removeFromList(from, entry.id)
                        removing = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Take out of \"$from\"", style = MaterialTheme.typography.labelLarge, maxLines = 1) }
            }
        }
    }
}

/**
 * A poster, a title, and a rating when there is one.
 *
 * Posters are provider-hosted and frequently dead, so the placeholder icon is
 * drawn first and the image over it: a failure leaves the icon, and the grid
 * keeps its rhythm.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterTile(
    title: String,
    image: String,
    placeholder: ImageVector,
    ratingOutOfTen: Double?,
    onClick: () -> Unit,
    /**
     * Holding a poster is how it goes in or out of one of the viewer's own
     * lists. A long press rather than a button on every tile: the grid is
     * mostly picture, and a corner button on each would be a hundred little
     * targets nobody asked for. On a remote it is hold-OK, which is already
     * what holding means on a channel.
     */
    onLongPress: (() -> Unit)? = null,
) {
    // Rounded, with a faint rim so a dark cover still has an edge against the
    // page — Debritsu's PosterArt, which every cover there is drawn with.
    //
    // The artwork is *fitted*, not cropped. The tile is a fixed 2:3 and a
    // provider uploads whatever it has: a square cover lost its top and
    // bottom and a landscape one lost most of its width, which is what the
    // owner saw. Nothing here can reshape a picture, so the tile keeps its
    // shape and the cover keeps all of itself, with the tile colour behind
    // whatever is left over. The fake panel has one of each shape now — with
    // only the two 2:3 ones it had before, this could never have shown.
    val poster = Corners.card
    Column(
        modifier = Modifier
            .focusFill(poster)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            )
            // Inside the fill: a poster paints over a background, so without
            // this focus would show only behind the title under it.
            .padding(3.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(poster)
                .background(Night.veil)
                .border(1.dp, Night.hairline, poster),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                placeholder,
                contentDescription = null,
                tint = Night.inkSoft.copy(alpha = 0.4f),
            )
            if (image.isNotBlank()) {
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            ratingOutOfTen?.let { rating ->
                // A translucent cobalt pill, so it reads on any poster without
                // hiding it.
                QuietPill(
                    text = "★ " + String.format(Locale.ROOT, "%.1f", rating),
                    fill = Night.badge,
                    colour = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(7.dp),
                )
            }
        }

        Spacer(Modifier.height(7.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ErrorPanel(
    message: String,
    onRetry: () -> Unit,
    fullLoadLabel: String = "",
    onLoadEverything: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton(onClick = onRetry) { Text("Try again", style = MaterialTheme.typography.labelLarge) }
        if (onLoadEverything != null) {
            Spacer(Modifier.height(8.dp))
            SecondaryButton(text = fullLoadLabel, onClick = onLoadEverything)
            Text(
                "One request for the whole library. On a large one this is several " +
                    "megabytes and takes a while.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
