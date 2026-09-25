package com.ipterebi.app.data

import android.content.Context
import android.net.ConnectivityManager
import com.ipterebi.core.EpgListing
import com.ipterebi.core.GuideClock
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
     * [channels] are the line's `epg_channel_id`s, to keep only those; when not
     * given, the channel list is fetched to find them.
     */
    fun refreshIfStale(account: XtreamAccount, channels: Collection<String>? = null, onMetered: Boolean = false) =
        refresh(account, channels, onMetered, asked = false)

    /**
     * Fetches [account]'s full guide now, because someone asked: whatever the
     * network, however recent the last one, and whatever the last failure.
     */
    fun refreshNow(account: XtreamAccount) = refresh(account, null, onMetered = true, asked = true)

    private val _downloading = MutableStateFlow(false)

    /** A refresh is under way, for a screen that offered one. */
    val downloading: StateFlow<Boolean> = _downloading.asStateFlow()

    private fun refresh(account: XtreamAccount, channels: Collection<String>?, onMetered: Boolean, asked: Boolean) {
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
                val wanted = (channels ?: xtream.liveStreams(account).map { it.epgChannelId })
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
                writer.commit(fetchedAt)
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
