package com.ipterebi.app.data

import android.content.Context
import android.net.ConnectivityManager
import com.ipterebi.core.LiveStream
import com.ipterebi.core.EpgListing
import com.ipterebi.core.GuideClock
import com.ipterebi.core.SAME_EVENT_SLACK_SECONDS
import com.ipterebi.core.mentioning
import com.ipterebi.core.showings
import com.ipterebi.core.Showing
import com.ipterebi.core.WhatsOnIndex
import com.ipterebi.core.showingOf
import com.ipterebi.core.XmltvProgramme
import com.ipterebi.core.XtreamAccount
import com.ipterebi.core.XtreamClient
import com.ipterebi.core.asListing
import com.ipterebi.core.lineKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * What is on, for every screen: the full guide kept on the device
 * ([GuideStore]), and `get_short_epg` put right ([GuideClock]) for channels the
 * full guide does not cover.
 *
 * The full guide is the one whose times can be trusted — `xmltv.php` states
 * its offset — so it answers first. See [GuideClock] for how the first real
 * line's `get_short_epg` came to be two hours out.
 */
class Guide(context: Context, private val xtream: XtreamClient, private val log: (String) -> Unit) {

    private val store = GuideStore(context)
    val clock = GuideClock()

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshing = AtomicBoolean(false)
    private val failedAt = ConcurrentHashMap<String, Long>()

    private val _version = MutableStateFlow(0)

    /** Moves on each time a refresh completes, so screens know to look again. */
    val version: StateFlow<Int> = _version.asStateFlow()

    /**
     * Fetches [account]'s full guide in the background if it is over twelve
     * hours old — never two at once, not again for six hours after a failure,
     * and not on a metered network unless [onMetered]: it is tens of megabytes.
     *
     * [channels] are the line's channels, to keep only their guide entries —
     * and to know which of them keep a recording. When not
     * given, the channel list is fetched to find them.
     */
    fun refreshIfStale(account: XtreamAccount, channels: Collection<LiveStream>? = null, onMetered: Boolean = false) =
        refresh(account, channels, onMetered, asked = false)

    /**
     * Fetches [account]'s full guide now, because someone asked: whatever the
     * network, however recent the last one, and whatever the last failure.
     */
    fun refreshNow(account: XtreamAccount) = refresh(account, null, onMetered = true, asked = true)

    private val _downloading = MutableStateFlow(false)

    /** A refresh is under way, for a screen that offered one. */
    val downloading: StateFlow<Boolean> = _downloading.asStateFlow()

    private val clockAsked = ConcurrentHashMap.newKeySet<String>()

    /**
     * Asks the panel what time it thinks it is, once per line per launch.
     *
     * `server_info` states the panel's own wall clock, and the gap to the unix
     * timestamp beside it is the offset catch-up has to write into its URLs.
     * [GuideClock] can also work that out from a programme found in both the
     * short answer and the full guide — but only where `get_short_epg` answers
     * at all, and on the owner's box it answers nothing for the channels the
     * TV screen tunes. Nothing was ever learned there, the shift stayed zero,
     * and every recording was asked for in UTC from a panel two hours ahead of
     * it: it played, and it played the wrong two hours.
     *
     * One request, and it is the same call the sign-in screen makes.
     */
    fun learnPanelClock(account: XtreamAccount) {
        val line = account.lineKey
        if (!clockAsked.add(line)) return
        scope.launch {
            runCatching { xtream.authenticate(account) }
                .onSuccess { status -> status.clockShift?.let { clock.statedBy(line, it) } }
                // Not remembered as asked, so the next screen tries again:
                // this is worth having and one failed request costs nothing.
                .onFailure { clockAsked.remove(line) }
        }
    }

