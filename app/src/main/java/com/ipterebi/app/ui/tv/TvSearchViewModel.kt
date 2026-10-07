package com.ipterebi.app.ui.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ipterebi.app.AppContainer
import com.ipterebi.app.data.AccountState
import com.ipterebi.core.LiveStream
import com.ipterebi.core.NameIndex
import com.ipterebi.core.Showing
import com.ipterebi.core.WhatsOnIndex
import com.ipterebi.core.XtreamAccount
import com.ipterebi.core.hiddenIds
import com.ipterebi.core.searchByName
import com.ipterebi.core.streams
import com.ipterebi.core.withoutHidden
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A match, and the channels of this line carrying it. */
data class TvShowingOn(val showing: Showing, val channels: List<LiveStream>)

data class TvSearchUiState(
    val query: String = "",
    /** Events on now or coming, widest-carried first. */
    val showings: List<TvShowingOn> = emptyList(),
    /** Channels whose *name* matches. */
    val channels: List<LiveStream> = emptyList(),
    /** The whole line is being fetched, which is the one slow part. */
    val preparing: Boolean = false,
    /**
     * The sport on now, shown before anything is typed.
     *
     * A search screen that opens on nothing is a screen that asks a question
     * when it could answer one � and on a D-pad the typing is the expensive
     * part, so the answer somebody wanted is often already here.
     */
    val sport: List<TvShowingOn> = emptyList(),
    val note: String? = null,
) {
    val empty: Boolean get() = showings.isEmpty() && channels.isEmpty()
}

/**
 * Searching, on a television.
 *
 * **The same two answers the phone gives**, and for the same reason: a search
 * for a team matches no channel *name* — nothing is called Croatia — so the
 * events are reported above the channels rather than instead of them. That
 * was a bug on the phone once: "0 channels match" drew over the match it had
 * just found.
 *
 * Nothing here is new thinking. `WhatsOnIndex` folds the guide once,
 * `NameIndex` folds the channel names once, and `LineChannels` is the shared
 * cache so this and the channel list make one several-megabyte request rather
 * than one each. The TV having had no search at all is what this fixes.
 */
class TvSearchViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(TvSearchUiState())
    val state: StateFlow<TvSearchUiState> = _state.asStateFlow()

    private var account: XtreamAccount? = null
    private var names: NameIndex<LiveStream>? = null
    private var whatsOn: WhatsOnIndex? = null
    private var line: List<LiveStream> = emptyList()
    private var preparing: Job? = null
    private var searching: Job? = null
    private var failed = false

    init {
        viewModelScope.launch {
            account = (container.credentials.state.first { it !is AccountState.Loading }
                as? AccountState.SignedIn)?.account
            prepare()
        }
    }

    fun onQuery(query: String) {
        _state.update { it.copy(query = query) }
        prepare()
        searching?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(showings = emptyList(), channels = emptyList()) }
            return
        }
        searching = viewModelScope.launch { run(query) }
    }

    private suspend fun run(query: String) {
        val hidden = account?.let { container.channelLists.hidden(it).first() }.orEmpty().hiddenIds()
        val visible = line.withoutHidden(hidden)
        val byName = withContext(Dispatchers.Default) {
            names?.search(query) ?: visible.searchByName(query) { it.name }
        }
        val events = withContext(Dispatchers.Default) {
            whatsOn?.search(query).orEmpty()
                .map { it to it.streams(visible) }
                // Nothing to press is nothing to show: a programme whose
                // channels are all hidden, or which this line does not carry
                // under that guide id at all.
                .filter { (_, streams) -> streams.isNotEmpty() }
                .take(MAX_SHOWINGS)
                .map { (showing, streams) -> TvShowingOn(showing, streams) }
        }
        // A slow search must not overwrite a newer one's results.
        _state.update {
            if (it.query == query) {
                it.copy(showings = events, channels = byName.withoutHidden(hidden))
            } else {
                it
            }
        }
    }

    /**
     * Fetches the whole line and folds both indexes, once.
     *
     * Started as soon as the screen opens rather than on the first keystroke,
     * because on a television the typing is the slow part and there is no
     * reason to make somebody wait for a request they could have been having
     * while they hunted for the letters.
     */
    private fun prepare() {
        if (names != null || failed || preparing?.isActive == true) return
        val account = account ?: return
        preparing = viewModelScope.launch {
            _state.update { it.copy(preparing = true, note = null) }
            try {
                line = container.lineChannels.all(account)
                names = withContext(Dispatchers.Default) { NameIndex(line) { it.name } }
                val now = System.currentTimeMillis() / 1000
                whatsOn = container.guide.whatsOn(account, now, now + WHATS_ON_WINDOW_SECONDS)
                _state.update { it.copy(preparing = false) }
                showSport()
                _state.value.query.takeIf { it.isNotBlank() }?.let { run(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
                _state.update {
                    it.copy(
                        preparing = false,
                        note = "Could not fetch every channel to search " +
                            "(${e.message ?: "no reason given"}).",
                    )
                }
            }
        }
    }


    /**
     * What is on now that the guide calls sport, for the screen to open on.
     *
     * Three hours rather than this instant: a match that starts in twenty
     * minutes is the thing somebody opening this at ten to three wants, and a
     * list that empties the moment one finishes is a list nobody trusts.
     */
    private suspend fun showSport() {
        // Not even asked for when it is off: the query is local and cheap,
        // but a list nobody will see is work nobody asked for.
        if (SearchOpensOn.choice != SearchOpensOn.Choice.SPORT) return
        val account = account ?: return
        val now = System.currentTimeMillis() / 1000
        val hidden = container.channelLists.hidden(account).first().hiddenIds()
        val visible = line.withoutHidden(hidden)
        val on = container.guide.sportOn(account, now, now + SPORT_WINDOW_SECONDS)
            .map { it to it.streams(visible) }
            .filter { (_, streams) -> streams.isNotEmpty() }
            .take(MAX_SHOWINGS)
            .map { (showing, streams) -> TvShowingOn(showing, streams) }
        _state.update { it.copy(sport = on) }
    }
    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { TvSearchViewModel(container) }
        }
    }
}

/**
 * How far ahead a search looks.
 *
 * A day, which is what the guide dependably holds: the same line published 41
 * hours ahead one day and 17 the next, so anything longer promises more than a
 * provider delivers.
 */
private const val WHATS_ON_WINDOW_SECONDS = 24L * 3600

/**
 * How many events are worth listing.
 *
 * Six. The point of the list is to pick from a short one — `Live: College
 * Football` was on 127 channels on a real line — and on a ten-foot screen a
 * seventh row is below the fold anyway.
 */
private const val MAX_SHOWINGS = 6

/**
 * How far ahead "sport on now" looks.
 *
 * Three hours. A match starting in twenty minutes is what somebody opening
 * this at ten to three wants, and a list that empties the moment one finishes
 * is a list nobody trusts.
 */
private const val SPORT_WINDOW_SECONDS = 3L * 3600
