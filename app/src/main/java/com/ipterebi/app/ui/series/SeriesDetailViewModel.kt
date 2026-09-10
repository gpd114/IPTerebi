package com.ipterebi.app.ui.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ipterebi.app.AppContainer
import com.ipterebi.app.data.AccountState
import com.ipterebi.app.data.EpisodeListing
import com.ipterebi.core.SeriesDetail
import com.ipterebi.core.XtreamException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SeriesDetailUiState(
    /** Shown in the top bar before the detail arrives, when the list we came from knew it. */
    val name: String = "",
    val detail: SeriesDetail? = null,
    /** An index into [SeriesDetail.seasons], not a season number — numbers can skip. */
    val selectedSeason: Int = 0,
    val busy: Boolean = true,
    val error: String? = null,
)

/**
 * One series: its seasons and their episodes.
 *
 * Navigated to with the series id alone, for the same reason as the player —
 * see MediaRepository. The name is looked up from the list on screen so the top
 * bar is not blank while the episodes load, and after a process death, when
 * that list has gone, the detail response supplies it instead.
 */
class SeriesDetailViewModel(
    private val container: AppContainer,
    private val seriesId: Int,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SeriesDetailUiState(name = container.series.find(seriesId)?.name.orEmpty())
    )
    val state: StateFlow<SeriesDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun selectSeason(index: Int) = _state.update { it.copy(selectedSeason = index) }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val account = container.credentials.state
                    .filterIsInstance<AccountState.SignedIn>()
                    .first()
                    .account
                val detail = container.xtream.seriesDetail(account, seriesId, _state.value.name)

                // Every episode, not just the season on screen: switching season
                // should not leave the player unable to title what it is handed.
                container.episodes.publish(
                    detail.seasons.flatMap { season ->
                        season.episodes.map { EpisodeListing(detail.name, it) }
                    }
                )
                _state.update {
                    it.copy(
                        name = detail.name,
                        detail = detail,
                        busy = false,
                        selectedSeason = it.selectedSeason.coerceIn(0, (detail.seasons.size - 1).coerceAtLeast(0)),
                    )
                }
            } catch (e: XtreamException) {
                _state.update { it.copy(busy = false, error = e.message) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "Could not load the episodes.") }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer, seriesId: Int) = viewModelFactory {
            initializer { SeriesDetailViewModel(container, seriesId) }
        }
    }
}
