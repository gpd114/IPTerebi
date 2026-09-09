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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
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
    val signedOut: Boolean = false,
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

    init {
        viewModelScope.launch {
            account = container.credentials.state
                .filterIsInstance<AccountState.SignedIn>()
                .first()
                .account
            loadCategories()
        }
    }

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

    fun selectCategory(categoryId: String?) {
        _state.update { it.copy(selectedCategoryId = categoryId, query = "") }
        viewModelScope.launch { loadChannels(categoryId) }
    }

    fun retry() {
        viewModelScope.launch {
            if (_state.value.categories.isEmpty()) {
                loadCategories()
            } else {
                loadChannels(_state.value.selectedCategoryId)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            container.credentials.clear()
            container.channels.publish(emptyList())
            _state.update { it.copy(signedOut = true) }
        }
    }

    private suspend fun loadCategories() {
        val account = account ?: return
        _state.update { it.copy(busy = true, error = null) }
        try {
            val categories = container.xtream.liveCategories(account)
            _state.update { it.copy(categories = categories, busy = false) }
            // Opening on the first category rather than on everything: a large
            // line answers get_live_streams with no category filter in several
            // megabytes and tens of thousands of entries, which is a long wait
            // and a lot of memory for a list nobody scrolls to the end of.
            loadChannels(categories.firstOrNull()?.id)
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
        _state.update { it.copy(busy = true, error = null, selectedCategoryId = categoryId) }
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
