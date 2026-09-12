package com.ipterebi.app.ui.tv

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ipterebi.app.AppContainer
import com.ipterebi.app.data.AccountState
import com.ipterebi.core.Lineup
import com.ipterebi.core.LiveStream
import com.ipterebi.core.XtreamAccount
import com.ipterebi.core.holds
import com.ipterebi.core.lineKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Which list of channels the remote is going through. Favourites and recents
 * come from storage, the rest from the [Lineup].
 */
sealed interface TvGroup {
    val key: String

    data object Favourites : TvGroup { override val key = "fav" }
    data object Recent : TvGroup { override val key = "recent" }
    data object All : TvGroup { override val key = "all" }
    data class Category(val id: String) : TvGroup { override val key = "cat:$id" }

    companion object {
        fun fromKey(key: String?): TvGroup? = when {
            key == null -> null
            key == Favourites.key -> Favourites
            key == Recent.key -> Recent
            key == All.key -> All
            key.startsWith("cat:") -> Category(key.removePrefix("cat:"))
            else -> null
        }
    }
}

data class TvLiveState(
    val account: XtreamAccount? = null,
    /** Every channel on the line. Null until the one big request has answered. */
    val lineup: Lineup? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val favourites: List<LiveStream> = emptyList(),
    val recents: List<LiveStream> = emptyList(),
    /**
     * Recents have been read from storage at least once. Until then an empty
     * list means "not read yet", not "nothing watched", and deciding what to
     * open on would pick the first channel over the one left on last night.
     */
    val recentsRead: Boolean = false,
    /** The group last zapped through, as it was left. */
    val savedGroup: TvGroup? = null,
) {
    fun channelsIn(group: TvGroup): List<LiveStream> = when (group) {
        TvGroup.Favourites -> favourites
        TvGroup.Recent -> recents
        TvGroup.All -> lineup?.all.orEmpty()
        is TvGroup.Category -> lineup?.group(group.id)?.channels.orEmpty()
    }

    fun nameOf(group: TvGroup): String = when (group) {
        TvGroup.Favourites -> "Favourites"
        TvGroup.Recent -> "Recently watched"
        TvGroup.All -> "All channels"
        is TvGroup.Category -> lineup?.group(group.id)?.name ?: "Channels"
    }

    /**
     * A channel by id, as fresh as can be had: the lineup's record when it has
     * arrived — providers rename channels, and a stored recent may be stale —
     * else the stored one, so the channel left on can play before the lineup
     * has loaded.
     */
    fun find(streamId: Int): LiveStream? =
        lineup?.find(streamId) ?: recents.firstOrNull { it.streamId == streamId }
            ?: favourites.firstOrNull { it.streamId == streamId }

    fun isFavourite(streamId: Int) = favourites.holds(streamId)
}

/**
 * The line as a set-top box holds it: the whole channel list, fetched once,
 * with the viewer's favourites and recents alongside.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TvLiveViewModel(private val container: AppContainer, context: Context) : ViewModel() {

    private val prefs = context.applicationContext.getSharedPreferences("tv_live", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(TvLiveState())
    val state: StateFlow<TvLiveState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        val signedIn = container.credentials.state.filterIsInstance<AccountState.SignedIn>()

        viewModelScope.launch {
            signedIn.collect { current ->
                val previous = _state.value.account
                _state.update { it.copy(account = current.account) }
                // Only a different line reloads. Changing the stream format or
                // the user agent emits here too, and neither changes the list.
                val sameLine = previous != null && previous.lineKey == current.account.lineKey
                if (!sameLine) {
                    _state.update {
                        it.copy(
                            lineup = null,
                            savedGroup = TvGroup.fromKey(prefs.getString(groupKey(current.account), null)),
                        )
                    }
                    load()
                }
            }
        }
        viewModelScope.launch {
            signedIn.flatMapLatest { container.channelLists.favourites(it.account) }
                .collect { favourites -> _state.update { it.copy(favourites = favourites) } }
        }
        viewModelScope.launch {
            signedIn.flatMapLatest { container.channelLists.recents(it.account) }
                .collect { recents -> _state.update { it.copy(recents = recents, recentsRead = true) } }
        }
    }

    fun retry() = load()

    private fun load() {
        val account = _state.value.account ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                // Both at once: they are independent, and the channel list is
                // the slow one on any line big enough to matter. Off the main
                // thread: the client fetches on IO but decodes where it is
                // called, and the whole line is megabytes of JSON — a frozen
                // screen for seconds on a box with a phone's processor from
                // years ago.
                val lineup = withContext(Dispatchers.Default) {
                    val categories = async { container.xtream.liveCategories(account) }
                    val channels = container.xtream.liveStreams(account, categoryId = null)
                    Lineup(channels, categories.await())
                }
                _state.update { it.copy(lineup = lineup, loading = false) }
                // The full guide, in the background, for the channels just
                // loaded — whatever the network: a television on the mains is
                // what a guide grid is for, and it has nowhere else to get one.
                container.guide.refreshIfStale(account, lineup.all.map { it.epgChannelId }, onMetered = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Could not load the channel list.") }
            }
        }
    }

    fun toggleFavourite(channel: LiveStream) {
        val account = _state.value.account ?: return
        viewModelScope.launch { container.channelLists.toggleFavourite(account, channel) }
    }

    /** Remembered per line, so the next evening zaps through the same group. */
    fun rememberGroup(group: TvGroup) {
        val account = _state.value.account ?: return
        prefs.edit().putString(groupKey(account), group.key).apply()
        _state.update { it.copy(savedGroup = group) }
    }

    private fun groupKey(account: XtreamAccount) = "group:${account.lineKey}"

    companion object {
        fun factory(container: AppContainer, context: Context) = viewModelFactory {
            initializer { TvLiveViewModel(container, context) }
        }
    }
}
