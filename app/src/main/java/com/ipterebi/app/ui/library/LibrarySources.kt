package com.ipterebi.app.ui.library

import com.ipterebi.app.AppContainer
import com.ipterebi.core.ListedItem
import com.ipterebi.core.SavedKind
import com.ipterebi.core.Series
import com.ipterebi.core.VodStream

/**
 * What a films library is and what a series library is, as values.
 *
 * Lifted out of the screens that used to hold them because the TV build draws
 * the same two libraries a different way, and a second copy of "this is how a
 * film becomes a list entry" would drift from this one the first time either
 * changed. The screens are two; the libraries are still one each.
 */
fun filmsSource(container: AppContainer) = LibrarySource(
    categories = { account -> vodCategories(account) },
    items = { account, categoryId -> vodStreams(account, categoryId) },
    nameOf = { it.name },
    noun = "film",
    nouns = "films",
    publish = container.films::publish,
    kind = SavedKind.FILM,
    // The extension travels with it: a film cannot be played from a list
    // without knowing whether the panel holds an mp4 or an mkv, and the list
    // it came from will be gone by then.
    toListed = { film ->
        ListedItem(
            kind = SavedKind.FILM,
            id = film.streamId.toString(),
            name = film.name,
            poster = film.icon,
            extension = film.containerExtension,
        )
    },
)

fun seriesSource(container: AppContainer) = LibrarySource(
    categories = { account -> seriesCategories(account) },
    items = { account, categoryId -> series(account, categoryId) },
    nameOf = { it.name },
    noun = "series",
    nouns = "series",
    publish = container.series::publish,
    kind = SavedKind.SERIES,
    // No extension: a series opens a screen, not a stream.
    toListed = { series ->
        ListedItem(
            kind = SavedKind.SERIES,
            id = series.seriesId.toString(),
            name = series.name,
            poster = series.cover,
        )
    },
)

/** A film the panel is no longer on hand to describe, rebuilt from a list entry. */
fun ListedItem.asFilm() = VodStream(
    streamId = id.toIntOrNull() ?: 0,
    name = name,
    containerExtension = extension,
    icon = poster,
)

/** The same for a series, which needs only enough to open its own screen. */
fun ListedItem.asSeries() = Series(
    seriesId = id.toIntOrNull() ?: 0,
    name = name,
    cover = poster,
)
