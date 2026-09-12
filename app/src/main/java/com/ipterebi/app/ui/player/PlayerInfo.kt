package com.ipterebi.app.ui.player

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ipterebi.app.AppContainer
import com.ipterebi.app.BuildConfig
import com.ipterebi.app.TAG_PLAY
import com.ipterebi.core.EpgListing
import com.ipterebi.core.XtreamAccount
import com.ipterebi.core.lineKey
import com.ipterebi.core.nowAndNext
import kotlinx.coroutines.delay
import java.time.Instant


/**
 * How often the clock advances. Only the progress bar reads it, and a bar two
 * pixels tall does not repay ticking any faster than this.
 */
private const val TICK_MS = 30_000L

/**
 * The shortest gap between two guide requests for the same channel.
 *
 * Without a floor, a panel whose guide has stopped being updated — every
 * programme in it already finished — satisfies "we have run off the end" on
 * every single tick, and the app quietly asks it for the guide twice a minute
 * for as long as the channel is left on. Legitimate exhaustion is hours away,
 * because a request returns several programmes, so nothing is lost by waiting.
 */
private const val MIN_REFETCH_SECONDS = 300L

/**
 * What is on now and next, and the moment that answer was true for.
 *
 * [at] is carried rather than each composable calling `Instant.now()` for
 * itself: Compose only redraws what has changed, so a bar that read the clock
 * directly would be drawn once and then sit still. Recomposition follows this
 * value, so everything derived from it moves together.
 */
data class ProgrammeGuide(
    val now: EpgListing? = null,
    val next: EpgListing? = null,
    val at: Instant = Instant.EPOCH,
) {
    /** Null when the panel said nothing, which is different from "just started". */
    val progress: Float? get() = now?.progressAt(at)

    val isEmpty: Boolean get() = now == null && next == null
}

/**
 * The guide for one channel: from the full guide kept on the device when it
 * has the channel, else `get_short_epg` put on the right clock — see Guide.
 *
 * Looked up once when the channel is opened, again whenever the full guide is
 * refreshed, and again only when the clock runs past the last programme we
 * were handed. Sitting on one channel all evening should not query the panel
 * every thirty seconds; the ticker moves the bar, it does not fetch.
 */
@Composable
fun rememberProgrammeGuide(
    container: AppContainer,
    account: XtreamAccount,
    streamId: Int,
    /** The channel's `epg_channel_id`, which is what the full guide is keyed on. */
    epgChannelId: String?,
): ProgrammeGuide {
    // Keyed on the line as well as the channel: changing the user agent in
    // settings rebuilds the account, and the guide belongs to the line.
    val key = Triple(streamId, account.base, account.username)
    val guideVersion by container.guide.version.collectAsStateWithLifecycle()

    var listings by remember(key) { mutableStateOf<List<EpgListing>>(emptyList()) }
    var now by remember { mutableStateOf(Instant.now()) }
    var reloads by remember(key) { mutableStateOf(0) }
    var lastFetched by remember(key) { mutableStateOf(Instant.EPOCH) }

    LaunchedEffect(key, reloads, guideVersion) {
        // Never throws: a line with no guide, a channel missing from one, a fork
        // answering `false` and a panel with no get_short_epg at all all arrive
        // as an empty list, because none of them is a fault worth a message.
        val at = Instant.now()
        listings = container.guide.listings(account, streamId, epgChannelId, at)
        lastFetched = at
        if (BuildConfig.DEBUG) {
            val shift = container.guide.clock.shiftFor(account.lineKey)
            Log.d(
                TAG_PLAY,
                "stream $streamId guide: ${listings.size} programmes " +
                    "(${if (listings.firstOrNull()?.plainText == true) "full guide" else "short answer"}), " +
                    "now ${listings.nowAndNext(at).now?.titleText ?: "(nothing listed)"}" +
                    (if (shift != 0L) ", short answers put right by ${shift / 60} min" else ""),
            )
        }
    }

    LaunchedEffect(key) {
        while (true) {
            delay(TICK_MS)
            now = Instant.now()
        }
    }

    // Past the end of everything we were given, so there is something new to
    // ask for. Guarded twice over: on having been given anything at all, so a
    // channel with no guide does not re-ask forever, and on [MIN_REFETCH_SECONDS],
    // so one whose guide has stopped being updated does not either.
    val lastKnownEnd = remember(listings) { listings.maxOfOrNull { it.stopTimestamp } ?: 0L }
    LaunchedEffect(now, lastKnownEnd) {
        val runOut = lastKnownEnd > 0L && now.epochSecond >= lastKnownEnd
        val cooledDown = now.epochSecond - lastFetched.epochSecond >= MIN_REFETCH_SECONDS
        if (runOut && cooledDown) reloads++
    }

    val (current, upNext) = remember(listings, now) { listings.nowAndNext(now) }
    return ProgrammeGuide(now = current, next = upNext, at = now)
}

/**
 * The channel name, and the guide when there is one.
 *
 * Sits at the top because the player's own controls own the bottom of the
 * screen. Everything below the name is optional — plenty of lines carry no EPG,
 * plenty of channels are missing from one that exists — so each piece is drawn
 * only if it arrived, and this collapses to just the channel name when none of
 * it did.
 */
@Composable
fun ChannelInfoOverlay(
    channelName: String?,
    guide: ProgrammeGuide,
    modifier: Modifier = Modifier,
) {
    if (channelName == null && guide.isEmpty) return

    Column(
        modifier = modifier
            .widthIn(max = 420.dp)
            .clip(RoundedCornerShape(18.dp))
            // A dark panel whichever theme: it sits over the picture.
            .background(com.ipterebi.app.ui.theme.OverVideo.panel)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (channelName != null) {
            Text(
                text = channelName,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }

        guide.now?.let { current ->
            val title = current.titleText
            if (title.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            val time = current.timeLabel()
            if (time.isNotBlank()) {
                Text(
                    text = time,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        guide.next?.titleText?.takeIf { it.isNotBlank() }?.let { title ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Next: $title",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * How far through the current programme we are, pinned to the very top edge.
 *
 * Absent entirely when the panel did not say. A bar sitting at zero would read
 * as a programme that has just started rather than as a guide that is missing,
 * and the second is far more common than the first.
 */
@Composable
fun ProgrammeProgress(guide: ProgrammeGuide, modifier: Modifier = Modifier) {
    val progress = guide.progress ?: return

    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier.fillMaxWidth().height(2.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = Color.White.copy(alpha = 0.25f),
    )
}
