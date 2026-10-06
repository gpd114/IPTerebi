package com.ipterebi.app.data

import android.content.Context
import com.ipterebi.core.Pane
import com.ipterebi.core.XtreamAccount
import com.ipterebi.core.asStored
import com.ipterebi.core.lineKey
import com.ipterebi.core.storedPanes

/**
 * The multiview grid as it was left.
 *
 * **Per line, like everything keyed on a panel's ids.** Channel 4271 on one
 * provider is not channel 4271 on another, so a grid carried across lines
 * would open on four things nobody chose. [lineKey] is the key, and signing
 * out of a line takes its grid with it.
 *
 * Plain preferences rather than a DataStore, for the reason [TeamStore] uses
 * them: it is one short string per line, read while the screen is being
 * built, and a flow of one value is not worth a file format.
 */
class MultiStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The grid for [account], or empty when none was ever set. */
    fun panes(account: XtreamAccount): List<Pane> =
        storedPanes(prefs.getString(account.lineKey, "").orEmpty())

    /** Remembers the grid, so it is set up once rather than every visit. */
    fun set(account: XtreamAccount, panes: List<Pane>) {
        prefs.edit().putString(account.lineKey, panes.asStored()).apply()
    }

    /** Forgets [account]'s grid, when the line is signed out of. */
    fun clear(account: XtreamAccount) {
        prefs.edit().remove(account.lineKey).apply()
    }

    private companion object {
        const val FILE = "multiview"
    }
}
