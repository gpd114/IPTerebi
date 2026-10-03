package com.ipterebi.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ipterebi.app.AppContainer
import com.ipterebi.app.data.AccountState
import com.ipterebi.core.LiveStream
import com.ipterebi.core.hiddenIds
import com.ipterebi.core.withoutHidden
import com.ipterebi.core.OwnList
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import com.ipterebi.core.TeamMatch
import com.ipterebi.core.teamMatch
import com.ipterebi.core.streams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

data class HomeUiState(
    val account: XtreamAccount? = null,
    /** Both kept: which one the row shows is read from HomeChannels as it draws. */
    val favourites: List<LiveStream> = emptyList(),
    val recents: List<LiveStream> = emptyList(),
    val films: List<WatchedItem> = emptyList(),
    val episodes: List<WatchedItem> = emptyList(),
    /**
     * The viewer's own lists, in the order they were made. Empty ones are
     * dropped: a row with nothing in it on the screen you see most is a row
     * that has to explain itself, and a list you just made is one tap from
     * being filled anyway.
     */
    val lists: List<OwnList> = emptyList(),
    /**
     * True until the stored lists have actually been read. Without it every
     * launch draws three "nothing here yet" panels for the moment before the
     * disk answers, which reads as an app that has forgotten everything.
     */
    val loading: Boolean = true,
    /** The team's name, blank when none is set. */
    val team: String = "",
    /** Their match, on now or next, with the channels carrying it. */
    val teamMatch: TeamMatch? = null,
    val teamChannels: List<LiveStream> = emptyList(),
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

    /**
     * Where the team is on, found once per launch and whenever the name
     * changes.
     *
     * This is the one thing on Home that can cost a request, and it is the
     * exception rather than a change of mind. Mapping the guide's channel
     * ids to things that can be pressed needs the channel list, which is
     * several megabytes on a real line — the reason there is no "recently
     * added films" row. The difference is that nothing happens unless a team
     * has actually been named: setting one is asking for precisely this, in
     * advance, so that the moment a feed dies there is a list already
     * waiting. Everyone who has not set one pays nothing, and the rest of
     * the screen still draws before the panel answers.
     */
    private fun findTeam(account: XtreamAccount, team: String) {
        viewModelScope.launch {
            if (team.isBlank()) {
                _state.update { it.copy(team = team, teamMatch = null, teamChannels = emptyList()) }
                return@launch
            }
            val found = runCatching {
                val now = System.currentTimeMillis() / 1000
                val guide = container.guide.whatsOn(account, now, now + TEAM_WINDOW_SECONDS)
                val match = withContext(Dispatchers.Default) {
                    teamMatch(guide.search(team), now)
                } ?: return@runCatching null
                val line = container.lineChannels.all(account)
                    .withoutHidden(container.channelLists.hidden(account).first().hiddenIds())
                match to match.showing.streams(line)
            }.getOrNull()
            _state.update {
                it.copy(
                    team = team,
                    teamMatch = found?.first,
                    teamChannels = found?.second.orEmpty(),
                )
            }
        }
    }

    init {
        // The name is one short string in plain preferences, so this is a
        // cheap flow to sit on: changing it in Settings refreshes the row on
        // the way back without a reload of anything else.
        viewModelScope.launch {
            // The guide version is in here because the row has to look
            // again when the guide arrives. On a first run Home is built
            // before the full guide has downloaded, so the first answer is
            // always "nothing for them" — and without this it stayed that
            // way until something else happened to rebuild the screen.
            combine(
                signedIn.map { it.account },
                container.team.team,
                container.guide.version,
            ) { account, team, _ -> account to team }
                .collect { (account, team) -> findTeam(account, team) }
        }
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
                        container.lists.lists(account),
                        container.channelLists.hidden(account),
                    ) { values ->
                        @Suppress("UNCHECKED_CAST")
                        Home(
                            account = account,
                            // Hidden here as well as on the channel list: these
                            // rows are drawn from the stored copies, so a
                            // channel hidden after it was starred would
                            // otherwise still greet you on the screen you see
                            // most.
                            favourites = (values[0] as List<LiveStream>)
                                .withoutHidden((values[4] as List<LiveStream>).hiddenIds()),
                            recents = (values[1] as List<LiveStream>)
                                .withoutHidden((values[4] as List<LiveStream>).hiddenIds()),
                            watching = values[2] as List<WatchedItem>,
                            lists = values[3] as List<OwnList>,
                        )
                    }
                }
                .collect { home ->
                    _state.update {
                        it.copy(
                            account = home.account,
                            favourites = home.favourites,
                            recents = home.recents,
                            films = home.watching.ofKind(WatchKind.FILM),
                            episodes = home.watching.ofKind(WatchKind.EPISODE),
                            lists = home.lists.filter { list -> list.items.isNotEmpty() },
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

/**
 * One frame of everything the home screen shows, so the four flows above
 * combine into something with names rather than a nest of pairs.
 */
private class Home(
    val account: XtreamAccount,
    val favourites: List<LiveStream>,
    val recents: List<LiveStream>,
    val watching: List<WatchedItem>,
    val lists: List<OwnList>,
)

/**
 * How far ahead the team row looks.
 *
 * Four days, rather than the one a search uses. A row that says "nothing
 * this week" the moment a match finishes is a row nobody trusts, and the
 * next fixture is the useful thing between matches. The guide reaches only
 * as far as the provider publishes — 17 hours on one measurement and 41 on
 * another — so this is a ceiling and often not reached.
 */
private const val TEAM_WINDOW_SECONDS = 4L * 24 * 3600
