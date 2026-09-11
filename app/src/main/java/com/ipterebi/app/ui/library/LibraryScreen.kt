package com.ipterebi.app.ui.library

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.ipterebi.app.ui.CategoryShelf
import com.ipterebi.app.ui.DpadTextField
import com.ipterebi.app.ui.SearchFieldShape
import com.ipterebi.app.ui.SectionTopBar
import com.ipterebi.app.ui.ShelfChip
import com.ipterebi.app.ui.focusRing
import com.ipterebi.app.ui.searchFieldColours
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
    tile: @Composable (T) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { SectionTopBar(title = title, onSettings = onSettings) },
        // The glow behind the section screens is drawn once, under the NavHost.
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
                    colors = searchFieldColours(),
                    modifier = fieldModifier.fillMaxWidth(),
                )
            }

            if (state.categories.isNotEmpty()) {
                CategoryShelf(
                    buildList {
                        add(
                            ShelfChip(
                                key = "all",
                                label = "All",
                                selected = state.selectedCategoryId == null,
                                onClick = { viewModel.selectCategory(null) },
                            )
                        )
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
                    items(state.visible, key = key) { item -> tile(item) }
                }
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
@Composable
fun PosterTile(
    title: String,
    image: String,
    placeholder: ImageVector,
    ratingOutOfTen: Double?,
    onClick: () -> Unit,
) {
    val poster = RoundedCornerShape(14.dp)
    Column(modifier = Modifier.focusRing(poster).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .shadow(8.dp, poster)
                .clip(poster)
                .background(Night.card),
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
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // The glare across the icon's screen, laid over every poster.
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            0f to Color.White.copy(alpha = 0.16f),
                            0.45f to Color.Transparent,
                        )
                    )
            )
            ratingOutOfTen?.let { rating ->
                Text(
                    text = "★ " + String.format(Locale.ROOT, "%.1f", rating),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Night.onYellow,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Night.yellow)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
        }

        Spacer(Modifier.height(7.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
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
        Button(onClick = onRetry) { Text("Try again") }
        if (onLoadEverything != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onLoadEverything) { Text(fullLoadLabel) }
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
