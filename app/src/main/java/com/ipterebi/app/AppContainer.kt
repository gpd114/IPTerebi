package com.ipterebi.app

import android.content.Context
import android.util.Log
import com.ipterebi.app.data.ChannelRepository
import com.ipterebi.app.data.CredentialStore
import com.ipterebi.core.XtreamClient
import com.ipterebi.core.defaultXtreamHttpClient

/**
 * Everything with a lifetime longer than a screen, built once in
 * [IPTerebiApp.onCreate]. A service locator rather than a DI framework: there
 * are four objects in here and none of them have interesting dependencies, so
 * anything more would be scaffolding around nothing.
 */
class AppContainer(context: Context) {

    val credentials = CredentialStore(context.applicationContext)

    val xtream = XtreamClient(
        http = defaultXtreamHttpClient(),
        log = { line -> if (BuildConfig.DEBUG) Log.d(TAG_API, line) },
    )

    val channels = ChannelRepository()
}

/** Every panel request and its result, credentials stripped. Debug only. */
const val TAG_API = "IPTerebiApi"

/** Player state changes and playback failures, with the stream URL redacted. Debug only. */
const val TAG_PLAY = "IPTerebiPlay"
