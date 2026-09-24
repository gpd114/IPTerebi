package com.ipterebi.app.ui.player

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import com.ipterebi.app.ui.focusRing
import com.ipterebi.app.ui.theme.Corners
import com.ipterebi.app.ui.theme.OverVideo
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Which audio and which subtitles, chosen over the picture.
 *
 * A panel's streams routinely carry more than one audio track — the same match
 * with the home commentary and the away commentary, a film with its original
 * language beside a dub — and until now there was no way in: ExoPlayer took
 * the first track it could play and that was the end of it. Subtitles were
 * worse; a stream could be carrying them and nothing ever showed them.
 *
 * Two things are remembered, and they are languages rather than track numbers:
 * a track number means nothing on the next channel, while "English audio,
 * subtitles off" is exactly what someone means when they pick it once. They
 * are set as *preferences* on the player, so a stream that has the language
 * comes up in it and a stream that does not falls back to its own first track
 * rather than to silence.
 */
object TrackChoice {
    private const val FILE = "tracks"
    private const val AUDIO = "audio.language"
    private const val TEXT = "text.language"

    /** What [TEXT] holds when subtitles have been turned off by hand. */
    private const val OFF = "off"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * Puts the remembered choice on a player being built, before anything is
     * prepared — so the first frame already has the right audio rather than
     * switching a second in.
     */
    fun applyTo(context: Context, builder: TrackSelectionParameters.Builder): TrackSelectionParameters.Builder {
        val p = prefs(context)
        p.getString(AUDIO, null)?.let { builder.setPreferredAudioLanguage(it) }
        when (val text = p.getString(TEXT, null)) {
            null -> Unit
            OFF -> builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            else -> builder.setPreferredTextLanguage(text)
        }
        return builder
    }

    private fun rememberAudio(context: Context, language: String?) {
        prefs(context).edit().putString(AUDIO, language?.takeIf { it.isNotBlank() }).apply()
    }

    private fun rememberText(context: Context, language: String?) {
        prefs(context).edit().putString(TEXT, language ?: OFF).apply()
    }

    /**
     * Whether there is anything to choose between: one audio track and no
     * subtitles is the common case on a live channel, and a button that opens
     * a panel saying "Audio 1" is clutter over the picture.
     */
    fun worthOffering(tracks: Tracks): Boolean {
        val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.sumOf { it.length }
        val text = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.sumOf { it.length }
        return audio > 1 || text > 0
    }

    /**
     * Picks a track: an override for the stream playing now, and the same
     * language remembered for the next one.
     */
    fun choose(context: Context, player: Player, row: TrackRow) {
        val builder = player.trackSelectionParameters.buildUpon()
        when (row) {
            is TrackRow.Off -> {
                builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                builder.setPreferredTextLanguage(null)
                rememberText(context, null)
            }
            is TrackRow.Track -> {
                builder.setTrackTypeDisabled(row.type, false)
                builder.setOverrideForType(TrackSelectionOverride(row.group.mediaTrackGroup, row.index))
                if (row.type == C.TRACK_TYPE_AUDIO) {
                    builder.setPreferredAudioLanguage(row.language)
                    rememberAudio(context, row.language)
                } else {
                    builder.setPreferredTextLanguage(row.language)
                    rememberText(context, row.language ?: OFF)
                }
            }
        }
        player.trackSelectionParameters = builder.build()
    }
}

/** One line in the panel. */
sealed interface TrackRow {
    val label: String
    val selected: Boolean

    /** Subtitles off — always offered, always first in its section. */
    data class Off(override val selected: Boolean) : TrackRow {
        override val label get() = "Off"
    }

    data class Track(
        override val label: String,
        override val selected: Boolean,
        val type: Int,
        val group: Tracks.Group,
        val index: Int,
        val language: String?,
    ) : TrackRow
}

/** The tracks of one kind, as rows, skipping anything the device cannot play. */
private fun rowsFor(tracks: Tracks, type: Int): List<TrackRow.Track> =
    tracks.groups.filter { it.type == type }.flatMap { group ->
        (0 until group.length).mapNotNull { i ->
            if (!group.isTrackSupported(i)) return@mapNotNull null
            val format = group.getTrackFormat(i)
            TrackRow.Track(
                label = format.describe(type, i),
                selected = group.isTrackSelected(i),
                type = type,
                group = group,
                index = i,
                language = format.language,
            )
        }
    }

/**
 * What to call a track.
 *
 * Panels label tracks badly or not at all — `und`, a codec name, nothing — so
 * the language is read first and the label only used when it says something
 * the language does not. A number is the last resort, because a list of blanks
 * cannot be chosen from.
 */
