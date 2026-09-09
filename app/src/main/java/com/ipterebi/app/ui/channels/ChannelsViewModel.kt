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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChannelsUiState(
    val categories: List<LiveCategory> = emptyList(),
    /** Null means every category at once. */
    val selectedCategoryId: String? = null,
    val channels: List<LiveStream> = emptyList(),
    val query: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    /**
     * Set when the panel named no categories, so the error can offer the
     * unfiltered load rather than leaving a line with no way in.
     */
    val offerFullLoad: Boolean = false,
) {
    val visibleChannels: List<LiveStream>
        get() = if (query.isBlank()) {
            channels
        } else {
            channels.filter { it.name.contains(query, ignoreCase = true) }
        }
}

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
        viewModelScope.launch {
            container.credentials.state
                .filterIsInstance<AccountState.SignedIn>()
                .collect { signedIn ->
                    val previous = account
                    account = signedIn.account
                    // Reloaded only when the line itself changed. Switching the
                    // stream format or the user agent in settings emits here
                    // too, and re-fetching thousands of channels because
                    // somebody flipped a chip would be absurd.
                    val sameLine = previous != null &&
                        previous.base == signedIn.account.base &&
                        previous.username == signedIn.account.username
                    if (!sameLine) startLoad { loadCategories() }
                }
        }
    }

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

    fun selectCategory(categoryId: String?) {
        _state.update { it.copy(selectedCategoryId = categoryId, query = "") }
        startLoad { loadChannels(categoryId) }
    }

    fun retry() {
        startLoad {
            if (_state.value.categories.isEmpty()) {
                loadCategories()
            } else {
                loadChannels(_state.value.selectedCategoryId)
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
                selectedCategoryId = categoryId,
            )
        }
        try {
            val channels = container.xtream.liveStreams(account, categoryId)
            // Published so the player can resolve a stream id without the list
            // travelling through navigation arguments.
            container.channels.publish(channels)
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
