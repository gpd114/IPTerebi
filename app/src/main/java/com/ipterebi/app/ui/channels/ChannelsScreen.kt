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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ipterebi.app.AppContainer
import com.ipterebi.app.ui.CategoryShelf
import com.ipterebi.app.ui.DpadTextField
import com.ipterebi.app.ui.PrimaryButton
import com.ipterebi.app.ui.QuietPill
import com.ipterebi.app.ui.SearchFieldShape
import com.ipterebi.app.ui.SecondaryButton
import com.ipterebi.app.ui.SectionTopBar
import com.ipterebi.app.ui.ShelfChip
import com.ipterebi.app.ui.SquareIconButton
import com.ipterebi.app.ui.fieldColours
import com.ipterebi.app.ui.focusRing
import com.ipterebi.app.ui.nightCard
import com.ipterebi.app.ui.theme.Night
import com.ipterebi.core.LiveStream
import com.ipterebi.core.XmltvProgramme
import com.ipterebi.core.channelInitials
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(
    container: AppContainer,
    onChannel: (Int) -> Unit,
    onSettings: () -> Unit,
    /** The TV guide, for the channels on screen. */
    onGuide: () -> Unit,
    viewModel: ChannelsViewModel = viewModel(factory = ChannelsViewModel.factory(container)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val guideVersion by viewModel.guideVersion.collectAsStateWithLifecycle()
    // The minute, so what is on moves on while the list is open.
    var minute by remember { mutableLongStateOf(System.currentTimeMillis() / 60_000) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            minute = System.currentTimeMillis() / 60_000
        }
    }

    Scaffold(
        topBar = {
            SectionTopBar(title = "Live TV", onSettings = onSettings) {
                SquareIconButton(Icons.Filled.GridView, "TV guide", onGuide)
                Spacer(Modifier.size(8.dp))
            }
        },
        // The page is drawn once, under the NavHost.
        containerColor = Color.Transparent,
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // Click-to-edit under a remote: this sits above the list, so without
            // it every trip up the channels would pass through it and open a
            // keyboard. See DpadTextField.
            DpadTextField(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { fieldModifier ->
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = { Text("Search all channels") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = SearchFieldShape,
                    colors = fieldColours(),
                    modifier = fieldModifier.fillMaxWidth(),
                )
            }

            if (state.searching) {
                SearchStatus(state)
            } else {
                // Only offered once there is something to carry on with, and
                // hidden while already looking at the recents shelf, where it
                // would sit directly above the same channel.
                state.lastWatched?.takeIf { state.shelf != Shelf.Recent }?.let { channel ->
                    ResumeBar(channel = channel, onClick = { onChannel(channel.streamId) })
                }

                // Hidden while searching: results come from every channel, so a
                // chip would claim a filter that is not being applied.
                ShelfChips(state = state, onSelect = viewModel::selectShelf)
            }

            when {
                // A search is not answered from the category, so the category's
                // own error and loading states do not stand in for its results.
                !state.searching && state.error != null -> ErrorPanel(
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

                !state.searching && state.busy && state.channels.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                state.visibleChannels.isEmpty() -> EmptyPanel(state)

                else -> {
                    val categoryNames = remember(state.categories) {
                        state.categories.associate { it.id to it.name }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.visibleChannels, key = { it.streamId }) { channel ->
                            // From the full guide on the device: a quick local
                            // lookup per row, never a request to the panel.
                            val onNow by produceState<XmltvProgramme?>(null, channel.epgChannelId, guideVersion, minute) {
                                value = viewModel.onNow(channel, minute * 60)
                            }
                            ChannelRow(
                                onNow = onNow,
                                nowSeconds = minute * 60,
                                channel = channel,
                                // Worth saying on favourites, recents, All and
                                // search results; inside the category, it would
                                // only repeat the chip.
                                category = categoryNames[channel.categoryId]
                                    ?.takeIf { state.shelf != Shelf.Panel(channel.categoryId) },
                                starred = state.isFavourite(channel.streamId),
                                onClick = { onChannel(channel.streamId) },
                                onStar = { viewModel.toggleFavourite(channel) },
                            )
                        }
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
 * moment it is used. Laid out as a [CategoryShelf].
 */
@Composable
private fun ShelfChips(state: ChannelsUiState, onSelect: (Shelf) -> Unit) {
    fun chip(key: String, label: String, shelf: Shelf, special: Boolean = false) =
        ShelfChip(key, label, selected = state.shelf == shelf, onClick = { onSelect(shelf) }, special = special)

    CategoryShelf(
        buildList {
            if (state.favourites.isNotEmpty()) add(chip("favourites", "★ Favourites", Shelf.Favourites, special = true))
            if (state.recents.isNotEmpty()) add(chip("recent", "Recent", Shelf.Recent, special = true))
            if (state.categories.isNotEmpty()) {
                add(chip("all", "All", Shelf.Panel(null)))
                state.categories.forEach { add(chip("c:${it.id}", it.name, Shelf.Panel(it.id))) }
            }
        }
    )
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
    // A panel with the one solid-cobalt button on the screen: carrying on is
    // the most likely thing anyone opening the app wants.
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .nightCard(shape)
            .focusRing(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Night.cobalt),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
        }
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "CARRY ON WATCHING",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 0.8.sp,
                color = Night.accent,
            )
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun ChannelRow(
    channel: LiveStream,
    category: String?,
    starred: Boolean,
    onClick: () -> Unit,
    onStar: () -> Unit,
    /** What is on now, when the full guide knows. */
    onNow: XmltvProgramme? = null,
    nowSeconds: Long = 0,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .nightCard()
            .focusRing(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(start = 9.dp, end = 2.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLogo(channel)

        Spacer(Modifier.size(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (category != null) {
                Text(
                    text = category,
                    style = MaterialTheme.typography.bodySmall,
                    color = Night.inkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            onNow?.takeIf { it.title.isNotBlank() }?.let { programme ->
                Text(
                    text = programme.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = Night.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
                LinearProgressIndicator(
                    progress = {
                        ((nowSeconds - programme.start).toFloat() / (programme.stop - programme.start)).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.padding(top = 4.dp, end = 12.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = Night.accent,
                    trackColor = Night.quiet,
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                )
            }
        }

        if (channel.number > 0) {
            QuietPill("${channel.number}", Modifier.padding(start = 8.dp))
        }

        IconButton(onClick = onStar) {
            Icon(
                imageVector = if (starred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = if (starred) {
                    "Remove ${channel.name} from favourites"
                } else {
                    "Add ${channel.name} to favourites"
                },
                tint = if (starred) Night.pink else Night.inkSoft,
            )
        }
    }
}

/**
 * The channel's logo in a rounded tile, or — when there is none, or the link is
 * dead, which is a good share of them — its initials on a colour picked from its
 * name. Picked, not random, so a channel keeps its colour from one visit to the
 * next. Logos are decoration and never load-bearing: the row reads fine either way.
 */
@Composable
private fun ChannelLogo(channel: LiveStream) {
    var failed by remember(channel.icon) { mutableStateOf(false) }
    val showLogo = channel.icon.isNotBlank() && !failed
    val tile = RoundedCornerShape(13.dp)
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(tile)
            .background(if (showLogo) Night.quiet else tileColour(channel.name)),
        contentAlignment = Alignment.Center,
    ) {
        if (showLogo) {
            AsyncImage(
                model = channel.icon,
                contentDescription = null,
                onError = { failed = true },
                modifier = Modifier.size(40.dp),
            )
        } else {
            Text(
                text = channelInitials(channel.name),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
            )
        }
    }
}

/** Flat, from the icon's own family, deep enough for white initials. No yellow. */
private val TileColours = listOf(
    Color(0xFF2F5FD6),
    Color(0xFFC2456B),
    Color(0xFF1D8A7E),
    Color(0xFF5B4BC9),
    Color(0xFF2B79B8),
    Color(0xFF8A3F9E),
)

internal fun tileColour(name: String): Color = TileColours[Math.floorMod(name.hashCode(), TileColours.size)]

/**
 * What is being searched, so a short list of results is not mistaken for the
 * whole answer. The first search on a line has to fetch every channel, which
 * on a big line takes a while, and saying so beats a list that silently grows.
 */
@Composable
private fun SearchStatus(state: ChannelsUiState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.indexing) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(8.dp))
        }
        val count = state.visibleChannels.size
        Text(
            text = when {
                state.indexing -> "Fetching every channel to search…"
                state.searchNote != null -> state.searchNote
                count == 1 -> "1 channel matches"
                else -> "$count channels match"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (state.searchNote != null && !state.indexing) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** Says which list is empty, because "no channels" answers a different question. */
@Composable
private fun EmptyPanel(state: ChannelsUiState) {
    val message = when {
        state.searching && state.indexing -> "Searching every channel…"
        state.searching && state.searchNote != null -> "Nothing in this list matches \"${state.query}\"."
        state.searching -> "No channel on this line matches \"${state.query}\"."
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
        PrimaryButton(onClick = onRetry) { Text("Try again", style = MaterialTheme.typography.labelLarge) }

        if (onLoadEverything != null) {
            Spacer(Modifier.height(8.dp))
            SecondaryButton(text = "Load every channel", onClick = onLoadEverything)
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
