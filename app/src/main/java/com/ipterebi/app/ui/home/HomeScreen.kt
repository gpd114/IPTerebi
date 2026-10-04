package com.ipterebi.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ipterebi.app.AppContainer
import com.ipterebi.app.ui.SectionTopBar
import com.ipterebi.app.ui.focusFill
import com.ipterebi.app.ui.nightCard
import com.ipterebi.app.ui.channels.tileColour
import com.ipterebi.app.ui.theme.Corners
import com.ipterebi.app.ui.theme.Night
import com.ipterebi.app.ui.theme.tabular
import com.ipterebi.core.LiveStream
import com.ipterebi.core.ListedItem
import com.ipterebi.core.SavedKind
import com.ipterebi.core.WatchedItem
import com.ipterebi.core.channelInitials
import com.ipterebi.core.remainingLabel

/**
 * Where the app opens: one row per section, and the heading is the way in.
 *
 * Everything on it is already on the device — favourites and recents are
 * stored per line, positions are a local list — so opening the app costs no
 * request and the screen is filled before the panel has answered anything.
 * That is deliberate, and it is why there is no "recently added" row: that
 * would mean pulling the whole film list on every launch, which is several
 * megabytes on a real line.
 */
@Composable
fun HomeScreen(
    container: AppContainer,
    onSettings: () -> Unit,
    onChannels: () -> Unit,
    onFilms: () -> Unit,
    onSeries: () -> Unit,
    onPlayChannel: (LiveStream) -> Unit,
    onResume: (WatchedItem) -> Unit,
    /** Something opened out of one of the viewer's own lists. */
    onOpenListed: (ListedItem) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(container)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Read as the row draws rather than held in the state: changing it in
    // Settings then shows on the way back, with no reload of either list.
    val showingRecent = HomeChannels.source == HomeChannels.Source.RECENT
    val channels = if (showingRecent) state.recents else state.favourites

    // Down from a heading has to be told where to go. A heading is the width
    // of the screen and a card is 76dp of it, so Compose weighs the card's
    // centre — seven hundred pixels off to the left — against the next
    // heading's, which is directly below, and the heading wins. Measured on
    // the TV emulator: the cards are focusable and are candidates in the
    // search; they simply never win it. So each heading hands Down to its own
    // row, and the row, being a focus group, passes it to the first card.
    val teamRow = remember { FocusRequester() }
    val channelRow = remember { FocusRequester() }
    val filmRow = remember { FocusRequester() }
    val episodeRow = remember { FocusRequester() }

    Scaffold(
        topBar = { SectionTopBar("Home", onSettings) },
        containerColor = Night.ground,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        ) {
            // Above everything, because when it is on it is the reason the
            // television is on. Absent entirely when no team is named, so
            // nobody who has not asked for it sees a row explaining itself.
            if (state.team.isNotBlank()) {
                item {
                    RowHeading(
                        title = state.team,
                        // A mention is labelled as one. With nothing in the
                        // guide that reads as a fixture the row falls back to
                        // whatever says the name, and dressing that as "Next"
                        // is how a wartime documentary, a motocross
                        // championnat and a Bundesliga listing on an Indian
                        // film channel each came to look like the team's
                        // match. Shown, because seeing it beats seeing
                        // nothing — but not passed off as something it isn't.
                        tag = state.teamMatch?.let {
                            when {
                                !it.isFixture -> "Mentioned"
                                it.onNow -> "On now"
                                else -> "Next"
                            }
                        }.orEmpty(),
                        onOpen = null,
                        row = teamRow.takeIf { state.teamChannels.isNotEmpty() },
                    )
                }
                item {
                    val match = state.teamMatch
                    if (match == null && state.teamLooking) {
                        // Reading the guide. Saying "nothing" here and
                        // correcting it a moment later is how a viewer comes
                        // away believing the answer is no.
                        EmptyRow(
                            title = "Looking for ${state.team}\u2026",
                            detail = "Reading the guide.",
                        )
                    } else if (match == null) {
                        EmptyRow(
                            title = "Nothing for ${state.team} in the guide",
                            detail = "The guide reaches only as far as your provider publishes, " +
                                "which is often about a day. Settings → Your team changes the name.",
                        )
                    } else {
                        Column {
                            Text(
                                match.showing.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = Night.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                            )
                            Text(
                                teamWhen(match) + "  ·  on ${state.teamChannels.size} channel" +
                                    (if (state.teamChannels.size == 1) "" else "s"),
                                style = MaterialTheme.typography.bodySmall.tabular(),
                                color = Night.inkSoft,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                            )
                            LazyRow(
                                modifier = Modifier.focusRequester(teamRow).rightStaysInRow().focusGroup(),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(state.teamChannels, key = { it.streamId }) { channel ->
                                    ChannelCard(channel) { onPlayChannel(channel) }
                                }
                            }
                        }
                    }
                }
            }

            item {
                RowHeading(
                    title = "Live TV",
                    tag = if (showingRecent) "Recent" else "Favourites",
                    onOpen = onChannels,
                    row = channelRow.takeIf { channels.isNotEmpty() },
                )
            }
            item {
                if (channels.isEmpty() && !state.loading) {
                    EmptyRow(
                        title = if (showingRecent) "Nothing watched yet" else "No favourites yet",
                        detail = if (showingRecent) {
                            "Channels you watch turn up here. Settings → Home can show your favourites instead."
                        } else {
                            "Star a channel in the list and it waits here. Settings → Home can show recent channels instead."
                        },
                    )
                } else {
                    LazyRow(
                        // The heading above hands Down to this requester, and
                        // the group passes it on to the first card. Both halves
                        // are needed; nothing shows any of it on a phone.
                        modifier = Modifier.focusRequester(channelRow).rightStaysInRow().focusGroup(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(channels, key = { it.streamId }) { channel ->
                            ChannelCard(channel) { onPlayChannel(channel) }
                        }
                    }
                }
            }

            item {
                RowHeading(
                    title = "Films",
                    tag = if (state.films.isEmpty()) "" else "Continue watching",
                    onOpen = onFilms,
                    row = filmRow.takeIf { state.films.isNotEmpty() },
                )
            }
            item {
                if (state.films.isEmpty() && !state.loading) {
                    EmptyRow(
                        title = "Nothing part-watched",
                        detail = "Start a film and leave it, and it will be here at the second you stopped.",
                    )
                } else {
                    LazyRow(
                        // The heading above hands Down to this requester, and
                        // the group passes it on to the first card. Both halves
                        // are needed; nothing shows any of it on a phone.
                        modifier = Modifier.focusRequester(filmRow).rightStaysInRow().focusGroup(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.films, key = { it.id }) { film ->
                            WatchedCard(film, width = 104.dp, ratio = 2f / 3f) { onResume(film) }
                        }
                    }
                }
            }

            item {
                RowHeading(
                    title = "Series",
                    tag = if (state.episodes.isEmpty()) "" else "Continue watching",
                    onOpen = onSeries,
                    row = episodeRow.takeIf { state.episodes.isNotEmpty() },
                )
            }
            item {
                if (state.episodes.isEmpty() && !state.loading) {
                    EmptyRow(
                        title = "No episode in progress",
                        detail = "The next episode of anything you are watching will appear here.",
                    )
                } else {
                    LazyRow(
                        // The heading above hands Down to this requester, and
                        // the group passes it on to the first card. Both halves
                        // are needed; nothing shows any of it on a phone.
                        modifier = Modifier.focusRequester(episodeRow).rightStaysInRow().focusGroup(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.episodes, key = { it.id }) { episode ->
                            WatchedCard(episode, width = 168.dp, ratio = 16f / 9f) { onResume(episode) }
                        }
                    }
                }
            }

            // The viewer's own lists, after the rows the app decides on: what
            // someone filed themselves should be here, but under what they
            // were in the middle of. Empty lists are left out by the view
            // model — a row that says "nothing in this list" on the screen you
            // see most is a row explaining itself for no reason.
            state.lists.forEach { own ->
                item(key = "h:${own.name}") {
                    RowHeading(title = own.name, tag = "LIST")
                }
                item(key = "r:${own.name}") {
                    LazyRow(
                        modifier = Modifier.rightStaysInRow().focusGroup(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(own.items, key = { it.kind.name + ":" + it.id }) { entry ->
                            ListCard(entry) { onOpenListed(entry) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A section's name, what the row is showing, and the way into the full list.
 *
 * [row] is the row of cards beneath it, or null when there is none to go to.
 * Down is sent there explicitly because Compose will not send it there on its
 * own — see the note where the requesters are made.
 */
@Composable
private fun RowHeading(
    title: String,
    tag: String,
    /**
     * Null for one of the viewer's own lists: the row *is* the list, so there
     * is nowhere further to go and no "All" to offer.
     */
    onOpen: (() -> Unit)? = null,
    row: FocusRequester? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(Corners.control)
            .then(if (row != null) Modifier.focusProperties { down = row } else Modifier)
            .then(if (onOpen != null) Modifier.focusFill(Corners.control) else Modifier)
            .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Night.ink)
        if (tag.isNotBlank()) {
            Text(tag.uppercase(), style = MaterialTheme.typography.labelSmall, color = Night.accent)
        }
        Box(Modifier.weight(1f))
        if (onOpen != null) {
            Text("All", style = MaterialTheme.typography.bodySmall, color = Night.inkSoft)
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Night.inkSoft,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * A row with nothing in it says what would put something there. A row that
 * simply vanished would leave a new line opening on a blank page with no clue
 * what the screen is for.
 */
@Composable
private fun EmptyRow(title: String, detail: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .nightCard()
            .padding(14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Night.ink)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = Night.inkSoft)
    }
}

/** A channel: its logo, its number, and how far through whatever is on it is. */
@Composable
private fun ChannelCard(channel: LiveStream, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clip(Corners.tag)
            .focusFill(Corners.tag)
            .clickable(onClick = onClick)
            // Inside the fill, so focus shows as a frame round the logo as
            // well as behind the name: a tile paints over a background, and a
            // card that is all picture would otherwise show focus nowhere.
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .width(76.dp)
                .height(56.dp)
                .clip(Corners.tag)
                .background(if (channel.icon.isBlank()) tileColour(channel.name) else Night.veil),
            contentAlignment = Alignment.Center,
        ) {
            if (channel.icon.isNotBlank()) {
                AsyncImage(
                    model = channel.icon,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                )
            } else {
                Text(
                    channelInitials(channel.name),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                )
            }
        }
        if (channel.number > 0) {
            Text(
                "${channel.number}",
                style = MaterialTheme.typography.labelSmall.tabular(),
                color = Night.inkSoft,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        // Three lines, not two, and the card is wider than the tile it holds.
        //
        // A provider's channel names are mostly prefix — "UK:", "ENGLISH:",
        // the country, then the quality — and what tells two of them apart is
        // at the *end*: "UK: BBC ONE LONDON 4K" beside "UK: BBC ONE LONDON
        // HD". Clipping takes exactly the part that distinguishes them, which
        // is worst on the two rows where several feeds of one thing sit side
        // by side: favourites, and the team's channels. Being unable to tell
        // which is which there defeats the point of listing them.
        Text(
            channel.name,
            style = MaterialTheme.typography.bodySmall,
            color = Night.ink,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A film or an episode, with a bar showing how far in and how long is left. */
@Composable
private fun WatchedCard(item: WatchedItem, width: androidx.compose.ui.unit.Dp, ratio: Float, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(width + 6.dp)
            .clip(Corners.tag)
            .focusFill(Corners.tag)
            .clickable(onClick = onClick)
            // As the channel card: the poster would cover a background.
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .width(width)
                .aspectRatio(ratio)
                .clip(Corners.tag)
                .background(if (item.poster.isBlank()) tileColour(item.name) else Night.veil),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (item.poster.isNotBlank()) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp),
                )
            }
            // Over the artwork rather than under it: it is about this picture,
            // and under the title it would be mistaken for the row's own.
            LinearProgressIndicator(
                progress = { item.fraction },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = Night.accent,
                trackColor = Color.Black.copy(alpha = 0.45f),
                drawStopIndicator = {},
                gapSize = 0.dp,
            )
        }
        Text(
            item.name,
            style = MaterialTheme.typography.bodySmall,
            color = Night.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            listOf(item.detail, remainingLabel(item.remainingMs).takeIf { item.durationMs > 0 }.orEmpty())
                .filter { it.isNotBlank() }
                .joinToString(" � "),
            style = MaterialTheme.typography.labelSmall.tabular(),
            color = Night.inkSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One thing out of the viewer's own list.
 *
 * A poster and a name, with no progress bar: these were filed on purpose
 * rather than left half-watched, and a bar at nought under every one would
 * say something untrue. Fitted rather than cropped, as every poster in the
 * app is.
 */
@Composable
private fun ListCard(entry: ListedItem, onClick: () -> Unit) {
    val width = 104.dp
    Column(
        modifier = Modifier
            .width(width + 6.dp)
            .clip(Corners.tag)
            .focusFill(Corners.tag)
            .clickable(onClick = onClick)
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .width(width)
                .aspectRatio(2f / 3f)
                .clip(Corners.tag)
                .background(if (entry.poster.isBlank()) tileColour(entry.name) else Night.veil),
            contentAlignment = Alignment.Center,
        ) {
            if (entry.poster.isNotBlank()) {
                AsyncImage(
                    model = entry.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
        Text(
            entry.name,
            style = MaterialTheme.typography.bodySmall,
            color = Night.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            if (entry.kind == SavedKind.FILM) "Film" else "Series",
            style = MaterialTheme.typography.labelSmall,
            color = Night.inkSoft,
            maxLines = 1,
        )
    }
}

/**
 * A row of cards keeps Right to itself.
 *
 * There is no card to the right of the last one, and Compose does not stop
 * there: it looks for the nearest focusable anywhere to the right and takes
 * it. With one favourite channel on a television that was the settings cog in
 * the opposite corner — one press of Right and the remote was 1,600 pixels
 * away in the top bar, with a second press doing nothing because there is no
 * further right. It reads as the remote slipping, and it happens at the end of
 * every row; a row of one just makes it the first press.
 *
 * Only Right is cancelled. Left is how a remote gets back to the rail, and up
 * and down are how the rows are walked, so both still leave.
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.rightStaysInRow(): Modifier = focusProperties {
    exit = { direction ->
        if (direction == FocusDirection.Right) FocusRequester.Cancel else FocusRequester.Default
    }
}

/** "15:00 – 17:00" for a match, with the day when it is not today. */
private fun teamWhen(match: com.ipterebi.core.TeamMatch): String {
    val zone = java.time.ZoneId.systemDefault()
    val from = java.time.Instant.ofEpochSecond(match.showing.start).atZone(zone)
    val to = java.time.Instant.ofEpochSecond(match.showing.stop).atZone(zone)
    val today = java.time.LocalDate.now(zone)
    val clock = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    val day = if (from.toLocalDate() == today) "" else
        java.time.format.DateTimeFormatter.ofPattern("EEE d MMM, ").format(from)
    return day + clock.format(from) + " – " + clock.format(to)
}
