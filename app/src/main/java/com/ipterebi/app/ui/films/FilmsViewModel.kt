package com.ipterebi.app.ui.films

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ipterebi.app.AppContainer
import com.ipterebi.app.data.AccountState
import com.ipterebi.core.VodStream
import com.ipterebi.core.XtreamAccount
import com.ipterebi.core.XtreamCategory
import com.ipterebi.core.XtreamException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FilmsUiState(
    val categories: List<XtreamCategory> = emptyList(),
    /** Null means every category at once. */
    val selectedCategoryId: String? = null,
    val films: List<VodStream> = emptyList(),
    val query: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val offerFullLoad: Boolean = false,
) {
    val visibleFilms: List<VodStream>
        get() = if (query.isBlank()) {
            films
        } else {
            films.filter { it.name.contains(query, ignoreCase = true) }
        }
}

/**
 * The film library, one category at a time.
 *
 * The same shape as the channel list and for the same reasons: a whole VOD
 * library is at least as large as a channel list, loading it unfiltered is the
 * request to avoid, and taps outrun a slow panel so the load in flight is
 * cancelled rather than raced. Loaded lazily — nothing is asked for until this
 * screen is first shown, so a user who only watches live television never pays
 * for a film library they do not look at.
 */
class FilmsViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(FilmsUiState())
    val state: StateFlow<FilmsUiState> = _state.asStateFlow()

    private var account: XtreamAccount? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            container.credentials.state
                .filterIsInstance<AccountState.SignedIn>()
                .collect { signedIn ->
                    val previous = account
                    account = signedIn.account
                    val sameLine = previous != null &&
                        previous.base == signedIn.account.base &&
                        previous.username == signedIn.account.username
                    if (!sameLine) startLoad { loadCategories() }
                }
        }

        // Kept current so the player can put a title on a film it was handed by
        // id alone. See MediaRepository for why it is only ever an id.
        viewModelScope.launch {
            _state.map { it.films }.distinctUntilChanged().collect(container.films::publish)
        }
    }

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

    fun selectCategory(categoryId: String?) {
        _state.update { it.copy(selectedCategoryId = categoryId, query = "") }
        startLoad { loadFilms(categoryId) }
    }

    fun retry() {
        startLoad {
            if (_state.value.categories.isEmpty()) {
                loadCategories()
            } else {
                loadFilms(_state.value.selectedCategoryId)
            }
        }
    }

    private fun startLoad(block: suspend () -> Unit) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { block() }
    }

    private suspend fun loadCategories() {
        val account = account ?: return
        _state.update { it.copy(busy = true, error = null, offerFullLoad = false) }
        try {
            val categories = container.xtream.vodCategories(account)
            _state.update { it.copy(categories = categories, busy = false) }

            // A line with no VOD at all is common — plenty of packages are live
            // only — so this is worded as a fact about the line rather than as a
            // failure, and the unfiltered load is offered for the panel that
            // lists films without categorising them.
            if (categories.isEmpty()) {
                _state.update {
                    it.copy(
                        busy = false,
                        offerFullLoad = true,
                        error = "This line listed no film categories. It may carry no " +
                            "films at all — many packages are live television only.",
                    )
                }
                return
            }
            loadFilms(categories.first().id)
        } catch (e: XtreamException) {
            _state.update { it.copy(busy = false, error = e.message) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(busy = false, error = e.message ?: "Could not load films.") }
        }
    }

    private suspend fun loadFilms(categoryId: String?) {
        val account = account ?: return
        _state.update {
            it.copy(busy = true, error = null, offerFullLoad = false, selectedCategoryId = categoryId)
        }
        try {
            val films = container.xtream.vodStreams(account, categoryId)
            _state.update { it.copy(films = films, busy = false) }
        } catch (e: XtreamException) {
            _state.update { it.copy(busy = false, error = e.message) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(busy = false, error = e.message ?: "Could not load films.") }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { FilmsViewModel(container) }
        }
    }
}
