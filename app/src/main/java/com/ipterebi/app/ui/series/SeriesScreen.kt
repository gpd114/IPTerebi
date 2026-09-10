package com.ipterebi.app.ui.series

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ipterebi.app.AppContainer
import com.ipterebi.app.ui.library.LibraryScreen
import com.ipterebi.app.ui.library.LibrarySource
import com.ipterebi.app.ui.library.LibraryViewModel
import com.ipterebi.app.ui.library.PosterTile
import com.ipterebi.core.Series

/**
 * The series library. Identical to films in how it is browsed; different in
 * that tapping a series opens its seasons rather than playing anything, since a
 * series is a container and only its episodes play.
 */
@Composable
fun SeriesScreen(
    container: AppContainer,
    onSeries: (Series) -> Unit,
    onSettings: () -> Unit,
) {
    val viewModel: LibraryViewModel<Series> = viewModel(
        key = "series",
        factory = LibraryViewModel.factory(
            container,
            LibrarySource(
                categories = { account -> seriesCategories(account) },
                items = { account, categoryId -> series(account, categoryId) },
                nameOf = { it.name },
                noun = "series",
                nouns = "series",
                publish = container.series::publish,
            ),
        ),
    )

    // Keyed on seriesId, which listableSeries() has already made present and unique.
    LibraryScreen(
        title = "Series",
        viewModel = viewModel,
        key = { it.seriesId },
        onSettings = onSettings,
    ) { series ->
        PosterTile(
            title = series.name,
            image = series.cover,
            placeholder = Icons.Filled.VideoLibrary,
            ratingOutOfTen = series.ratingOutOfTen,
            onClick = { onSeries(series) },
        )
    }
}