    private fun refresh(account: XtreamAccount, channels: Collection<LiveStream>?, onMetered: Boolean, asked: Boolean) {
        // Before the early returns below: the guide may be fresh, or the
        // network metered, and the clock is still wanted.
        learnPanelClock(account)
        scope.launch {
            val line = account.lineKey
            val now = System.currentTimeMillis()
            if (!asked) {
                val fetched = store.fetchedAt(line)
                if (fetched != null && now - fetched < STALE_AFTER_MS) return@launch
                if ((failedAt[line] ?: 0L) > now - RETRY_AFTER_MS) return@launch
            }
            if (!onMetered && connectivity?.isActiveNetworkMetered != false) return@launch
            if (!refreshing.compareAndSet(false, true)) return@launch
            _downloading.value = true
            try {
                // The line's channels, for two questions: which guide entries
                // to keep at all, and which of those keep a recording — the
                // past is only worth holding on to for channels that can play
                // it back. See GuideStore.commit.
                val streams = channels ?: xtream.liveStreams(account)
                val wanted = streams.map { it.epgChannelId }.filter { it.isNotBlank() }.toHashSet()
                val withArchive = streams
                    .filter { it.hasCatchUp }
                    .map { it.epgChannelId }
                    .filter { it.isNotBlank() }
                    .toHashSet()
                val writer = store.writer(line)
                // How far the guide reaches either way, which nothing else
                // reports and which decides what catch-up can offer: a
                // provider keeping seven days of recordings is no use if its
                // guide starts at this morning, because there is then nothing
                // to point at. Measured rather than assumed.
                var earliest = Long.MAX_VALUE
                var latest = Long.MIN_VALUE
                xtream.xmltv(account) {
                    if (it.channel in wanted) {
                        writer.add(it)
                        if (it.start < earliest) earliest = it.start
                        if (it.stop > latest) latest = it.stop
                    }
                }
                val fetchedAt = System.currentTimeMillis()
                writer.commit(fetchedAt, keepPastFor = withArchive)
                failedAt.remove(line)
                log(
                    "  guide kept: ${writer.written} programmes for ${wanted.size} channels" +
                        if (writer.written > 0) {
                            val nowSeconds = fetchedAt / 1000
                            ", from ${hoursFrom(nowSeconds, earliest)} to ${hoursFrom(nowSeconds, latest)}"
                        } else ""
                )
                _version.value++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failedAt[line] = System.currentTimeMillis()
                log("  guide not refreshed: ${e.message}")
            } finally {
                refreshing.set(false)
                _downloading.value = false
            }
        }
    }

    /**
     * What is on [epgChannelId] (or, failing that, stream [streamId]) from now,
     * right-clocked, or empty when nothing is known. Blocking: call off the
     * main thread.
     */
    suspend fun listings(account: XtreamAccount, streamId: Int, epgChannelId: String?, at: Instant): List<EpgListing> =
        withContext(Dispatchers.IO) {
            val line = account.lineKey
            val full = epgChannelId?.takeIf { it.isNotBlank() }
                ?.let { store.programmes(line, it, after = at.epochSecond - 60) }
                .orEmpty()
            if (full.isNotEmpty()) {
                // Learned once per line: the one comparison that tells how far
                // out this panel's short answers are, for the channels the full
                // guide lacks.
                if (!clock.knows(line)) learnFrom(account, streamId, full)
                return@withContext full.map { it.asListing() }
            }
            clock.correct(line, "$streamId", xtream.shortEpg(account, streamId), at)
        }

    /**
     * [epgChannelId]'s programmes from the full guide across [from]..[to], for
     * a guide grid. Empty when the full guide does not have the channel — the
     * grid does not ask `get_short_epg` row by row, which would be a request
     * per channel on screen.
     */
    suspend fun schedule(account: XtreamAccount, epgChannelId: String, from: Long, to: Long): List<XmltvProgramme> =
        if (epgChannelId.isBlank()) emptyList()
        else withContext(Dispatchers.IO) { store.between(account.lineKey, epgChannelId, from, to) }

    /**
     * What is on [epgChannelId] at [at], from the full guide only — for a row
     * in a list of channels, where a request per row is out of the question.
     */
    suspend fun onNow(account: XtreamAccount, epgChannelId: String, at: Long): XmltvProgramme? =
        if (epgChannelId.isBlank()) null
        else withContext(Dispatchers.IO) {
            store.programmes(account.lineKey, epgChannelId, after = at, limit = 1).firstOrNull()?.takeIf { it.start <= at }
        }

