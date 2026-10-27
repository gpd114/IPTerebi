package com.ipterebi.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.ipterebi.core.XtreamAccount
import com.ipterebi.core.XtreamClient

/**
 * The Windows build.
 *
 * It is a third target rather than a port: `:core` comes across whole — every
 * panel quirk, the URL parsing, the flexible serialisers, the diagnostics —
 * and only the screen and the player are written again, because Compose for
 * Android and Media3 are both Android-only.
 *
 * What is here is live television: sign in, the provider's groups, a group's
 * channels, and a picture. The guide, films, series, catch-up and recording
 * are not, and the honest reason is that each one leans on a piece of `:app`
 * that is Android as well — `GuideStore` is SQLite through
 * `SQLiteOpenHelper`, the lists are DataStore, the recorder is a foreground
 * service with an alarm. The logic above each of those is in `:core` and can
 * be reused; the storage underneath has to be written for the desktop.
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "IPTerebi",
        state = rememberWindowState(width = 1440.dp, height = 880.dp),
    ) {
        App()
    }
}

@Composable
private fun App() {
    // One client for the run, so its connection pool and its threads are
    // shared by every call the way `AppContainer` shares them on Android.
    val client = remember { XtreamClient() }
    var account by remember { mutableStateOf(Prefs.load()) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Ink.cobalt,
            background = Ink.ground,
            surface = Ink.veil,
            onBackground = Ink.ink,
            onSurface = Ink.ink,
        )
    ) {
        Box(Modifier.fillMaxSize().background(Ink.ground)) {
            val signedIn: XtreamAccount? = account
            if (signedIn == null) {
                SignInScreen(client = client, onSignedIn = { account = it })
            } else {
                LiveScreen(
                    account = signedIn,
                    client = client,
                    onSignOut = {
                        Prefs.forget()
                        account = null
                    },
                )
            }
        }
    }
}
