package com.ipterebi.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ipterebi.core.StreamFormat
import com.ipterebi.core.XtreamAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.accountStore by preferencesDataStore(name = "account")

/**
 * Whether we have a line to use. [Loading] matters: DataStore reads from disk,
 * so on a cold start there is a moment where the answer is not yet known, and
 * treating that moment as "signed out" flashes the login screen at somebody who
 * signed in months ago.
 */
sealed interface AccountState {
    data object Loading : AccountState
    data object SignedOut : AccountState
    data class SignedIn(val account: XtreamAccount) : AccountState
}

/**
 * The stored line.
 *
 * The password is kept in plain text in the app's private DataStore. That is
 * what it has to be — it goes into the path of every stream URL, so the app
 * needs it back in the clear on every play — and it is what every other IPTV
 * client does. It is readable on a rooted device or off an unencrypted backup,
 * which is why `android:allowBackup` is false in the manifest.
 */
class CredentialStore(private val context: Context) {

    private object Keys {
        val base = stringPreferencesKey("base")
        val username = stringPreferencesKey("username")
        val password = stringPreferencesKey("password")
        val format = stringPreferencesKey("format")
        val userAgent = stringPreferencesKey("user_agent")
    }

    val state: Flow<AccountState> = context.accountStore.data.map { prefs ->
        val base = prefs[Keys.base]
        val username = prefs[Keys.username]
        val password = prefs[Keys.password]

        if (base.isNullOrBlank() || username.isNullOrBlank() || password.isNullOrBlank()) {
            AccountState.SignedOut
        } else {
            AccountState.SignedIn(
                XtreamAccount(
                    base = base,
                    username = username,
                    password = password,
                    format = prefs[Keys.format]
                        ?.let { saved -> StreamFormat.entries.firstOrNull { it.name == saved } }
                        ?: StreamFormat.TS,
                    userAgent = prefs[Keys.userAgent]?.takeIf { it.isNotBlank() }
                        ?: XtreamAccount.DEFAULT_USER_AGENT,
                )
            )
        }
    }

    suspend fun save(account: XtreamAccount) {
        context.accountStore.edit { prefs ->
            prefs[Keys.base] = account.base
            prefs[Keys.username] = account.username
            prefs[Keys.password] = account.password
            prefs[Keys.format] = account.format.name
            prefs[Keys.userAgent] = account.userAgent
        }
    }

    suspend fun clear() {
        context.accountStore.edit { it.clear() }
    }
}
