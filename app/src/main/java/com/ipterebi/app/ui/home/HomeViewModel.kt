package com.ipterebi.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ipterebi.app.AppContainer
import com.ipterebi.app.data.AccountState
import com.ipterebi.core.LiveStream
import com.ipterebi.core.WatchKind
import com.ipterebi.core.WatchedItem
import com.ipterebi.core.XtreamAccount
import com.ipterebi.core.ofKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val account: XtreamAccount? = null,
    /** Both kept: which one the row shows is read from HomeChannels as it draws. */
    val favourites: List<LiveStream> = emptyList(),
    val recents: List<LiveStream> = emptyList(),
    val films: List<WatchedItem> = emptyList(),
    val episodes: List<WatchedItem> = emptyList(),
    /**
     * True until the stored lists have actually been read. Without it every
     * launch draws three "nothing here yet" panels for the moment before the
     * disk answers, which reads as an app that has forgotten everything.
     */
    val loading: Boolean = true,
)

/**
 * The home screen's three rows, all of them from the device.
 *
 * Nothing here asks the panel for anything: favourites and recents are stored
 * per line, what is on comes from the guide already downloaded, and Continue
 * watching is a local list of positions. That is the point of the screen —
 * opening the app costs no requests and works before the line has answered.
 */
class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val signedIn = container.credentials.state.filterIsInstance<AccountState.SignedIn>()

    init {
        @OptIn(ExperimentalCoroutinesApi::class)
        viewModelScope.launch {
            signedIn
                .map { it.account }
                .distinctUntilChanged { old, new -> old.lineMatches(new) }
                .flatMapLatest { account ->
                    combine(
                        container.channelLists.favourites(account),
                        container.channelLists.recents(account),
                        container.watched.watching(account),
                    ) { favourites, recents, watching ->
                        Triple(account, favourites to recents, watching)
                    }
                }
                .collect { (account, lists, watching) ->
                    val (favourites, recents) = lists
                    _state.update {
                        it.copy(
                            account = account,
                            favourites = favourites,
                            recents = recents,
                            films = watching.ofKind(WatchKind.FILM),
                            episodes = watching.ofKind(WatchKind.EPISODE),
                            loading = false,
                        )
                    }
                }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { HomeViewModel(container) }
        }
    }
}

/**
 * Whether two accounts are the same line. The stored lists are keyed on that,
 * so a changed user agent or stream format must not throw the rows away and
 * read them again.
 */
private fun XtreamAccount.lineMatches(other: XtreamAccount): Boolean =
    base == other.base && username == other.username
