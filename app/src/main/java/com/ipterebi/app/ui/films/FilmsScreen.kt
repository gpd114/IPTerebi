package com.ipterebi.app.ui.films

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ipterebi.app.AppContainer
import com.ipterebi.app.ui.library.LibraryScreen
import com.ipterebi.app.ui.library.LibraryViewModel
import com.ipterebi.app.ui.library.PosterTile
import com.ipterebi.app.ui.library.asFilm
import com.ipterebi.app.ui.library.filmsSource
import com.ipterebi.core.VodStream

@Composable
fun FilmsScreen(
    container: AppContainer,
    onFilm: (VodStream) -> Unit,
    onSettings: () -> Unit,
) {
    val viewModel: LibraryViewModel<VodStream> = viewModel(
        // Keyed explicitly: the class alone is the default key, and it is the
        // same class for every library.
        key = "films",
        factory = LibraryViewModel.factory(container, filmsSource(container)),
    )

    // Keyed on streamId, which playableFilms() has already made present and unique.
    LibraryScreen(
        title = "Films",
        viewModel = viewModel,
        key = { it.streamId },
        onSettings = onSettings,
        placeholder = Icons.Filled.Movie,
        // Opened from one of the viewer's own lists, where all that survives
        // is what was stored. Enough: a film is played by its id and its
        // container, and both were kept for exactly this.
        onOpenListed = { entry -> onFilm(entry.asFilm()) },
    ) { film, hold ->
        PosterTile(
            title = film.name,
            image = film.icon,
            placeholder = Icons.Filled.Movie,
            ratingOutOfTen = film.ratingOutOfTen,
            onClick = { onFilm(film) },
            onLongPress = hold,
        )
    }
}
