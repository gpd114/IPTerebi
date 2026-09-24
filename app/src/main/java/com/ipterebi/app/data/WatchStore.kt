package com.ipterebi.app.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ipterebi.app.BuildConfig
import com.ipterebi.app.TAG_API
import com.ipterebi.core.LenientJson
import com.ipterebi.core.WatchKind
import com.ipterebi.core.WatchedItem
import com.ipterebi.core.XtreamAccount
import com.ipterebi.core.lineKey
import com.ipterebi.core.resumeAt
import com.ipterebi.core.withWatched
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer

private val Context.watchStore by preferencesDataStore(name = "watching")

/**
 * Where you got to in the films and episodes you have not finished.
 *
 * Its own store rather than a third list in [ChannelListStore], because it
 * holds a different shape and is written far more often — every time a film is
 * left — and because losing one list should not take the other with it.
 *
 * Per line, like the channel lists and for the same reason: a stream id is a
 * provider's private numbering, so a stored position against id 4271 means
 * nothing on anybody else's panel. See `lineKey`.
 *
 * What is kept with the position — the name, the poster, the container
 * extension — is kept because the list it came from is usually gone by the
 * time someone taps the row. The player is navigated to with an id, and the
 * film list it would otherwise be looked up in is only loaded while the Films
 * tab is open. Without these fields a home screen opened on a cold start would
 * show a row of blank posters it could not even play.
 */
class WatchStore(private val context: Context) {

    private fun key(account: XtreamAccount) = stringPreferencesKey("watching:${account.lineKey}")

    fun watching(account: XtreamAccount): Flow<List<WatchedItem>> =
        context.watchStore.data.map { it[key(account)].decodeWatched() }

    /**
     * Records where something got to. The rules about what that is worth
     * keeping — finished, barely started, already in the list — are
     * `withWatched` in core, where they are tested.
     *
     * [name] and [poster] are merged rather than overwritten when they arrive
     * blank: the player knows them only when the list it came from is still
     * loaded, and a resume from the home screen has no list behind it. Losing
     * the name on the second watch would empty the row of anything readable.
     */
    suspend fun record(account: XtreamAccount, item: WatchedItem) {
        context.watchStore.edit { prefs ->
            val k = key(account)
            val existing = prefs[k].decodeWatched()
            val known = existing.firstOrNull { it.kind == item.kind && it.id == item.id }
            val merged = item.copy(
                name = item.name.ifBlank { known?.name.orEmpty() },
                detail = item.detail.ifBlank { known?.detail.orEmpty() },
                poster = item.poster.ifBlank { known?.poster.orEmpty() },
                extension = item.extension.ifBlank { known?.extension.orEmpty() },
            )
            prefs[k] = existing.withWatched(merged).encode()
        }
    }

    /**
     * Where to start playing something, or 0 to start at the beginning.
     *
     * Read once when the player opens rather than held in memory: the player
     * can be reached from the home screen, from a list, or from a notification
     * after the process was killed, and a cache primed by whichever screen
     * happened to be open first would be empty in exactly the last case.
     */
    suspend fun resumeAt(account: XtreamAccount, kind: WatchKind, id: String): Long =
        watching(account).first().resumeAt(kind, id)

    /** Forgets everything for one line, when that line is signed out of. */
    suspend fun clear(account: XtreamAccount) {
        context.watchStore.edit { it.remove(key(account)) }
    }
}

private val watchedSerializer = ListSerializer(WatchedItem.serializer())

private fun List<WatchedItem>.encode(): String =
    LenientJson.encodeToString(watchedSerializer, this)

/**
 * Anything unreadable is no list at all — the same bargain the channel lists
 * make. This is written by this app, so a decode failure means an upgrade
 * changed the shape underneath it; losing where you were in a film is a
 * disappointment, and crashing on the home screen every launch is not.
 */
private fun String?.decodeWatched(): List<WatchedItem> {
    if (this.isNullOrBlank()) return emptyList()
    return try {
        LenientJson.decodeFromString(watchedSerializer, this)
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) Log.w(TAG_API, "stored watch list was unreadable, dropping it", e)
        emptyList()
    }
}