    /**
     * The other channels showing whatever [epgChannelId] has on at [at].
     *
     * The answer to "this feed has gone, where else is the match". Entirely a
     * local query: the window around the programme is read from the guide
     * already on the device and grouped by [showingOf], so a failing stream
     * costs no panel request to work around — which matters, because the
     * panel refusing things is usually why we are here.
     *
     * Null when this channel has no guide, when the programme is unknown, or
     * when nothing else carries it. All three are ordinary.
     */
    suspend fun elsewhere(account: XtreamAccount, epgChannelId: String, at: Long): Showing? {
        if (epgChannelId.isBlank()) return null
        return withContext(Dispatchers.IO) {
            val line = account.lineKey
            val playing = store.programmes(line, epgChannelId, after = at, limit = 1)
                .firstOrNull()?.takeIf { it.start <= at } ?: return@withContext null
            // Only what could possibly be the same event: a programme whose
            // start is within the slack the rule allows. Asking for the
            // playing programme's whole span looks equivalent and is not
            // — Strictly Come Dancing runs two and a half hours, and that
            // window held 3,163 rows on a real line against 168 within a
            // quarter of an hour of its start. The rule would have thrown
            // all but those away anyway; the box just had to grind through
            // them first, and did not finish.
            val near = store.startingNear(
                line,
                playing.start - SAME_EVENT_SLACK_SECONDS,
                playing.start + SAME_EVENT_SLACK_SECONDS,
            )
            showingOf(playing, near)
        }
    }

    /**
     * Everything on between [from] and [to], folded once for searching.
     *
     * Built off the main thread and handed back whole, because the screen
     * searches it on every keystroke and the whole point of the index is that
     * the folding has already happened — see [WhatsOnIndex].
     */
    suspend fun whatsOn(account: XtreamAccount, from: Long, to: Long): WhatsOnIndex =
        withContext(Dispatchers.IO) {
            WhatsOnIndex(store.inWindow(account.lineKey, from, to))
        }

    /**
     * What is on between [from] and [to] matching [term], grouped.
     *
     * For one name rather than for typing into: the database sieves on the
     * term's longest word first, so only a handful of programmes are folded
     * instead of the whole window. Finding England on a real line meant
     * folding 35,329 programmes and 5 MB of text to reach 149 of them; this
     * reads those 149.
     *
     * The sieve is a substring and the rule is not, so [mentioning] still
     * decides what actually matches — "eng" will not pass as England here
     * any more than it does anywhere else.
     */
    suspend fun whatIsOnFor(
        account: XtreamAccount,
        term: String,
        from: Long,
        to: Long,
    ): List<Showing> = withContext(Dispatchers.IO) {
        // The longest word is the most selective, and short ones like "fc"
        // would drag half the schedule through the precise rule for nothing.
        val needle = term.trim().split(Regex("\\s+")).maxByOrNull { it.length }.orEmpty()
        if (needle.isBlank()) return@withContext emptyList()
        store.mentioning(account.lineKey, needle, from, to)
            .mentioning(term)
            .showings()
    }

    /** When [account]'s full guide was last fetched, in epoch millis; null when it never has been. */
    suspend fun fetchedAt(account: XtreamAccount): Long? = withContext(Dispatchers.IO) { store.fetchedAt(account.lineKey) }

    private suspend fun learnFrom(account: XtreamAccount, streamId: Int, full: List<XmltvProgramme>) {
        val short = xtream.shortEpg(account, streamId)
        if (short.isNotEmpty()) clock.learnFrom(account.lineKey, short, full)
    }

    /** Forgets [account]'s guide, when it is signed out of. */
    fun forget(account: XtreamAccount) {
        clock.forget(account.lineKey)
        scope.launch { store.clear(account.lineKey) }
    }

    private companion object {
        const val STALE_AFTER_MS = 12 * 60 * 60 * 1000L
        const val RETRY_AFTER_MS = 6 * 60 * 60 * 1000L
    }
}

/**
 * "6 h ago", "in 4 days" — how far a moment is from now, for the guide's own
 * log. Rough on purpose: the question it answers is "does this guide have a
 * past worth catching up on", not "when exactly".
 */
private fun hoursFrom(nowSeconds: Long, seconds: Long): String {
    val delta = seconds - nowSeconds
    val hours = delta / 3600
    return when {
        hours in -1..1 -> "now"
        hours < -48 -> "${-hours / 24} days ago"
        hours < 0 -> "${-hours} h ago"
        hours > 48 -> "in ${hours / 24} days"
        else -> "in $hours h"
    }
}
