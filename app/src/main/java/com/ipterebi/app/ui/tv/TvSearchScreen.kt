package com.ipterebi.app.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ipterebi.app.AppContainer
import com.ipterebi.app.ui.DpadTextField
import com.ipterebi.app.ui.fieldColours
import com.ipterebi.core.LiveStream
import com.ipterebi.core.carriedOnLabel
import com.ipterebi.core.spreadByChannel
import com.ipterebi.app.ui.theme.tabular

/**
 * Searching, on a television.
 *
 * The TV build had none at all — the note in CLAUDE.md said the team search
 * was "the phone's for now" — and this is that gap closed. It answers the
 * same two questions the phone's does, in the same order:
 *
 * - **What is *on* that matches**, above. A team name matches no channel
 *   name, so without this the answer to the question people actually ask is
 *   "nothing on this line". Each event names every channel carrying it, which
 *   is the whole point: when a feed dies this is the list you want.
 * - **What is *called* that**, below.
 *
 * **Typing is the expensive part here and the layout admits it.** The field
 * is at the top and keeps focus until Down is pressed, the whole line is
 * fetched while the viewer is still hunting for letters rather than on the
 * first keystroke, and results are rows a remote walks rather than a grid it
 * has to aim at. Anybody searching a team name every week should set it in
 * Settings instead and let Home do it — that is what the team row is for, and
 * this screen says so when it is empty.
 */
@Composable
fun TvSearchScreen(
    container: AppContainer,
    onPlay: (LiveStream) -> Unit,
) {
    val model: TvSearchViewModel =
        viewModel(factory = TvSearchViewModel.factory(container))
    val state by model.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(TvPanel)
            .padding(horizontal = 48.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // **The field takes focus when the screen opens, and that is not a
        // flourish.** Focus arrives on the rail, and the rail runs down the
        // left while the field sits across the top: pressing Right from
        // "Search", six rows down, finds nothing level with it and nothing
        // happens. Somebody who opened a search screen wants to type, so the
        // caret is put where they are already looking. Safe because
        // DpadTextField is click-to-edit -- focus alone does not summon the
        // keyboard, which is the trap it exists to avoid.
        val field = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { field.requestFocus() } }

        Row(verticalAlignment = Alignment.CenterVertically) {
            DpadTextField(Modifier.fillMaxWidth().focusRequester(field)) { fieldModifier ->
                OutlinedTextField(
                    value = state.query,
                    onValueChange = model::onQuery,
                    label = { Text("Search channels and what is on") },
                    singleLine = true,
                    colors = fieldColours(),
                    shape = MaterialTheme.shapes.large,
                    modifier = fieldModifier.fillMaxWidth(),
                )
            }
        }

        if (state.preparing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = TvAccent)
                Text(
                    "  Fetching every channel on the line, once.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TvInkSoft,
                )
            }
        }
        state.note?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = TvPink)
        }

        if (state.query.isBlank()) {
            Text(
                "Type a channel name, or the name of a team. A team is worth setting " +
                    "in Settings instead — the row on Home then finds their match for " +
                    "you, which beats spelling it out on a remote every week.",
                style = MaterialTheme.typography.bodyMedium,
                color = TvInkSoft,
            )
            return@Column
        }

        // Both kinds have to be empty before this says so. On the phone the
        // channel list once replaced its results with "0 channels match"
        // whenever no *name* matched, which drew over the event it had just
        // found — precisely the search this exists for.
        if (state.empty && !state.preparing) {
            Text(
                "Nothing on the line matches that, by name or in the guide.",
                style = MaterialTheme.typography.bodyMedium,
                color = TvInkSoft,
            )
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.showings.isNotEmpty()) {
                item {
                    Text(
                        "ON NOW AND COMING",
                        style = MaterialTheme.typography.labelMedium,
                        color = TvAccent,
                    )
                }
            }
            items(state.showings, key = { it.showing.title + it.showing.start }) { on ->
                Column(Modifier.padding(bottom = 4.dp)) {
                    Text(
                        on.showing.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TvInk,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        // The guide channels and the streams, which are
                        // different numbers on a line that carries each
                        // channel five ways. See carriedOnLabel.
                        carriedOnLabel(on.showing.feeds, on.channels.size),
                        style = MaterialTheme.typography.bodySmall.tabular(),
                        color = TvInkSoft,
                    )
                    // Every feed carrying it, each one a row that tunes it.
                    // This is the list somebody wants when a picture has just
                    // gone, so it is not behind another press.
                    on.channels.spreadByChannel().forEach { channel ->
                        ChannelLine(channel) { onPlay(channel) }
                    }
                }
            }

            if (state.channels.isNotEmpty()) {
                item {
                    Text(
                        "CHANNELS",
                        style = MaterialTheme.typography.labelMedium,
                        color = TvAccent,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            items(state.channels, key = { it.streamId }) { channel ->
                ChannelLine(channel) { onPlay(channel) }
            }
        }
    }
}

/** One channel, as a row the remote walks. */
@Composable
private fun ChannelLine(channel: LiveStream, onPlay: () -> Unit) {
    TvRow(onClick = onPlay, modifier = Modifier.fillMaxWidth()) { focused ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                channel.number.toString(),
                style = MaterialTheme.typography.bodySmall.tabular(),
                color = if (focused) TvFocusInk else TvInkSoft,
                modifier = Modifier.padding(end = 12.dp),
            )
            Text(
                channel.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (focused) TvFocusInk else TvInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
