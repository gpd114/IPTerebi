package com.ipterebi.core

import kotlinx.serialization.Serializable

/**
 * Lists the viewer makes and fills themselves, beside the ones the provider
 * ships.
 *
 * A provider's categories are its own filing, and on a real line there are
 * dozens of them with names like `EN - 2020 & OLD`. These are the other
 * thing: a handful of lists someone names and puts things in.
 *
 * Films and series only. Channels already have Favourites, and two ideas for
 * the same act — starring a channel, adding a film — would be one idea too
 * many. The shape here would hold a channel without changing, if that turns
 * out to be wanted.
 *
 * Everything decidable about them lives here: what a name may be, what
 * happens when the same thing is added twice, and what order things come back
 * in. All of it can be got wrong quietly — a list that silently drops an
 * entry, two lists with the same name — so all of it is tested rather than
 * looked at on a screen.
 */
@Serializable
enum class SavedKind { FILM, SERIES }

/**
 * One thing in a list.
 *
 * The name, the poster and the container are kept beside the id for the same
 * reason [WatchedItem] keeps them: the list a film came from is usually gone
 * by the time someone opens it again. A row built from storage alone has to
 * be drawable and playable, or a home screen on a cold start shows blank
 * cards that do nothing.
 */
@Serializable
data class ListedItem(
    val kind: SavedKind,
    /** The panel's id: a number for a film, and not necessarily for a series. */
    val id: String,
    val name: String,
    val poster: String = "",
    /**
     * A film's container — mp4, mkv, avi — because its URL cannot be built
     * without it. Blank for a series, which opens a screen rather than a
     * stream.
     */
    val extension: String = "",
    /** When it was put in, unix milliseconds. */
    val addedAt: Long = 0,
)

/** A list with a name and the things in it, oldest first. */
@Serializable
data class OwnList(
    val name: String,
    val items: List<ListedItem> = emptyList(),
) {
    fun holds(kind: SavedKind, id: String): Boolean =
        items.any { it.kind == kind && it.id == id }
}

/** The longest a list name may be. Long enough to be a sentence, short enough to be a chip. */
const val MAX_LIST_NAME = 40

/** At most this many lists. Past it the shelf is worse than no shelf. */
const val MAX_LISTS = 20

/**
 * A name as it will be stored, or null when there is nothing usable in it.
 *
 * Whitespace is collapsed and trimmed, because a name typed on a remote
 * arrives with whatever the keyboard left behind, and " Saturday " and
 * "Saturday" are not two lists.
 */
fun cleanListName(raw: String): String? {
    val cleaned = raw.trim().replace(Regex("""\s+"""), " ")
    if (cleaned.isEmpty()) return null
    return cleaned.take(MAX_LIST_NAME)
}

/** Whether [name] is already a list here, ignoring case as a person would. */
fun List<OwnList>.hasList(name: String): Boolean =
    any { it.name.equals(name, ignoreCase = true) }

/**
 * Adds an empty list, or returns the lists unchanged when the name is unusable
 * or already taken. Newest last, so the shelf keeps the order they were made
 * in rather than shuffling under the viewer.
 */
fun List<OwnList>.withNewList(raw: String): List<OwnList> {
    val name = cleanListName(raw) ?: return this
    if (hasList(name) || size >= MAX_LISTS) return this
    return this + OwnList(name)
}

/** Drops a list and everything in it. */
fun List<OwnList>.withoutList(name: String): List<OwnList> =
    filterNot { it.name.equals(name, ignoreCase = true) }

/**
 * Puts [item] in [listName], making the list if it is not there yet.
 *
 * Appended rather than put at the front: these are curated, and a list that
 * reorders itself every time something is added is a list nobody can point at.
 * Adding the same thing twice keeps the first, with whatever it knew — the
 * second may have arrived from a screen that had no poster.
 */
fun List<OwnList>.withItem(listName: String, item: ListedItem): List<OwnList> {
    val name = cleanListName(listName) ?: return this
    val made = if (hasList(name)) this else withNewList(name)
    return made.map { list ->
        if (!list.name.equals(name, ignoreCase = true)) list
        else if (list.holds(item.kind, item.id)) list
        else list.copy(items = list.items + item)
    }
}

/** Takes something out of one list, leaving the list itself in place. */
fun List<OwnList>.withoutItem(listName: String, kind: SavedKind, id: String): List<OwnList> =
    map { list ->
        if (!list.name.equals(listName, ignoreCase = true)) list
        else list.copy(items = list.items.filterNot { it.kind == kind && it.id == id })
    }

/** The names of every list holding this, for a screen that offers to take it out again. */
fun List<OwnList>.listsHolding(kind: SavedKind, id: String): List<String> =
    filter { it.holds(kind, id) }.map { it.name }

/** Only the lists worth showing on a films or series screen: those that hold that kind. */
fun List<OwnList>.withAnyOf(kind: SavedKind): List<OwnList> =
    filter { list -> list.items.any { it.kind == kind } }
