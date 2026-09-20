package com.ipterebi.app.ui.home

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Which channels the home screen's Live TV row shows.
 *
 * Favourites by default, because that is a list someone chose; recent is there
 * for anyone who would rather see where they have just been. Held as Compose
 * state, like the theme in [com.ipterebi.app.ui.theme.Appearance] and for the
 * same reason: changing it in Settings has to recolour — here, refill — the
 * screen behind without anything being rebuilt or re-read.
 *
 * In plain preferences rather than DataStore because it is read while the
 * first frame is being drawn, and a suspending read would draw an empty row
 * first and the right one a moment later.
 */
object HomeChannels {

    enum class Source(val label: String) { FAVOURITES("Favourites"), RECENT("Recent") }

    private const val FILE = "home"
    private const val KEY = "channels.row"

    var source by mutableStateOf(Source.FAVOURITES)
        private set

    fun load(context: Context) {
        source = when (prefs(context).getString(KEY, null)) {
            Source.RECENT.name -> Source.RECENT
            else -> Source.FAVOURITES
        }
    }

    fun set(context: Context, value: Source) {
        prefs(context).edit().putString(KEY, value.name).apply()
        source = value
    }

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
