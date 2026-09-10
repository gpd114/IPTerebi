package com.ipterebi.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ipterebi.app.AppContainer
import com.ipterebi.app.data.AccountState
import com.ipterebi.core.XtreamAccount
import com.ipterebi.core.XtreamCategory
import com.ipterebi.core.XtreamClient
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

/**
 * What a library screen needs to know about the kind of thing it lists.
 *
 * Films and series are browsed identically — categories along the top, one
 * category loaded at a time, a search box over what is loaded — and differ only
 * in which calls fetch them and what they are called. This is that difference,
 * as plain values, so one screen and one ViewModel serve both.
 *
 * Plain values rather than an abstract base class on purpose: the ViewModel
 * starts collecting in its constructor, and a base class doing that can call an
 * override before the subclass has finished initialising.
 */
class LibrarySource<T : Any>(
    val categories: suspend XtreamClient.(XtreamAccount) -> List<XtreamCategory>,
    val items: suspend XtreamClient.(XtreamAccount, String?) -> List<T>,
    val nameOf: (T) -> String,
    /** Singular, lower case: "film". */
    val noun: String,
    /** Plural, lower case: "films". */
    val nouns: String,
    /** Handed each loaded list, so a later screen can look an entry up by id. */
    val publish: (List<T>) -> Unit,
)

data class LibraryUiState<T>(
    val categories: List<XtreamCategory> = emptyList(),
    /** Null means every category at once. */
    val selectedCategoryId: String? = null,
    val items: List<T> = emptyList(),
    /** [items] narrowed by [query]. Worked out here so the screen stays dumb. */
    val visible: List<T> = emptyList(),
    val query: String = "",
    /**
     * True from the start: before the first load has even begun — while the
     * saved line is still being read from disk — "not busy, nothing listed"
     * would draw as a genuinely empty category, and every launch would open
     * on a message saying there is nothing here.
     */
    val busy: Boolean = true,
    val error: String? = null,
    val offerFullLoad: Boolean = false,
)

/**
 * A browsable library, one category at a time.
 *
 * The same shape as the channel list and for the same reasons: a whole library
 * is at least as large as a channel list, loading it unfiltered is the request
 * to avoid, and taps outrun a slow panel so the load in flight is cancelled
 * rather than raced. Nothing is asked for until the screen is first shown, so a
 * section nobody opens costs nothing.
 */
class LibraryViewModel<T : Any>(
    private val container: AppContainer,
    private val source: LibrarySource<T>,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState<T>())
    val state: StateFlow<LibraryUiState<T>> = _state.asStateFlow()

    val noun: String get() = source.noun
    val nouns: String get() = source.nouns

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

        viewModelScope.launch {
            _state.map { it.items }.distinctUntilChanged().collect { source.publish(it) }
        }
    }

    fun onQueryChange(value: String) = update { it.copy(query = value) }

    fun selectCategory(categoryId: String?) {
        update { it.copy(selectedCategoryId = categoryId, query = "") }
        startLoad { loadItems(categoryId) }
    }

    fun retry() {
        startLoad {
            if (_state.value.categories.isEmpty()) loadCategories()
            else loadItems(_state.value.selectedCategoryId)
        }
    }

    /** Every state change goes through here, so [LibraryUiState.visible] cannot go stale. */
    private fun update(change: (LibraryUiState<T>) -> LibraryUiState<T>) {
        _state.update { current ->
            val next = change(current)
            next.copy(
                visible = if (next.query.isBlank()) {
                    next.items
                } else {
                    next.items.filter { source.nameOf(it).contains(next.query, ignoreCase = true) }
                },
            )
        }
    }

    private fun startLoad(block: suspend () -> Unit) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { block() }
    }

    private suspend fun loadCategories() {
        val account = account ?: return
        update { it.copy(busy = true, error = null, offerFullLoad = false) }
        try {
            val categories = source.categories(container.xtream, account)
            // Still busy: the first category is about to load, and dropping
            // busy in between would flash "nothing in this category" for a frame.
            update { it.copy(categories = categories) }

            // A line without this section at all is common — plenty of
            // packages are live only — so this is worded as a fact about the
            // line rather than a failure, and the unfiltered load is offered for
            // the panel that lists things without categorising them.
            if (categories.isEmpty()) {
                update {
                    it.copy(
                        busy = false,
                        offerFullLoad = true,
                        error = "This line listed no ${source.noun} categories. It may carry " +
                            "no ${source.nouns} at all — many packages are live television only.",
                    )
                }
                return
            }
            loadItems(categories.first().id)
        } catch (e: XtreamException) {
            update { it.copy(busy = false, error = e.message) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            update { it.copy(busy = false, error = e.message ?: "Could not load ${source.nouns}.") }
        }
    }

    private suspend fun loadItems(categoryId: String?) {
        val account = account ?: return
        update {
            it.copy(busy = true, error = null, offerFullLoad = false, selectedCategoryId = categoryId)
        }
        try {
            val items = source.items(container.xtream, account, categoryId)
            update { it.copy(items = items, busy = false) }
        } catch (e: XtreamException) {
            update { it.copy(busy = false, error = e.message) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            update { it.copy(busy = false, error = e.message ?: "Could not load ${source.nouns}.") }
        }
    }

    companion object {
        fun <T : Any> factory(container: AppContainer, source: LibrarySource<T>) = viewModelFactory {
            initializer { LibraryViewModel(container, source) }
        }
    }
}
