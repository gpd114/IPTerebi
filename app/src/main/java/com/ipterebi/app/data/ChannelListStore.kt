package com.ipterebi.app.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ipterebi.app.BuildConfig
import com.ipterebi.app.TAG_API
import com.ipterebi.core.LenientJson
import com.ipterebi.core.LiveStream
import com.ipterebi.core.XtreamAccount
import com.ipterebi.core.lineKey
import com.ipterebi.core.playableChannels
import com.ipterebi.core.withFavouriteToggled
import com.ipterebi.core.withRecent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer

private val Context.channelListStore by preferencesDataStore(name = "channel_lists")

/**
 * Starred channels and recently watched ones, kept on the device.
 *
 * Stored per line — see `lineKey` — because stream ids are a panel's private
 * numbering and mean nothing on anybody else's. Signing out and into a
 * different provider therefore reveals that provider's lists rather than
 * pointing the old ones at whatever happens to share an id.
 *
 * The whole [LiveStream] is stored rather than just the id, because these lists
 * have to be drawn while a different category is loaded, and a name and an icon
 * cannot be recovered from an id without asking the panel for a list we do not
 * otherwise need. The cost is that a name can go stale; the fix is that
 * watching a channel again refreshes it.
 */
class ChannelListStore(private val context: Context) {

    private fun favouritesKey(account: XtreamAccount) =
        stringPreferencesKey("favourites:${account.lineKey}")

    private fun recentsKey(account: XtreamAccount) =
        stringPreferencesKey("recents:${account.lineKey}")

    fun favourites(account: XtreamAccount): Flow<List<LiveStream>> =
        context.channelListStore.data.map { it[favouritesKey(account)].decodeChannels() }

    fun recents(account: XtreamAccount): Flow<List<LiveStream>> =
        context.channelListStore.data.map { it[recentsKey(account)].decodeChannels() }

    suspend fun toggleFavourite(account: XtreamAccount, channel: LiveStream) {
        context.channelListStore.edit { prefs ->
            val key = favouritesKey(account)
            prefs[key] = prefs[key].decodeChannels().withFavouriteToggled(channel).encode()
        }
    }

    /** Called when a channel actually starts playing, not when one is tapped. */
    suspend fun recordWatched(account: XtreamAccount, channel: LiveStream) {
        context.channelListStore.edit { prefs ->
            val key = recentsKey(account)
            prefs[key] = prefs[key].decodeChannels().withRecent(channel).encode()
        }
    }

    /** Forgets both lists for one line. Used when that line is signed out of. */
    suspend fun clear(account: XtreamAccount) {
        context.channelListStore.edit { prefs ->
            prefs.remove(favouritesKey(account))
            prefs.remove(recentsKey(account))
        }
    }
}

private val channelListSerializer = ListSerializer(LiveStream.serializer())

private fun List<LiveStream>.encode(): String =
    LenientJson.encodeToString(channelListSerializer, this)

/**
 * Anything unreadable is treated as no list at all.
 *
 * These lists are written by this app, so a decode failure means the stored
 * shape has changed under an upgrade. Losing a favourites list on an upgrade is
 * a disappointment; crashing on the channel screen every launch until the app
 * is reinstalled is a different order of problem.
 */
private fun String?.decodeChannels(): List<LiveStream> {
    if (this.isNullOrBlank()) return emptyList()
    return try {
        LenientJson.decodeFromString(channelListSerializer, this).playableChannels()
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) Log.w(TAG_API, "stored channel list was unreadable, dropping it", e)
        emptyList()
    }
}
