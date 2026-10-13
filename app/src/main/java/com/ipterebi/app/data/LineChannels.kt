package com.ipterebi.app.data

import com.ipterebi.core.LiveStream
import com.ipterebi.core.XtreamAccount
import com.ipterebi.core.XtreamClient
import com.ipterebi.core.lineKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Every channel on the line, asked for once and kept.
 *
 * `get_live_streams` with no category is the several-megabyte request the rest
 * of this app is arranged to avoid — the lists load a category at a time for
 * exactly that reason. But two features genuinely need the whole line:
 * searching for a channel outside the loaded category, and finding the other
 * feeds carrying a programme, which are scattered across categories by
 * definition. Those two used to be one fetch each.
 *
 * So it is held here instead, keyed on the line: signing in to a different
 * provider throws it away, because a stream id means nothing off its own
 * panel. The lock is what stops two screens asking at once on a slow line and
 * paying for it twice.
 *
 * Not persisted. Tens of thousands of channels on disk would be a cache to
 * invalidate and a provider's renaming to miss; a launch that needs it pays
 * for it once.
 */
class LineChannels(private val xtream: XtreamClient) {

    private val lock = Mutex()
    private var line: String? = null
    private var channels: List<LiveStream> = emptyList()

    /** The whole line, fetched if this is the first ask for it. */
    suspend fun all(account: XtreamAccount): List<LiveStream> = lock.withLock {
        val key = account.lineKey
        if (key == line && channels.isNotEmpty()) return@withLock channels
        // Already through playableChannels(), so ids are unique and usable as
        // list keys — see XtreamClient.liveStreams.
        val fetched = xtream.liveStreams(account, categoryId = null)
        line = key
        channels = fetched
        fetched
    }

    /** What is held, without asking the panel for it. */
    fun cached(account: XtreamAccount): List<LiveStream> =
        if (account.lineKey == line) channels else emptyList()

    /** On sign-out, because the next line's ids are not these. */
    fun forget() {
        line = null
        channels = emptyList()
    }

    private val _version = MutableStateFlow(0)

    /**
     * Moves each time someone asks for the line to be fetched again, so a
     * screen holding its own copy knows to go back for it.
     *
     * The same shape as `Guide.version`, and for the same reason: the channel
     * list is held in more than one place. This holds it for the search, the
     * team row and the error card; `TvLiveViewModel` keeps its own `Lineup`
     * because it needs the categories with it. A refresh that emptied only
     * this one would leave the screen the viewer is looking at showing
     * yesterday's channels, which is the half they would actually notice.
     */
    val version: StateFlow<Int> = _version.asStateFlow()

    /**
     * Throw away what is held and tell anyone listening.
     *
     * Not [forget]: that is sign-out, where the next line's ids are not these
     * and nothing should go looking for them again. This is the viewer saying
     * the provider has changed something.
     */
    fun refresh() {
        line = null
        channels = emptyList()
        _version.value++
    }
}
