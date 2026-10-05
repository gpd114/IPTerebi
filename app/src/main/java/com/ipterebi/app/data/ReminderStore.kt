package com.ipterebi.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ipterebi.core.LenientJson
import com.ipterebi.core.Reminder
import com.ipterebi.core.XtreamAccount
import com.ipterebi.core.lineKey
import com.ipterebi.core.stillToCome
import com.ipterebi.core.withReminderToggled
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString

private val Context.reminderStore by preferencesDataStore("reminders")

/**
 * The programmes someone has asked to be told about.
 *
 * **Per line**, like everything keyed on a panel's ids: a reminder names a
 * channel to put on, and stream id 4271 on one provider is not the same
 * channel on another. Signing in elsewhere should not offer to switch you to
 * something unrelated.
 *
 * Reminders are read before being written rather than kept in memory,
 * because the thing that fires them is a broadcast receiver which may run in
 * a process that has no screen and no view model.
 */
class ReminderStore(private val context: Context) {

    private fun key(account: XtreamAccount) = stringPreferencesKey("reminders:${account.lineKey}")

    /** What is set for this line, with anything finished dropped. */
    fun reminders(account: XtreamAccount): Flow<List<Reminder>> =
        context.reminderStore.data.map { prefs ->
            decode(prefs[key(account)]).stillToCome(System.currentTimeMillis() / 1000)
        }

    /** Sets one, or takes it away if it is already set. */
    suspend fun toggle(account: XtreamAccount, reminder: Reminder) {
        context.reminderStore.edit { prefs ->
            val now = System.currentTimeMillis() / 1000
            val next = decode(prefs[key(account)])
                .stillToCome(now)
                .withReminderToggled(reminder)
            prefs[key(account)] = LenientJson.encodeToString(next)
        }
    }

    /** Whatever is set, read once — for the receiver that arms the alarm. */
    suspend fun now(account: XtreamAccount): List<Reminder> = reminders(account).first()

    /**
     * Drops one that has been dealt with.
     *
     * Called after it has fired, so the alarm can be re-armed for whatever is
     * next without finding the same one waiting again.
     */
    suspend fun forget(account: XtreamAccount, id: String) {
        context.reminderStore.edit { prefs ->
            val next = decode(prefs[key(account)]).filterNot { it.id == id }
            prefs[key(account)] = LenientJson.encodeToString(next)
        }
    }

    private fun decode(raw: String?): List<Reminder> =
        if (raw.isNullOrBlank()) emptyList()
        else runCatching {
            LenientJson.decodeFromString(ListSerializer(Reminder.serializer()), raw)
        }.getOrDefault(emptyList())
}
