package com.ipterebi.app

import android.content.Context
import android.util.Log
import com.ipterebi.app.data.ChannelListStore
import com.ipterebi.app.data.MediaRepository
import com.ipterebi.app.data.LineChannels
import com.ipterebi.app.data.ReminderStore
import com.ipterebi.app.data.TeamStore
import com.ipterebi.app.data.ListStore
import com.ipterebi.app.data.RecordingStore
import com.ipterebi.app.data.WatchStore
import com.ipterebi.app.data.CredentialStore
import com.ipterebi.app.data.EpisodeListing
import com.ipterebi.app.data.ExtraGuideStore
import com.ipterebi.app.data.Guide
import com.ipterebi.core.LiveStream
import com.ipterebi.core.Series
import com.ipterebi.core.VodStream
import com.ipterebi.core.XtreamClient
import com.ipterebi.core.defaultXtreamHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Everything with a lifetime longer than a screen, built once in
 * [IPTerebiApp.onCreate]. A service locator rather than a DI framework: there
 * are four objects in here and none of them have interesting dependencies, so
 * anything more would be scaffolding around nothing.
 */
class AppContainer(context: Context) {

    /**
     * For the few things that need one after construction: an alarm being
     * re-armed, a notification being posted, and on the TV branch a document
     * tree.
     *
     * Called `appContext` on both branches. It arrived as `context` here and
     * `appContext` there, and the two then conflicted on every merge -- in
     * `app/`, which is the kind CI refuses to resolve by itself and rightly
     * so. One name costs nothing.
     */
    val appContext: Context = context.applicationContext

    val credentials = CredentialStore(context.applicationContext)

    /**
     * One HTTP client for the whole app: the panel API, and the recorder that
     * writes a channel to a file. Sharing it shares the connection pool and
     * the timeouts, and means a recording is opened exactly the way every
     * other request to this panel is.
     */
    val http = defaultXtreamHttpClient()

    val xtream = XtreamClient(
        http = http,
        log = { line -> if (BuildConfig.DEBUG) Log.d(TAG_API, line) },
    )

    val channels = MediaRepository<Int, LiveStream> { it.streamId }

    /** What [channels] is, in words — "News", "Favourites" — for the TV guide to be headed with. */
    val channelsTitle = MutableStateFlow("Channels")

    val films = MediaRepository<Int, VodStream> { it.streamId }

    val series = MediaRepository<Int, Series> { it.seriesId }

    /** Keyed on the episode's own id, which is a string. See Episode.id. */
    val episodes = MediaRepository<String, EpisodeListing> { it.id }

    /**
     * For writes that must finish even though the screen that started them is
     * going away — where you got to in a film, written as the player is torn
     * down. A scope tied to a composition is cancelled at exactly that moment,
     * which is the one time this must not be.
     */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Where you got to in films and episodes you have not finished, per line. */
    val watched = WatchStore(context.applicationContext)

    /** The viewer's own lists of films and series, per line. See ListStore. */
    val lists = ListStore(context.applicationContext)

    /** What is to be recorded, what is recording, and where it is written. */
    val recordings = RecordingStore(context.applicationContext)

    /** Starred and recently watched channels, per line. */
    /** Every channel on the line, for searching what is on and for finding other feeds. */
    val lineChannels = LineChannels(xtream)

    /** The team whose channels Home lists. One name, typed once. */
    /** Programmes someone asked to be told about. */
    val reminders = ReminderStore(context.applicationContext)

    val team = TeamStore(context.applicationContext)

    val channelLists = ChannelListStore(context.applicationContext)

    /**
     * Extra XMLTV sources, typed in Settings, filling in what the provider's
     * own guide does not reach. Not keyed on the line: see ExtraGuideStore.
     */
    val extraGuides = ExtraGuideStore(context.applicationContext)

    /** What is on: the full guide kept on the device, and short answers put right. */
    val guide = Guide(
        context.applicationContext,
        xtream,
        extraSources = { extraGuides.sources.value },
        log = { line -> if (BuildConfig.DEBUG) Log.d(TAG_API, line) },
    )
}

/** Every panel request and its result, credentials stripped. Debug only. */
const val TAG_API = "IPTerebiApi"

/** Player state changes and playback failures, with the stream URL redacted. Debug only. */
const val TAG_PLAY = "IPTerebiPlay"
