package com.ipterebi.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whatever list is currently on screen, held so the player can look one entry
 * up by id.
 *
 * The player is navigated to with an id and nothing else, deliberately. A full
 * list is tens of thousands of entries on a large line, and navigation
 * arguments end up in a Bundle that is written to the saved instance state —
 * putting the list there risks a TransactionTooLargeException on a process
 * death that is impossible to reproduce on the machine it was written on. An
 * integer is always safe.
 *
 * Generic because channels, films and episodes are looked up identically and
 * differ only in the type that comes back and the type of their id — an
 * episode id is a string. [idOf] names the id field, since the model
 * classes share no interface — they are shaped by what the panel sends, not by
 * this app.
 */
class MediaRepository<K : Any, T : Any>(private val idOf: (T) -> K) {

    private val _items = MutableStateFlow<List<T>>(emptyList())
    val items: StateFlow<List<T>> = _items.asStateFlow()

    fun publish(items: List<T>) {
        _items.value = items
    }

    fun find(id: K): T? = _items.value.firstOrNull { idOf(it) == id }
}
