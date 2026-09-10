package com.ipterebi.app.ui.films

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ipterebi.app.AppContainer
import com.ipterebi.core.VodStream
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilmsScreen(
    container: AppContainer,
    onFilm: (VodStream) -> Unit,
    onSettings: () -> Unit,
    viewModel: FilmsViewModel = viewModel(factory = FilmsViewModel.factory(container)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Films") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Search this category") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (state.categories.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = state.selectedCategoryId == null,
                            onClick = { viewModel.selectCategory(null) },
                            label = { Text("All") },
                        )
                    }
                    items(state.categories, key = { it.id }) { category ->
                        FilterChip(
                            selected = state.selectedCategoryId == category.id,
                            onClick = { viewModel.selectCategory(category.id) },
                            label = { Text(category.name) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            when {
                state.error != null -> ErrorPanel(
                    message = state.error.orEmpty(),
                    onRetry = viewModel::retry,
                    onLoadEverything = if (state.offerFullLoad) {
                        { viewModel.selectCategory(null) }
                    } else null,
                )

                state.busy && state.films.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                state.visibleFilms.isEmpty() ->
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (state.query.isBlank()) "No films in this category."
                            else "Nothing here matches \"${state.query}\".",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }

                // Adaptive rather than a fixed column count: two across on a
                // phone in portrait, six or seven on a tablet in landscape, from
                // one rule. Keyed on streamId, which playableFilms() has already
                // made present and unique.
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 112.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.visibleFilms, key = { it.streamId }) { film ->
                        FilmTile(film = film, onClick = { onFilm(film) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FilmTile(film: VodStream, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        // A poster, when the provider has one that loads. Same rule as channel
        // logos: provider-hosted, frequently dead, never load-bearing. The
        // placeholder keeps the grid's rhythm when it fails.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Movie,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            if (film.icon.isNotBlank()) {
                AsyncImage(
                    model = film.icon,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = film.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        film.ratingOutOfTen?.let { rating ->
            Text(
                text = "★ " + String.format(Locale.ROOT, "%.1f", rating),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorPanel(
    message: String,
    onRetry: () -> Unit,
    onLoadEverything: (() -> Unit)?,
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
            TextButton(onClick = onLoadEverything) { Text("Load every film") }
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
