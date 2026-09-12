package com.ipterebi.app.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ipterebi.app.AppContainer
import com.ipterebi.app.data.AccountState
import com.ipterebi.core.LiveStream
import com.ipterebi.core.NameIndex
import com.ipterebi.core.XtreamAccount
import com.ipterebi.core.XtreamCategory
import com.ipterebi.core.XtreamException
import com.ipterebi.core.XmltvProgramme
import com.ipterebi.core.holds
import com.ipterebi.core.searchByName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val categories: List<XtreamCategory> = emptyList(),
    val shelf: Shelf = Shelf.Panel(null),
    /** What the panel last returned. Not what is necessarily on screen — see [listed]. */
    val channels: List<LiveStream> = emptyList(),
    val favourites: List<LiveStream> = emptyList(),
    val recents: List<LiveStream> = emptyList(),
    val query: String = "",
    /**
     * True from the start: before the first load has even begun — while the
     * saved line is still being read from disk — "not busy, nothing listed"
     * would draw as a genuinely empty category, and every launch would open
     * on a message saying there is nothing here.
     */
    val busy: Boolean = true,
    val error: String? = null,
    /**
     * Set when the panel named no categories, so the error can offer the
     * unfiltered load rather than leaving a line with no way in.
     */
    val offerFullLoad: Boolean = false,
    /**
     * Matches for [query] across every channel on the line — or, until the
     * full list has arrived, across [listed]. Null while not searching.
     */
    val results: List<LiveStream>? = null,
    /** The full channel list is being fetched for the first search. */
    val indexing: Boolean = false,
    /** Why search is only covering [listed], when the full list could not be had. */
    val searchNote: String? = null,
) {
    /** The list the chosen shelf is showing, when nothing is being searched. */
    val listed: List<LiveStream>
        get() = when (shelf) {
            Shelf.Favourites -> favourites
            Shelf.Recent -> recents
            is Shelf.Panel -> channels
        }

    val searching: Boolean get() = query.isNotBlank()

    /** What the list on screen is, in words: the TV guide is headed with it. */
    val listTitle: String
        get() = when {
            searching -> "Search: $query"
            shelf == Shelf.Favourites -> "Favourites"
            shelf == Shelf.Recent -> "Recently watched"
            shelf is Shelf.Panel && shelf.categoryId == null -> "All channels"
            shelf is Shelf.Panel -> categories.firstOrNull { it.id == shelf.categoryId }?.name ?: "Channels"
            else -> "Channels"
        }

    /**
     * What is on screen. While searching that is [results], which cover every
     * channel rather than the shelf underneath — and, for the moment before the
     * first results are ready, the shelf as it was rather than an empty list
     * that would read as "nothing matches".
     */
    val visibleChannels: List<LiveStream>
        get() = if (searching) results ?: listed else listed

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

    /**
     * Every channel on the line, with names ready to search, fetched the first
     * time anything is searched and kept for as long as this screen lives.
     *
     * The Xtream API has no search. The only way to find a channel in a
     * category you are not looking at is to hold the whole list — the
     * several-megabyte request everything else here is arranged to avoid. So
     * it is made once, only when somebody asks to search, and never to browse.
     */
    private var index: NameIndex<LiveStream>? = null
    private var indexJob: Job? = null

    /**
     * Set when the full list could not be had, so that every keystroke does not
     * ask again. Cleared when the search box is emptied, so starting a new
     * search tries once more.
     */
    private var indexFailed = false
    private var searchJob: Job? = null

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
                if (!sameLine) {
                    // Another line's channels are not this line's channels.
                    index = null
                    indexJob?.cancel()
                    indexFailed = false
                    _state.update { it.copy(query = "", results = null, searchNote = null) }
                    startLoad { loadCategories() }
                }
                // The full guide, in the background, when it is due and the
                // phone is on Wi-Fi: it is tens of megabytes on a big line.
                // Every change of settings asks, which costs nothing when fresh.
                container.guide.refreshIfStale(current.account)
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
        // here means a favourite or a search result from a category that is not
        // loaded still has a title, which it would not if only panel responses
        // were published.
        viewModelScope.launch {
            _state.map { it.visibleChannels }.distinctUntilChanged().collect(container.channels::publish)
        }
        viewModelScope.launch {
            _state.map { it.listTitle }.distinctUntilChanged().collect { container.channelsTitle.value = it }
        }
    }

    fun onQueryChange(value: String) {
        // The previous results stay up while the next are worked out, rather
        // than blanking the list on every key.
        _state.update { it.copy(query = value, results = if (value.isBlank()) null else it.results) }
        searchJob?.cancel()
        if (value.isBlank()) {
            indexFailed = false
            _state.update { it.copy(searchNote = null) }
            return
        }
        ensureIndex()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            runSearch(value)
        }
    }

    /**
     * Matches [query] across the index, or across what is on screen when there
     * is no index yet. Off the main thread: even with names normalised up front,
     * tens of thousands of comparisons per key is not work for the UI thread.
     */
    private suspend fun runSearch(query: String) {
        val source = index
        val listed = _state.value.listed
        val results = withContext(Dispatchers.Default) {
            source?.search(query) ?: listed.searchByName(query) { it.name }
        }
        // A slow search must not overwrite the results of a newer one.
        _state.update { if (it.query == query) it.copy(results = results) else it }
    }

    private fun ensureIndex() {
        if (index != null || indexFailed || indexJob?.isActive == true) return
        val account = account ?: return
        indexJob = viewModelScope.launch {
            _state.update { it.copy(indexing = true, searchNote = null) }
            try {
                val everything = container.xtream.liveStreams(account, categoryId = null)
                index = withContext(Dispatchers.Default) { NameIndex(everything) { it.name } }
                _state.update { it.copy(indexing = false) }
                _state.value.query.takeIf { it.isNotBlank() }?.let { runSearch(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                indexFailed = true
                _state.update {
                    it.copy(
                        indexing = false,
                        searchNote = "Could not fetch every channel to search " +
                            "(${e.message ?: "no reason given"}), so these are matches from this list only.",
                    )
                }
            }
        }
    }

    /** Favourites and recents come from storage, so neither costs a request. */
    fun selectShelf(shelf: Shelf) {
        _state.update { it.copy(shelf = shelf, query = "", error = null, offerFullLoad = false) }
        if (shelf is Shelf.Panel) startLoad { loadChannels(shelf.categoryId) }
    }

    /** Moves on when the full guide is refreshed, so rows look again. */
    val guideVersion = container.guide.version

    /** What is on [channel] at [at] (unix seconds), from the full guide on the device. */
    suspend fun onNow(channel: LiveStream, at: Long): XmltvProgramme? =
        account?.let { container.guide.onNow(it, channel.epgChannelId, at) }

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
            // Still busy: the first category is about to load, and dropping
            // busy in between would flash "nothing in this category" for a frame.
            _state.update { it.copy(categories = categories) }

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

/**
 * How long typing has to pause before a search runs. Short enough to feel
 * immediate, long enough that typing "bbc one" searches once rather than seven
 * times over every channel on the line.
 */
private const val SEARCH_DEBOUNCE_MS = 150L
