package com.ipterebi.app.ui.tv

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ipterebi.app.AppContainer
import com.ipterebi.app.ui.library.LibraryViewModel
import com.ipterebi.app.ui.library.asFilm
import com.ipterebi.app.ui.library.asSeries
import com.ipterebi.app.ui.library.filmsSource
import com.ipterebi.app.ui.library.seriesSource
import com.ipterebi.core.Series
import com.ipterebi.core.VodStream

/**
 * Films and series as a television draws them.
 *
 * The same two libraries the phone browses — `filmsSource` and `seriesSource`
 * are shared, so a change to what a film is or what goes in a list reaches
 * both — with [TvLibraryScreen] doing the drawing instead of `LibraryScreen`.
 *
 * The view models are keyed apart from the phone's by name. They are the same
 * class with the same source, and a shared key would hand the TV screen the
 * phone screen's state and vice versa, which only shows on a build where both
 * exist: this one.
 */
@Composable
fun TvFilmsScreen(
    container: AppContainer,
    onFilm: (VodStream) -> Unit,
) {
    val viewModel: LibraryViewModel<VodStream> = viewModel(
        key = "tv-films",
        factory = LibraryViewModel.factory(container, filmsSource(container)),
    )
    TvLibraryScreen(
        viewModel = viewModel,
        key = { it.streamId },
        nameOf = { it.name },
        imageOf = { it.icon },
        ratingOf = { it.ratingOutOfTen },
        // get_vod_streams says nothing about a film beyond its name and its
        // rating; the plot needs get_vod_info, which is a request per film and
        // not worth one for a cursor passing over a grid.
        plotOf = { "" },
        onOpen = onFilm,
        onOpenListed = { entry -> onFilm(entry.asFilm()) },
    )
}

@Composable
fun TvSeriesScreen(
    container: AppContainer,
    onSeries: (Series) -> Unit,
) {
    val viewModel: LibraryViewModel<Series> = viewModel(
        key = "tv-series",
        factory = LibraryViewModel.factory(container, seriesSource(container)),
    )
    TvLibraryScreen(
        viewModel = viewModel,
        key = { it.seriesId },
        nameOf = { it.name },
        imageOf = { it.cover },
        ratingOf = { it.ratingOutOfTen },
        // A series does carry its plot in the list response, which is why the
        // panel at the top is worth having at all.
        plotOf = { it.plot },
        onOpen = onSeries,
        onOpenListed = { entry -> onSeries(entry.asSeries()) },
    )
}
