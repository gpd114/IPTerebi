package com.ipterebi.app.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ipterebi.app.AppContainer
import com.ipterebi.app.data.AccountState
import com.ipterebi.core.LiveCategory
import com.ipterebi.core.LiveStream
import com.ipterebi.core.XtreamAccount
import com.ipterebi.core.XtreamException
import com.ipterebi.core.holds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Which list the screen is showing.
 *
 * Favourites and recents sit alongside the panel's own categories rather than
 * inside them: they are drawn from storage, cost no request, and a channel in
 * either can have come from any category on the line.
 */
sealed interface Shelf {
    data object Favourites : Shelf
    data object Recent : Shelf

    /** One of the panel's categories. A null [categoryId] means all of them at once. */
    data class Panel(val categoryId: String?) : Shelf
}

data class ChannelsUiState(
    val categories: List<LiveCategory> = emptyList(),
    val shelf: Shelf = Shelf.Panel(null),
    /** What the panel last returned. Not what is necessarily on screen — see [listed]. */
    val channels: List<LiveStream> = emptyList(),
    val favourites: List<LiveStream> = emptyList(),
    val recents: List<LiveStream> = emptyList(),
    val query: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    /**
     * Set when the panel named no categories, so the error can offer the
     * unfiltered load rather than leaving a line with no way in.
     */
    val offerFullLoad: Boolean = false,
) {
    /** The list the chosen shelf is showing, before the search box narrows it. */
    val listed: List<LiveStream>
        get() = when (shelf) {
            Shelf.Favourites -> favourites
            Shelf.Recent -> recents
            is Shelf.Panel -> channels
        }

    val visibleChannels: List<LiveStream>
        get() = if (query.isBlank()) {
            listed
        } else {
            listed.filter { it.name.contains(query, ignoreCase = true) }
        }

    /** The channel to offer as "carry on watching". Null before anything has been played. */
    val lastWatched: LiveStream? get() = recents.firstOrNull()

    fun isFavourite(streamId: Int): Boolean = favourites.holds(streamId)
}

// flatMapLatest is still marked experimental in coroutines 1.8.1 and has been
// for years. Opted into deliberately rather than left as a warning: the
// alternative is collecting the account and the two stored lists in one
// combine, which loses the "cancel the old line's flow" behaviour that is the
// whole reason for using it.
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChannelsViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(ChannelsUiState())
    val state: StateFlow<ChannelsUiState> = _state.asStateFlow()

    private var account: XtreamAccount? = null

    /**
     * The load in flight. Categories are tapped faster than a panel on cheap
     * hosting answers, and without this the responses race: whichever lands
     * last wins, so tapping A then B can leave B's chip selected above A's
     * channels, and publish A's list for the player to resolve titles against.
     */
    private var loadJob: Job? = null

    init {
        val signedIn = container.credentials.state.filterIsInstance<AccountState.SignedIn>()

        viewModelScope.launch {
            signedIn.collect { current ->
                val previous = account
                account = current.account
                // Reloaded only when the line itself changed. Switching the
                // stream format or the user agent in settings emits here too,
                // and re-fetching thousands of channels because somebody
                // flipped a chip would be absurd.
                val sameLine = previous != null &&
                    previous.base == current.account.base &&
                    previous.username == current.account.username
                if (!sameLine) startLoad { loadCategories() }
            }
        }

        // flatMapLatest so that signing into a different line swaps to that
        // line's lists rather than merging the two.
        viewModelScope.launch {
            signedIn.flatMapLatest { container.channelLists.favourites(it.account) }
                .collect { favourites -> _state.update { it.copy(favourites = favourites) } }
        }
        viewModelScope.launch {
            signedIn.flatMapLatest { container.channelLists.recents(it.account) }
                .collect { recents -> _state.update { it.copy(recents = recents) } }
        }

        // The repository is "the channel list currently on screen", which the
        // player reads to put a name on what it is playing. Keeping it fed from
        // here means a favourite opened from a different category still has a
        // title, which it would not if only panel responses were published.
        viewModelScope.launch {
            _state.map { it.listed }.distinctUntilChanged().collect(container.channels::publish)
        }
    }

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

    /** Favourites and recents come from storage, so neither costs a request. */
    fun selectShelf(shelf: Shelf) {
        _state.update { it.copy(shelf = shelf, query = "", error = null, offerFullLoad = false) }
        if (shelf is Shelf.Panel) startLoad { loadChannels(shelf.categoryId) }
    }

    fun toggleFavourite(channel: LiveStream) {
        val account = account ?: return
        viewModelScope.launch { container.channelLists.toggleFavourite(account, channel) }
    }

    fun retry() {
        startLoad {
            if (_state.value.categories.isEmpty()) {
                loadCategories()
            } else {
                loadChannels((_state.value.shelf as? Shelf.Panel)?.categoryId)
            }
        }
    }

    /** Replaces whatever was loading. See [loadJob]. */
    private fun startLoad(block: suspend () -> Unit) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { block() }
    }

    private suspend fun loadCategories() {
        val account = account ?: return
        _state.update { it.copy(busy = true, error = null, offerFullLoad = false) }
        try {
            val categories = container.xtream.liveCategories(account)
            _state.update { it.copy(categories = categories, busy = false) }

            // A panel that names no categories used to fall through to
            // loadChannels(null), and null means *every* category at once —
            // the several-megabyte, tens-of-thousands-of-entries request this
            // whole screen is arranged to avoid. Firing it by accident, on the
            // panel least likely to cope with it, is the wrong default. Say so
            // instead and let the user ask for it deliberately.
            if (categories.isEmpty()) {
                _state.update {
                    it.copy(
                        busy = false,
                        offerFullLoad = true,
                        error = "This panel listed no live categories. It may have none, " +
                            "or it may not answer get_live_categories at all.",
                    )
                }
                return
            }

            // Opening on the first category rather than on everything: a large
            // line answers get_live_streams with no category filter in several
            // megabytes and tens of thousands of entries, which is a long wait
            // and a lot of memory for a list nobody scrolls to the end of.
            loadChannels(categories.first().id)
        } catch (e: XtreamException) {
            _state.update { it.copy(busy = false, error = e.message) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(busy = false, error = e.message ?: "Could not load categories.") }
        }
    }

    private suspend fun loadChannels(categoryId: String?) {
        val account = account ?: return
        _state.update {
            it.copy(
                busy = true,
                error = null,
                offerFullLoad = false,
                shelf = Shelf.Panel(categoryId),
            )
        }
        try {
            val channels = container.xtream.liveStreams(account, categoryId)
            _state.update { it.copy(channels = channels, busy = false) }
        } catch (e: XtreamException) {
            _state.update { it.copy(busy = false, error = e.message) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(busy = false, error = e.message ?: "Could not load channels.") }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { ChannelsViewModel(container) }
        }
    }
}
