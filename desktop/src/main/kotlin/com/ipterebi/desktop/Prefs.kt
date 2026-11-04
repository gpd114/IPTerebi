package com.ipterebi.desktop

import com.ipterebi.core.StreamFormat
import com.ipterebi.core.XtreamAccount
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * What is remembered between runs, in one small JSON file.
 *
 * `%APPDATA%\IPTerebi\account.json`, which is where a Windows application is
 * supposed to put this and, unlike the install directory, is writable without
 * asking anybody for anything.
 *
 * **The password is in there in clear, and that is worth saying out loud.**
 * The Android app is no better -- its DataStore is an unencrypted file in the
 * app's private storage -- but on Android the sandbox is doing the work, and on
 * Windows there is no sandbox: any process running as this user can read it.
 * Nothing can be done about *needing* the password, because every Xtream
 * request puts it in the URL path, so there is no token to hold instead. What
 * could be done later is Windows' own DPAPI through `CryptProtectData`, which
 * ties the ciphertext to the user account. Until then the file is written with
 * no sharing beyond the owner and the honest answer is in this comment.
 */
object Prefs {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val file: File by lazy {
        val base = System.getenv("APPDATA")
            ?: System.getProperty("user.home")
        File(base, "IPTerebi").apply { mkdirs() }.resolve("account.json")
    }

    @Serializable
    private data class Stored(
        val base: String,
        val username: String,
        val password: String,
        val format: String = StreamFormat.TS.name,
        val userAgent: String = XtreamAccount.DEFAULT_USER_AGENT,
    )

    /** The last line signed in to, or null on a first run. */
    fun load(): XtreamAccount? = runCatching {
        if (!file.exists()) return null
        val stored = json.decodeFromString(Stored.serializer(), file.readText())
        XtreamAccount(
            base = stored.base,
            username = stored.username,
            password = stored.password,
            // A format or agent written by a newer build, or edited by hand
            // into something meaningless, must not stop the app starting.
            format = StreamFormat.entries.firstOrNull { it.name == stored.format }
                ?: StreamFormat.TS,
            userAgent = stored.userAgent,
        )
    }.getOrNull()

    fun save(account: XtreamAccount) {
        runCatching {
            file.writeText(
                json.encodeToString(
                    Stored.serializer(),
                    Stored(
                        base = account.base,
                        username = account.username,
                        password = account.password,
                        format = account.format.name,
                        userAgent = account.userAgent,
                    ),
                )
            )
        }
    }

    fun forget() {
        runCatching { file.delete() }
    }
}