private fun Format.describe(type: Int, index: Int): String {
    val tongue = language
        ?.takeIf { it.isNotBlank() && !it.equals("und", ignoreCase = true) }
        ?.let { Locale.forLanguageTag(it).displayLanguage.takeIf { name -> name.isNotBlank() } }

    val named = label?.takeIf { it.isNotBlank() && !it.equals(tongue, ignoreCase = true) }

    val head = tongue
        ?: named
        ?: if (type == C.TRACK_TYPE_AUDIO) "Audio ${index + 1}" else "Subtitles ${index + 1}"

    val notes = buildList {
        if (tongue != null && named != null) add(named)
        if (type == C.TRACK_TYPE_AUDIO) {
            when (channelCount) {
                1 -> add("Mono")
                2 -> add("Stereo")
                6 -> add("5.1")
                8 -> add("7.1")
            }
        }
        if (roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO != 0) add("described")
        if (roleFlags and C.ROLE_FLAG_TRANSCRIBES_DIALOG != 0) add("for the hard of hearing")
        if (selectionFlags and C.SELECTION_FLAG_FORCED != 0) add("forced")
    }

    return if (notes.isEmpty()) head else "$head · ${notes.joinToString(" · ")}"
}

/**
 * Everything about this playback that can be changed while it runs: how the
 * picture fills the screen, when to stop, which audio, which subtitles.
 *
 * One panel rather than a button each, because over a picture every extra
 * control is something in the way of the thing being watched. Picture and
 * Sleep are always here; the track sections appear only when the stream
 * carries a choice.
 *
 * The tracks are read as they change rather than once, because a live stream
 * announces them a moment after it opens and again after every reconnect.
 */
@Composable
fun PlaybackPanel(
    player: Player,
    context: Context,
    /** Live has no end of its own, so a sleep timer means something different. */
    live: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tracks by remember { mutableStateOf(player.currentTracks) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(new: Tracks) {
                tracks = new
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Ticks while the panel is open so the countdown is not stale. Only while
    // it is open: nothing is drawn from it otherwise.
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = SystemClock.elapsedRealtime()
        }
    }

    val audio = rowsFor(tracks, C.TRACK_TYPE_AUDIO)
    val text = rowsFor(tracks, C.TRACK_TYPE_TEXT)

    Column(
        modifier
            .widthIn(max = 320.dp)
            // A share of the screen rather than a fixed height: in landscape,
            // which is how anything is watched, 460dp is taller than the phone
            // and the panel ran off the bottom.
            .fillMaxHeight(0.78f)
            .heightIn(max = 460.dp)
            .clip(Corners.panel)
            .background(OverVideo.panel)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Heading("Picture")
        PictureFit.entries.forEach { option ->
            Line(
                label = option.label,
                selected = option == Picture.fit,
                // Chosen without closing: the whole point is to see the
                // difference, and a panel that shut each time would mean
                // opening it three times to compare three shapes.
                onClick = { Picture.set(context, option) },
            )
        }

        Heading(if (SleepTimer.running) "Sleep · ${sleepRemainingLabel(SleepTimer.remaining(now))}" else "Sleep")
        Line(label = "Off", selected = !SleepTimer.running, onClick = { SleepTimer.cancel() })
        SLEEP_CHOICES.forEach { minutes ->
            Line(
                label = "$minutes minutes",
                // Never ticked once it is running: what is running is a
                // deadline, and after a minute "30 minutes" would be a lie.
                selected = false,
                onClick = { SleepTimer.set(minutes, SystemClock.elapsedRealtime()) },
            )
        }
        if (!live) {
            Hint("Stops the film and frees your line. It will carry on where it stopped.")
        } else {
            Hint("Stops the channel and frees your line.")
        }

        if (audio.size > 1) {
            Heading("Audio")
            audio.forEach { row -> Line(row) { TrackChoice.choose(context, player, row); onDismiss() } }
        }

        if (text.isNotEmpty()) {
            Heading("Subtitles")
            val off = TrackRow.Off(selected = text.none { it.selected })
            Line(off) { TrackChoice.choose(context, player, off); onDismiss() }
            text.forEach { row -> Line(row) { TrackChoice.choose(context, player, row); onDismiss() } }
        }

        if (audio.size <= 1 && text.isEmpty()) {
            Heading("Audio and subtitles")
            Hint("This stream carries one audio track and no subtitles.")
        }
    }
}

/** Small print under a section, in the panel's own colours. */
@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = OverVideo.inkSoft,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = OverVideo.accent,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun Line(row: TrackRow, onClick: () -> Unit) =
    Line(label = row.label, selected = row.selected, onClick = onClick)

/** One choosable line: a tick where the chosen one is, and the label beside it. */
@Composable
private fun Line(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(Corners.control)
            .focusRing(Corners.control)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = "Chosen", tint = OverVideo.accent)
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) OverVideo.ink else OverVideo.inkSoft,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
