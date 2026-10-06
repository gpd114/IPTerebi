package com.ipterebi.app.data

import android.content.Context
import com.ipterebi.core.cleanGuideSources
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Extra XMLTV sources, beside the provider's own.
 *
 * **Several, not one, and that is the owner's requirement rather than a
 * flourish.** They watch the Premier League, which means a UK sports guide —
 * and the three o'clock Saturday games, which are not broadcast in the UK at
 * all, so the feeds carrying them are foreign and so are their listings. One
 * country's guide cannot answer both.
 *
 * **Not keyed on the line**, unlike favourites, hidden channels and the
 * viewer's own lists. Those hold a provider's stream ids, which mean nothing
 * on another panel; a public guide describes channels, so it stays useful
 * when the line changes — the one time somebody is least likely to want to
 * set this up again. The same reasoning as [TeamStore], and plain preferences
 * for the same reason: a handful of short strings, read while the first
 * screen is being built.
 *
 * Nothing is suggested, pre-filled or fetched on the app's own initiative.
 * These are whatever the viewer typed.
 */
class ExtraGuideStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _sources = MutableStateFlow(cleanGuideSources(prefs.getString(KEY, "").orEmpty()))

    /** The sources, in the order they were typed. Empty when none is set. */
    val sources: StateFlow<List<String>> = _sources.asStateFlow()

    /**
     * Sets them from a typed block of text, one per line.
     *
     * What is kept is what [cleanGuideSources] made of it, which is also what
     * the field is then shown — so a line that was not a URL visibly goes,
     * rather than being kept to fail hours later in a background refresh
     * where nobody would see it.
     */
    fun set(typed: String) {
        val clean = cleanGuideSources(typed)
        prefs.edit().putString(KEY, clean.joinToString("\n")).apply()
        _sources.value = clean
    }

    /** The sources as the field shows them: one per line. */
    fun asTyped(): String = _sources.value.joinToString("\n")

    private companion object {
        const val FILE = "extra_guides"
        const val KEY = "sources"
    }
}
