package com.ipterebi.app.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ipterebi.app.AppContainer
import com.ipterebi.app.playback.RecordingAlarms
import com.ipterebi.app.playback.RecordingService
import com.ipterebi.app.ui.theme.Corners
import com.ipterebi.app.ui.theme.tabular
import com.ipterebi.core.Recording
import com.ipterebi.core.RecordingState
import com.ipterebi.core.XtreamAccount
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What has been kept, and what is waiting to be.
 *
 * One list rather than two, in the order the clock will reach them: what is
 * recording now, then what is booked, then what is already on the stick,
 * newest first. A separate "scheduled" screen would be a second place to look
 * for an evening that has one answer.
 *
 * Each row says which of those it is, because the thing a viewer wants to know
 * first is whether the programme they asked for is actually going to happen,
 * and a failed recording is worth more than silence — it says what went wrong
 * in a sentence they can act on, which is usually "the stick is not in".
 */
@Composable
fun TvRecordingsScreen(
    container: AppContainer,
    account: XtreamAccount,
    onPlay: (Recording) -> Unit,
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val all by container.recordings.recordings(account)
        .collectAsStateWithLifecycle(emptyList())

    val ordered = remember(all) {
        all.sortedWith(
            compareBy<Recording> {
                when (it.state) {
                    RecordingState.RECORDING -> 0
                    RecordingState.SCHEDULED -> 1
                    else -> 2
                }
            }.thenByDescending { it.startSeconds },
        )
    }

    Column(Modifier.fillMaxSize().background(TvGround)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 48.dp, top = 24.dp, end = 48.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Recordings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = TvInk,
            )
            Spacer(Modifier.width(16.dp))
            Text(
                when {
                    ordered.isEmpty() -> ""
                    else -> "OK  Watch or cancel      Hold OK  Delete"
                },
                style = MaterialTheme.typography.labelLarge,
                color = TvInkSoft,
            )
        }

        if (ordered.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Nothing recorded yet. In the guide, press OK on a programme that " +
                        "has not been on yet to keep it; while a channel is playing, hold " +
                        "OK and choose Record.",
                    style = MaterialTheme.typography.titleMedium,
                    color = TvInkSoft,
                )
            }
            return@Column
        }

        LazyColumn(
            // A group, or the rail cannot hand the remote over: right from the
            // rail found nothing to go to and the press was simply lost. The
            // same hand-off the library grid needs.
            modifier = Modifier.fillMaxSize().focusGroup(),
            contentPadding = PaddingValues(start = 44.dp, end = 44.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(ordered, key = { it.id }) { recording ->
                RecordingRow(
                    recording = recording,
                    zone = zone,
                    onChoose = {
                        when (recording.state) {
                            // Already kept: watch it.
                            RecordingState.DONE -> onPlay(recording)
                            // Still to come: take it off, and move the alarm
                            // on to whatever is next.
                            RecordingState.SCHEDULED -> container.scope.launch {
                                container.recordings.remove(account, recording.id)
                                RecordingAlarms.arm(context)
                            }
                            // Going on now: stop it, and keep what there is.
                            RecordingState.RECORDING -> RecordingService.stop(context)
                            // Nothing to watch and nothing to stop.
                            else -> container.scope.launch {
                                container.recordings.forget(account, recording.id)
                            }
                        }
                    },
                    onDelete = {
                        container.scope.launch {
                            if (recording.state == RecordingState.RECORDING) {
                                RecordingService.stop(context)
                            }
                            container.recordings.forget(account, recording.id)
                            RecordingAlarms.arm(context)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun RecordingRow(
    recording: Recording,
    zone: ZoneId,
    onChoose: () -> Unit,
    onDelete: () -> Unit,
) {
    TvRow(onClick = onChoose, onLongClick = onDelete, radius = 12.dp) { focused ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    recording.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (focused) TvFocusInk else TvInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    recording.channelName + "  ·  " + whenOf(recording, zone),
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = if (focused) TvFocusInk.copy(alpha = 0.75f) else TvInkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(
                stateOf(recording),
                style = MaterialTheme.typography.labelLarge,
                // Red for a failure is the one place this app uses pink as a
                // warning rather than as favourites; nothing else on the row
                // would tell a viewer their evening did not happen.
                color = when {
                    focused -> TvFocusInk
                    recording.state == RecordingState.FAILED -> TvPink
                    recording.state == RecordingState.RECORDING -> TvAccent
                    else -> TvInkSoft
                },
                maxLines = 1,
            )
        }
    }
    if (recording.state == RecordingState.FAILED && recording.failure.isNotBlank()) {
        Text(
            recording.failure,
            style = MaterialTheme.typography.bodySmall,
            color = TvInkSoft,
            modifier = Modifier
                .fillMaxWidth()
                .clip(Corners.card)
                .padding(start = 18.dp, end = 18.dp, bottom = 4.dp),
        )
    }
}

private val DAY_AND_TIME: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.getDefault())

private fun whenOf(recording: Recording, zone: ZoneId): String {
    val start = if (recording.programmeStart > 0) recording.programmeStart else recording.startSeconds
    return Instant.ofEpochSecond(start).atZone(zone).format(DAY_AND_TIME)
}

private fun stateOf(recording: Recording): String = when (recording.state) {
    RecordingState.SCHEDULED -> "Booked"
    RecordingState.RECORDING -> "Recording · " + megabytes(recording.bytes)
    RecordingState.DONE -> megabytes(recording.bytes)
    RecordingState.FAILED -> "Failed"
    RecordingState.CANCELLED -> "Cancelled"
}

private fun megabytes(bytes: Long): String {
    val gb = bytes.toDouble() / (1024 * 1024 * 1024)
    return if (gb >= 1) String.format(Locale.ROOT, "%.1f GB", gb)
    else String.format(Locale.ROOT, "%d MB", bytes / (1024 * 1024))
}
