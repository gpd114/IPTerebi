package com.ipterebi.app.ui.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ipterebi.app.AppContainer
import com.ipterebi.app.ui.DpadTextField
import com.ipterebi.app.ui.focusRing
import com.ipterebi.core.LiveStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(
    container: AppContainer,
    onChannel: (Int) -> Unit,
    onSettings: () -> Unit,
    viewModel: ChannelsViewModel = viewModel(factory = ChannelsViewModel.factory(container)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live TV") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // Click-to-edit under a remote: this sits above the list, so without
            // it every trip up the channels would pass through it and open a
            // keyboard. See DpadTextField.
            DpadTextField(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { fieldModifier ->
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = { Text("Search this list") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = fieldModifier.fillMaxWidth(),
                )
            }

            // Only offered once there is something to carry on with, and hidden
            // while already looking at the recents shelf, where it would sit
            // directly above the same channel.
            state.lastWatched?.takeIf { state.shelf != Shelf.Recent }?.let { channel ->
                ResumeBar(channel = channel, onClick = { onChannel(channel.streamId) })
            }

            ShelfChips(state = state, onSelect = viewModel::selectShelf)

            when {
                state.error != null -> ErrorPanel(
                    message = state.error.orEmpty(),
                    onRetry = viewModel::retry,
                    // Only when the panel named no categories. Asking for every
                    // channel at once is slow and large enough that it has to be
                    // the user's decision, but on a line with no categories it is
                    // the only way in, so it cannot simply be unavailable.
                    onLoadEverything = if (state.offerFullLoad) {
                        { viewModel.selectShelf(Shelf.Panel(null)) }
                    } else null,
                )

                state.busy && state.channels.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                state.visibleChannels.isEmpty() -> EmptyPanel(state)

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.visibleChannels, key = { it.streamId }) { channel ->
                        ChannelRow(
                            channel = channel,
                            starred = state.isFavourite(channel.streamId),
                            onClick = { onChannel(channel.streamId) },
                            onStar = { viewModel.toggleFavourite(channel) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}

/**
 * Favourites and recents first, then the panel's own categories.
 *
 * The two stored shelves appear only once they have something in them. A person
 * who has never starred anything does not need a chip that opens an empty list;
 * the star on each row is what teaches the feature, and the chip arrives the
 * moment it is used.
 */
@Composable
private fun ShelfChips(state: ChannelsUiState, onSelect: (Shelf) -> Unit) {
    if (state.categories.isEmpty() && state.favourites.isEmpty() && state.recents.isEmpty()) return

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.favourites.isNotEmpty()) {
            item {
                FilterChip(
                    modifier = Modifier.focusRing(FilterChipDefaults.shape),
                    selected = state.shelf == Shelf.Favourites,
                    onClick = { onSelect(Shelf.Favourites) },
                    label = { Text("Favourites") },
                )
            }
        }
        if (state.recents.isNotEmpty()) {
            item {
                FilterChip(
                    modifier = Modifier.focusRing(FilterChipDefaults.shape),
                    selected = state.shelf == Shelf.Recent,
                    onClick = { onSelect(Shelf.Recent) },
                    label = { Text("Recent") },
                )
            }
        }
        if (state.categories.isNotEmpty()) {
            item {
                FilterChip(
                    modifier = Modifier.focusRing(FilterChipDefaults.shape),
                    selected = state.shelf == Shelf.Panel(null),
                    onClick = { onSelect(Shelf.Panel(null)) },
                    label = { Text("All") },
                )
            }
            items(state.categories, key = { it.id }) { category ->
                FilterChip(
                    modifier = Modifier.focusRing(FilterChipDefaults.shape),
                    selected = state.shelf == Shelf.Panel(category.id),
                    onClick = { onSelect(Shelf.Panel(category.id)) },
                    label = { Text(category.name) },
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

/**
 * One tap back to whatever was on last.
 *
 * Deliberately not automatic. Opening straight into playback would spend the
 * line's single connection before the user has said what they want, and a line
 * that is already in use elsewhere would greet them with a refusal instead of a
 * channel list.
 */
@Composable
private fun ResumeBar(channel: LiveStream, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .focusRing(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Carry on watching",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ChannelRow(
    channel: LiveStream,
    starred: Boolean,
    onClick: () -> Unit,
    onStar: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRing()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Channel logos are provider-hosted and a good share of them are dead
        // links, so this is decoration and never load-bearing: a failure leaves
        // the placeholder block and the row still reads fine.
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (channel.icon.isNotBlank()) {
                AsyncImage(
                    model = channel.icon,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                )
            }
        }

        Spacer(Modifier.size(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (channel.number > 0) {
                Text(
                    text = "Channel ${channel.number}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        IconButton(onClick = onStar) {
            Icon(
                imageVector = if (starred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = if (starred) {
                    "Remove ${channel.name} from favourites"
                } else {
                    "Add ${channel.name} to favourites"
                },
                tint = if (starred) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/** Says which list is empty, because "no channels" answers a different question. */
@Composable
private fun EmptyPanel(state: ChannelsUiState) {
    val message = when {
        state.query.isNotBlank() -> "Nothing here matches \"${state.query}\"."
        state.shelf == Shelf.Favourites ->
            "No favourites yet. Tap the star beside a channel to keep it here."
        state.shelf == Shelf.Recent -> "Nothing watched yet."
        else -> "No channels in this category."
    }

    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ErrorPanel(
    message: String,
    onRetry: () -> Unit,
    onLoadEverything: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Try again") }

        if (onLoadEverything != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onLoadEverything) { Text("Load every channel") }
            Text(
                "One request for the whole line. On a large one this is several " +
                    "megabytes and takes a while.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
