package com.ipterebi.app.ui.films

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ipterebi.app.AppContainer
import com.ipterebi.app.ui.library.LibraryScreen
import com.ipterebi.app.ui.library.LibrarySource
import com.ipterebi.app.ui.library.LibraryViewModel
import com.ipterebi.app.ui.library.PosterTile
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
        factory = LibraryViewModel.factory(
            container,
            LibrarySource(
                categories = { account -> vodCategories(account) },
                items = { account, categoryId -> vodStreams(account, categoryId) },
                nameOf = { it.name },
                noun = "film",
                nouns = "films",
                publish = container.films::publish,
            ),
        ),
    )

    // Keyed on streamId, which playableFilms() has already made present and unique.
    LibraryScreen(
        title = "Films",
        viewModel = viewModel,
        key = { it.streamId },
        onSettings = onSettings,
    ) { film ->
        PosterTile(
            title = film.name,
            image = film.icon,
            placeholder = Icons.Filled.Movie,
            ratingOutOfTen = film.ratingOutOfTen,
            onClick = { onFilm(film) },
        )
    }
}
