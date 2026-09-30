package com.ipterebi.app.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ipterebi.app.BuildConfig
import com.ipterebi.app.TAG_API
import com.ipterebi.core.LenientJson
import com.ipterebi.core.ListedItem
import com.ipterebi.core.OwnList
import com.ipterebi.core.SavedKind
import com.ipterebi.core.XtreamAccount
import com.ipterebi.core.lineKey
import com.ipterebi.core.withItem
import com.ipterebi.core.withNewList
import com.ipterebi.core.withoutItem
import com.ipterebi.core.withoutList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer

private val Context.ownListStore by preferencesDataStore(name = "ownlists")

/**
 * The lists the viewer made, per line.
 *
 * Per line like everything else keyed on a panel's ids, and for the same
 * reason: film 4271 on one provider is not film 4271 on another, so a list
 * carried across would play something else entirely. See `lineKey`.
 *
 * Its own store rather than another key in [ChannelListStore] because it
 * outlives a line's favourites in importance — these were typed, not
 * tapped — and one unreadable value should not take the other down with it.
 *
 * Every rule about what goes in and what comes out is `OwnLists` in core,
 * where it is tested. This file only reads and writes.
 */
class ListStore(private val context: Context) {

    private fun key(account: XtreamAccount) = stringPreferencesKey("lists:${account.lineKey}")

    fun lists(account: XtreamAccount): Flow<List<OwnList>> =
        context.ownListStore.data.map { it[key(account)].decodeLists() }

    suspend fun create(account: XtreamAccount, name: String) =
        update(account) { it.withNewList(name) }

    suspend fun delete(account: XtreamAccount, name: String) =
        update(account) { it.withoutList(name) }

    suspend fun add(account: XtreamAccount, listName: String, item: ListedItem) =
        update(account) { it.withItem(listName, item) }

    suspend fun remove(account: XtreamAccount, listName: String, kind: SavedKind, id: String) =
        update(account) { it.withoutItem(listName, kind, id) }

    /** Forgets every list for one line, when that line is signed out of. */
    suspend fun clear(account: XtreamAccount) {
        context.ownListStore.edit { it.remove(key(account)) }
    }

    private suspend fun update(account: XtreamAccount, change: (List<OwnList>) -> List<OwnList>) {
        context.ownListStore.edit { prefs ->
            val k = key(account)
            prefs[k] = change(prefs[k].decodeLists()).encode()
        }
    }
}

private val listsSerializer = ListSerializer(OwnList.serializer())

private fun List<OwnList>.encode(): String = LenientJson.encodeToString(listsSerializer, this)

/**
 * Unreadable is empty, as everywhere else here — but this is the one store
 * where that costs the viewer something they typed, so it says so in the log
 * rather than passing over it. Only an upgrade that changed the shape can
 * cause it, since nothing but this app writes the value.
 */
private fun String?.decodeLists(): List<OwnList> {
    if (this.isNullOrBlank()) return emptyList()
    return try {
        LenientJson.decodeFromString(listsSerializer, this)
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) Log.w(TAG_API, "stored lists were unreadable, dropping them", e)
        emptyList()
    }
}
