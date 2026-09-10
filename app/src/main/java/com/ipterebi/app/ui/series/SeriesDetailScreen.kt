package com.ipterebi.app.ui.series

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.ipterebi.app.ui.library.ErrorPanel
import com.ipterebi.core.EpisodeEntry
import com.ipterebi.core.SeriesDetail
import com.ipterebi.core.displayTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    container: AppContainer,
    seriesId: Int,
    onEpisode: (EpisodeEntry) -> Unit,
    onBack: () -> Unit,
    viewModel: SeriesDetailViewModel = viewModel(
        key = "series-$seriesId",
        factory = SeriesDetailViewModel.factory(container, seriesId),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to series")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            val detail = state.detail
            when {
                state.error != null -> ErrorPanel(message = state.error.orEmpty(), onRetry = viewModel::retry)

                detail == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                else -> SeriesContent(
                    detail = detail,
                    selectedSeason = state.selectedSeason,
                    onSeason = viewModel::selectSeason,
                    onEpisode = onEpisode,
                )
            }
        }
    }
}

@Composable
private fun SeriesContent(
    detail: SeriesDetail,
    selectedSeason: Int,
    onSeason: (Int) -> Unit,
    onEpisode: (EpisodeEntry) -> Unit,
) {
    val season = detail.seasons.getOrNull(selectedSeason)

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { Header(detail) }

        // One chip per season, and none at all for a series with only one:
        // a single chip offers a choice that does not exist.
        if (detail.seasons.size > 1) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    itemsIndexed(detail.seasons, key = { _, it -> it.number }) { index, entry ->
                        FilterChip(
                            selected = index == selectedSeason,
                            onClick = { onSeason(index) },
                            label = { Text(entry.name) },
                        )
                    }
                }
            }
        }

        if (season == null) {
            item {
                Text(
                    "The panel lists no episodes for this series.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }
        } else {
            // Keyed on the episode id, which the parser has made present and
            // unique within the season.
            items(season.episodes, key = { it.episode.id }) { entry ->
                EpisodeRow(entry = entry, seriesName = detail.name, onClick = { onEpisode(entry) })
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}

@Composable
private fun Header(detail: SeriesDetail) {
    var expanded by remember { mutableStateOf(false) }

    Row(modifier = Modifier.padding(16.dp)) {
        if (detail.cover.isNotBlank()) {
            AsyncImage(
                model = detail.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(96.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${detail.seasons.size} ${if (detail.seasons.size == 1) "season" else "seasons"} · " +
                    "${detail.episodeCount} ${if (detail.episodeCount == 1) "episode" else "episodes"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (detail.plot.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                // Plots run to paragraphs. Four lines, and a tap for the rest,
                // keeps the episodes on the first screen.
                Text(
                    text = detail.plot,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { expanded = !expanded },
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(entry: EpisodeEntry, seriesName: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A still from the episode when the panel has one; a play mark when not.
        Box(
            modifier = Modifier
                .width(112.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(28.dp),
            )
            if (entry.details.image.isNotBlank()) {
                AsyncImage(
                    model = entry.details.image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.displayTitle(seriesName),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOf(entry.code, entry.durationLabel).filter { it.isNotBlank() }.joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
