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
    fun refreshIfStale(account: XtreamAccount, channels: Collection<String>? = null, onMetered: Boolean = false) {
        scope.launch {
            val line = account.lineKey
            val now = System.currentTimeMillis()
            val fetched = store.fetchedAt(line)
            if (fetched != null && now - fetched < STALE_AFTER_MS) return@launch
            if ((failedAt[line] ?: 0L) > now - RETRY_AFTER_MS) return@launch
            if (!onMetered && connectivity?.isActiveNetworkMetered != false) return@launch
            if (!refreshing.compareAndSet(false, true)) return@launch
            try {
                val wanted = (channels ?: xtream.liveStreams(account).map { it.epgChannelId })
                    .filter { it.isNotBlank() }
                    .toHashSet()
                val writer = store.writer(line)
                xtream.xmltv(account) { if (it.channel in wanted) writer.add(it) }
                writer.commit(System.currentTimeMillis())
                failedAt.remove(line)
                log("  guide kept: ${writer.written} programmes for ${wanted.size} channels")
                _version.value++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failedAt[line] = System.currentTimeMillis()
                log("  guide not refreshed: ${e.message}")
            } finally {
                refreshing.set(false)
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
