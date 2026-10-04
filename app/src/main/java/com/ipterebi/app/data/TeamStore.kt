package com.ipterebi.app.data

import android.content.Context
import com.ipterebi.core.cleanTeamName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The team whose channels are listed.
 *
 * One name, typed once, and after that the row on Home simply says where
 * they are on. Typing it at the moment a feed dies, on a D-pad keyboard,
 * mid-match, is exactly what this exists to avoid.
 *
 * **Not keyed on the line**, unlike favourites, hidden channels and the
 * viewer's own lists. Those hold a provider's stream ids, which mean nothing
 * on another panel; a team's name means the same thing everywhere, so it
 * survives changing provider — which is the one time someone is least likely
 * to want to set it up again.
 *
 * Plain preferences rather than a DataStore, for the same reason
 * [com.ipterebi.app.ui.theme.Appearance] uses them: it is one short string,
 * it is read while the first screen is being built, and a flow of one value
 * is not worth a file format.
 */
class TeamStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _team = MutableStateFlow(prefs.getString(KEY, "").orEmpty())

    /** The team's name, or blank when none is set. */
    val team: StateFlow<String> = _team.asStateFlow()

    /**
     * Sets it, or clears it when what was typed is blank.
     *
     * Blank is the way out: there is no second switch for "show the row",
     * which could only ever get out of step with the name.
     */
    fun set(name: String) {
        val clean = cleanTeamName(name)
        prefs.edit().putString(KEY, clean).apply()
        _team.value = clean
    }

    private companion object {
        const val FILE = "your_team"
        const val KEY = "team"
    }
}
