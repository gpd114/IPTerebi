package com.ipterebi.app

import android.content.Context
import android.util.Log
import com.ipterebi.app.data.ChannelListStore
import com.ipterebi.app.data.MediaRepository
import com.ipterebi.app.data.CredentialStore
import com.ipterebi.app.data.EpisodeListing
import com.ipterebi.app.data.Guide
import com.ipterebi.core.LiveStream
import com.ipterebi.core.Series
import com.ipterebi.core.VodStream
import com.ipterebi.core.XtreamClient
import com.ipterebi.core.defaultXtreamHttpClient
import kotlinx.coroutines.flow.MutableStateFlow

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

    val channels = MediaRepository<Int, LiveStream> { it.streamId }

    /** What [channels] is, in words — "News", "Favourites" — for the TV guide to be headed with. */
    val channelsTitle = MutableStateFlow("Channels")

    val films = MediaRepository<Int, VodStream> { it.streamId }

    val series = MediaRepository<Int, Series> { it.seriesId }

    /** Keyed on the episode's own id, which is a string. See Episode.id. */
    val episodes = MediaRepository<String, EpisodeListing> { it.id }

    /** Starred and recently watched channels, per line. */
    val channelLists = ChannelListStore(context.applicationContext)

    /** What is on: the full guide kept on the device, and short answers put right. */
    val guide = Guide(context.applicationContext, xtream, log = { line -> if (BuildConfig.DEBUG) Log.d(TAG_API, line) })
}

/** Every panel request and its result, credentials stripped. Debug only. */
const val TAG_API = "IPTerebiApi"

/** Player state changes and playback failures, with the stream URL redacted. Debug only. */
const val TAG_PLAY = "IPTerebiPlay"
