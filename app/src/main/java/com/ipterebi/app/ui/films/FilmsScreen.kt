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
import com.ipterebi.core.ListedItem
import com.ipterebi.core.SavedKind
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
                kind = SavedKind.FILM,
                // The extension travels with it: a film cannot be played from
                // a list without knowing whether the panel holds an mp4 or an
                // mkv, and the list it came from will be gone by then.
                toListed = { film ->
                    ListedItem(
                        kind = SavedKind.FILM,
                        id = film.streamId.toString(),
                        name = film.name,
                        poster = film.icon,
                        extension = film.containerExtension,
                    )
                },
            ),
        ),
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
        onOpenListed = { entry ->
            onFilm(
                VodStream(
                    streamId = entry.id.toIntOrNull() ?: 0,
                    name = entry.name,
                    containerExtension = entry.extension,
                    icon = entry.poster,
                )
            )
        },
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
