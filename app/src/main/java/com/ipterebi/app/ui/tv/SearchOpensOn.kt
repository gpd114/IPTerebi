package com.ipterebi.app.ui.tv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What the TV search screen shows before anything is typed.
 *
 * **Optional because not everyone watches sport**, which is the owner's own
 * point and a fair one: the screen opens on the sport that is on, and to
 * somebody who never looks for a match that is a list of things they do not
 * want in the place a prompt used to be.
 *
 * Off still leaves the search itself untouched — this is only the empty
 * state. [Choice.NOTHING] puts back the line of text that was there before,
 * which says what the box is for and points at the team row in Settings.
 *
 * **Off by default**, at the owner's word: not everyone watches sport, and a
 * screen that opens on a list of matches presumes something about whoever is
 * holding the remote. One press turns it on, and the prompt it replaces says
 * where to look. App-wide rather than per line, like
 * [com.ipterebi.app.ui.home.HomeChannels] and
 * [com.ipterebi.app.ui.theme.Appearance]: what somebody wants to see is about
 * them, not about which provider they happen to be signed in to.
 */
object SearchOpensOn {

    enum class Choice(val label: String) {
        NOTHING("Nothing"),
        SPORT("Sport on now"),
    }

    private const val FILE = "search"
    private const val KEY = "opens.on"

    var choice by mutableStateOf(Choice.NOTHING)
        private set

    /** Read before the first frame; see MainActivity. */
    fun load(context: Context) {
        choice = when (prefs(context).getString(KEY, null)) {
            Choice.SPORT.name -> Choice.SPORT
            else -> Choice.NOTHING
        }
    }

    fun set(context: Context, value: Choice) {
        prefs(context).edit().putString(KEY, value.name).apply()
        choice = value
    }

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
