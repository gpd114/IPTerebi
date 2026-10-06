package com.ipterebi.app.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ipterebi.core.WatchKind
import com.ipterebi.core.SavedKind
import com.ipterebi.core.Series
import com.ipterebi.core.VodStream
import com.ipterebi.core.EpisodeEntry
import com.ipterebi.core.EpisodeDetails
import com.ipterebi.core.Episode
import com.ipterebi.app.ui.home.HomeScreen
import com.ipterebi.app.data.EpisodeListing
import com.ipterebi.app.AppContainer
import androidx.annotation.DrawableRes
import androidx.compose.ui.res.painterResource
import com.ipterebi.app.R
import com.ipterebi.app.data.AccountState
import com.ipterebi.app.ui.channels.ChannelsScreen
import com.ipterebi.app.ui.films.FilmsScreen
import com.ipterebi.app.ui.guide.GuideScreen
import com.ipterebi.app.ui.login.LoginScreen
import com.ipterebi.app.ui.player.Playable
import com.ipterebi.app.ui.player.PlayerScreen
import com.ipterebi.app.ui.series.SeriesDetailScreen
import com.ipterebi.app.ui.series.SeriesScreen
import com.ipterebi.app.ui.settings.SettingsScreen
import com.ipterebi.app.ui.theme.Corners
import com.ipterebi.app.ui.theme.Night
import com.ipterebi.app.ui.tv.TvDestination
import com.ipterebi.app.ui.tv.TvFilmsScreen
import com.ipterebi.app.ui.tv.TvRecordingsScreen
import com.ipterebi.app.ui.tv.TvSeriesScreen
import com.ipterebi.app.ui.tv.TvLiveScreen
import com.ipterebi.app.ui.tv.TvMultiScreen
import com.ipterebi.app.ui.tv.TvSearchScreen

object Route {
    const val LOGIN = "login"
    const val HOME = "home"
    const val CHANNELS = "channels"
    // The TV app's home: live television, full screen. See TvLiveScreen.
    const val TV_LIVE = "tv/live?channel={channel}"

    /** The TV's live screen, optionally opening on a particular channel. */
    fun tvLive(channelId: Int = 0) = "tv/live?channel=$channelId"
    const val FILMS = "films"
    const val SERIES = "series"
    const val SERIES_DETAIL = "series/{id}"
    const val SETTINGS = "settings"

    /** What has been kept, and what is waiting to be. TV only. */
    const val RECORDINGS = "recordings"

    /** Several channels at once; the TV build only. See TvMultiScreen. */
    const val TV_MULTI = "tv/multi"

    /** Searching, TV only: the phone has its own on the channel list. */
    const val TV_SEARCH = "tv/search"

    /**
     * Playing a recording back: a file on the box, not anything the panel
     * knows about, so the path travels in the route rather than an id the
     * player would have to look up before it could draw anything.
     */
    const val RECORDED = "recorded/{path}/{name}"

    fun recorded(path: String, name: String) =
        "recorded/" + Uri.encode(path) + "/" + Uri.encode(name)
    const val GUIDE = "guide"
    const val PLAY_CHANNEL = "player/channel/{id}"
    const val PLAY_CATCHUP = "player/catchup/{id}/{start}/{minutes}"
    const val PLAY_FILM = "player/film/{id}/{ext}"
    const val PLAY_EPISODE = "player/episode/{id}/{ext}"

    fun playChannel(id: Int) = "player/channel/$id"

    /**
     * A recording of a channel, addressed by when it was on: there is no id
     * for a past programme, only the channel and the hour.
     */
    fun playCatchUp(channelId: Int, startSeconds: Long, minutes: Int) =
        "player/catchup/$channelId/$startSeconds/$minutes"

    /**
     * The extension is encoded because it comes from the panel, and a route is a
     * path: an extension containing a slash — unlikely, but nothing here is
     * promised — would otherwise invent a segment and match no destination.
     */
    fun playFilm(id: Int, extension: String) = "player/film/$id/${Uri.encode(extension)}"

    fun seriesDetail(id: Int) = "series/$id"

    /** Both encoded: an episode id is a string from the panel, not a number. */
    fun playEpisode(id: String, extension: String) =
        "player/episode/${Uri.encode(id)}/${Uri.encode(extension)}"
}

/**
 * The sections the bottom bar, or on a wide screen the rail, switches between.
 *
 * The icons are this app's own (`res/drawable/ic_nav_*`), not Material's.
 * Material's set is the house style of every Android app and looked like
 * nothing in particular here; these three share one 24 grid, one stroke and
 * one set of joins, so they read as a family — a screen with a signal on it, a
 * strip of film, a run of episodes.
 *
 * Live TV goes to the TV build's own home, not the phone's channel list.
 */
private enum class Section(
    /** What the destination is registered as, and so what a back stack entry says. */
    val route: String,
    val label: String,
    @DrawableRes val icon: Int,
    /** What to navigate to, which differs when the route takes an argument. */
    val go: String = route,
) {
    HOME(Route.HOME, "Home", R.drawable.ic_nav_home),
    LIVE(Route.TV_LIVE, "Live TV", R.drawable.ic_nav_live, go = Route.tvLive()),
    FILMS(Route.FILMS, "Films", R.drawable.ic_nav_films),
    SERIES(Route.SERIES, "Series", R.drawable.ic_nav_series),
    RECORDINGS(Route.RECORDINGS, "Recordings", R.drawable.ic_nav_recordings),

    // Last, and deliberately: on a line that allows one stream this is a
    // curiosity rather than where anyone starts the evening. See
    // core/MultiView.kt.
    // Above Multi, because a search is something people reach for and
    // multiview is a curiosity on a one-connection line.
    SEARCH(Route.TV_SEARCH, "Search", R.drawable.ic_nav_search),
    MULTI(Route.TV_MULTI, "Multi", R.drawable.ic_nav_multi),
}

@Composable
fun AppNav(container: AppContainer) {
    val state by container.credentials.state.collectAsStateWithLifecycle(AccountState.Loading)
    val nav = rememberNavController()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val current = state) {
            AccountState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            else -> {
                // Read once and kept. A NavHost only looks at its start
                // destination on first composition, so recomputing this on
                // every state change would look like it works and do nothing —
                // the navigate calls below are what actually move us.
                val start = remember {
                    if (current is AccountState.SignedIn) Route.HOME else Route.LOGIN
                }

                val backStack by nav.currentBackStackEntryAsState()
                val route = backStack?.destination?.route
                val section = Section.entries.firstOrNull { it.route == route }

                // Sections go down the left edge on a wide window and along the
                // bottom on a narrow one. On a television this is not a matter
                // of taste: a bottom bar is reached with a remote by pressing
                // down past the end of the list, and a category of eight
                // hundred channels puts Films and Series eight hundred presses
                // away. A rail is one press of left from anywhere. 600dp is
                // Material's own line between compact and medium, so a tablet,
                // or a phone turned sideways, gets the rail too.
                val wide = railShowing()

                Scaffold(
                    // The bar is shown on the section screens only. The screens
                    // inside draw their own top bars and handle their own top
                    // insets, so this Scaffold claims none of them — see the
                    // consumeWindowInsets below for the bottom.
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    // The flat dark-blue page, under all three sections; their own
                    // Scaffolds are transparent over it. Flat on purpose — see
                    // Night for the cobalt glow that made every button blue on blue.
                    containerColor = Color.Transparent,
                    modifier = Modifier.background(Night.ground),
                    bottomBar = {
                        if (section != null && !wide) {
                            FloatingTabBar(current = section, onSelect = { nav.switchSection(it.go) })
                        }
                    },
                ) { padding ->
                    // Consumed as well as applied: the bar already sits above the
                    // system navigation inset, and without this each screen's own
                    // Scaffold adds that inset a second time and leaves a band of
                    // empty space above the bar.
                    Row(Modifier.padding(padding).consumeWindowInsets(padding)) {
                        // Not over live television, which is the whole screen and has its own
                        // way to the other sections, in its channel list.
                        if (section != null && wide && section != Section.LIVE) {
                            // Centred on a short screen, from the top on a tall
                            // one. A television is 540dp high and the items
                            // nearly fill it, so centring reads as deliberate;
                            // a tablet in portrait is 1280dp and centring
                            // stranded Home, Live TV, Films and Series two
                            // thirds of the way down the left edge, a long way
                            // from the content they belong to and from where a
                            // thumb rests. Measured on a 800x1280dp tablet: the
                            // rail column was [0,137][160,2440] with its items
                            // between y 1028 and 1548.
                            val short =
                                LocalConfiguration.current.screenHeightDp < SHORT_SCREEN_HEIGHT
                            // Clear of the overscan band: the rail is the only thing in the app

                            // drawn tight against a physical edge, and "Recordings" was the

                            // first label to lose a letter on a real television.

                            NavigationRail(

                                containerColor = Color.Transparent,

                                modifier = Modifier.padding(start = overscanInset().first),

                            ) {
                                if (short) Spacer(Modifier.weight(1f))
                                Section.entries.forEach { item ->
                                    // Where you are and where the remote is are two
                                    // different questions asked of the same four items,
                                    // so they cannot be the same colour: chosen is the
                                    // accent, focus is the cobalt fill underneath.
                                    // A focused item drops the indicator and takes the
                                    // page ink, which is what reads on that fill.
                                    var focused by remember { mutableStateOf(false) }
                                    NavigationRailItem(
                                        selected = item == section,
                                        onClick = { nav.switchSection(item.go) },
                                        icon = { Icon(painterResource(item.icon), contentDescription = null) },
                                        label = { Text(item.label) },
                                        colors = NavigationRailItemDefaults.colors(
                                            selectedIconColor = if (focused) Night.ink else Night.onChosen,
                                            selectedTextColor = if (focused) Night.ink else Night.accent,
                                            indicatorColor = if (focused) Color.Transparent else Night.chosen,
                                            unselectedIconColor = if (focused) Night.ink else Night.inkSoft,
                                            unselectedTextColor = if (focused) Night.ink else Night.inkSoft,
                                        ),
                                        modifier = Modifier
                                            .onFocusChanged { focused = it.isFocused }
                                            .focusFill(),
                                    )
                                }
                                Spacer(Modifier.weight(1f))

                                // Settings at the foot, set apart from the
                                // sections: it is not a place you browse, it is
                                // the one you go to and come back from. It used
                                // to be a cog in each screen's top right and the
                                // owner asked for it here instead — on a remote
                                // the rail is one press of left from anywhere,
                                // while the top right is a trip across the
                                // screen and back. The cog is still there on a
                                // narrow screen, which has no rail to put it in.
                                var cogFocused by remember { mutableStateOf(false) }
                                NavigationRailItem(
                                    selected = route == Route.SETTINGS,
                                    onClick = { nav.navigate(Route.SETTINGS) },
                                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                                    label = { Text("Settings") },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = if (cogFocused) Night.ink else Night.onChosen,
                                        selectedTextColor = if (cogFocused) Night.ink else Night.accent,
                                        indicatorColor =
                                            if (cogFocused) Color.Transparent else Night.chosen,
                                        unselectedIconColor =
                                            if (cogFocused) Night.ink else Night.inkSoft,
                                        unselectedTextColor =
                                            if (cogFocused) Night.ink else Night.inkSoft,
                                    ),
                                    modifier = Modifier
                                        .onFocusChanged { cogFocused = it.isFocused }
                                        .focusFill(),
                                )
                            }
                        }
                        // Always the same call in the same place, whichever bar is
                        // showing: a NavHost that moved in the composition would be
                        // a new NavHost, and every screen's state would go with it.
                        NavHost(
                            navController = nav,
                            startDestination = start,
                            modifier = Modifier.weight(1f),
                        ) {
                            composable(Route.LOGIN) {
                                LoginScreen(
                                    container = container,
                                    onSignedIn = {
                                        nav.navigate(Route.HOME) {
                                            popUpTo(Route.LOGIN) { inclusive = true }
                                        }
                                    },
                                )
                            }

                            // The same home screen the phone has, and the box
                            // opens on it. Its Live TV row leads to the TV's
                            // own live screen, not the phone's channel list:
                            // that screen is where television actually plays
                            // here, and the channel it was asked for goes with
                            // it rather than through the phone's player.
                            composable(Route.HOME) {
                                HomeScreen(
                                    container = container,
                                    onSettings = { nav.navigate(Route.SETTINGS) },
                                    onChannels = { nav.switchSection(Route.tvLive()) },
                                    onFilms = { nav.switchSection(Route.FILMS) },
                                    onSeries = { nav.switchSection(Route.SERIES) },
                                    onPlayChannel = { channel ->
                                        nav.switchSection(Route.tvLive(channel.streamId))
                                    },
                                    // Out of one of the viewer's own lists. Same
                                    // bargain as a resume: publish what was
                                    // stored first, because nothing on a cold
                                    // start has loaded the list it came from.
                                    onOpenListed = { entry ->
                                        val id = entry.id.toIntOrNull() ?: 0
                                        when (entry.kind) {
                                            SavedKind.FILM -> {
                                                container.films.publish(
                                                    listOf(
                                                        VodStream(
                                                            streamId = id,
                                                            name = entry.name,
                                                            icon = entry.poster,
                                                            containerExtension = entry.extension,
                                                        )
                                                    )
                                                )
                                                nav.navigate(Route.playFilm(id, entry.extension))
                                            }
                                            SavedKind.SERIES -> {
                                                container.series.publish(
                                                    listOf(
                                                        Series(
                                                            seriesId = id,
                                                            name = entry.name,
                                                            cover = entry.poster,
                                                        )
                                                    )
                                                )
                                                nav.navigate(Route.seriesDetail(id))
                                            }
                                        }
                                    },
                                    onResume = { item ->
                                        when (item.kind) {
                                            WatchKind.FILM -> {
                                                val id = item.id.toIntOrNull() ?: 0
                                                container.films.publish(
                                                    listOf(
                                                        VodStream(
                                                            streamId = id,
                                                            name = item.name,
                                                            icon = item.poster,
                                                            containerExtension = item.extension,
                                                        )
                                                    )
                                                )
                                                nav.navigate(Route.playFilm(id, item.extension))
                                            }
                                            WatchKind.EPISODE -> {
                                                container.episodes.publish(
                                                    listOf(
                                                        EpisodeListing(
                                                            seriesName = item.name,
                                                            entry = EpisodeEntry(
                                                                episode = Episode(
                                                                    id = item.id,
                                                                    title = item.detail,
                                                                    containerExtension = item.extension,
                                                                ),
                                                                details = EpisodeDetails(image = item.poster),
                                                                seasonNumber = 0,
                                                            ),
                                                        )
                                                    )
                                                )
                                                nav.navigate(Route.playEpisode(item.id, item.extension))
                                            }
                                        }
                                    },
                                )
                            }

                            composable(
                                route = Route.TV_LIVE,
                                arguments = listOf(
                                    navArgument("channel") {
                                        type = NavType.IntType
                                        defaultValue = 0
                                    }
                                ),
                            ) { entry ->
                                TvLiveScreen(
                                    container = container,
                                    startOn = entry.arguments?.getInt("channel") ?: 0,
                                    onOpen = { destination ->
                                        when (destination) {
                                            TvDestination.Home -> nav.switchSection(Route.HOME)
                                            TvDestination.Films -> nav.switchSection(Route.FILMS)
                                            TvDestination.Series -> nav.switchSection(Route.SERIES)
                                            TvDestination.Multi -> nav.navigate(Route.TV_MULTI)
                                            TvDestination.Settings -> nav.navigate(Route.SETTINGS)
                                        }
                                    },
                                    onCatchUp = { channel, start, minutes ->
                                        nav.navigate(Route.playCatchUp(channel.streamId, start, minutes))
                                    },
                                )
                            }

                            composable(Route.CHANNELS) {
                                ChannelsScreen(
                                    container = container,
                                    onChannel = { id -> nav.navigate(Route.playChannel(id)) },
                                    onSettings = { nav.navigate(Route.SETTINGS) },
                                    onGuide = { nav.navigate(Route.GUIDE) },
                                )
                            }

                            // The television's own libraries, not the phone's:
                            // same data, same lists, drawn for a sofa. The
                            // cog lives in the rail here, so neither takes an
                            // onSettings.
                            composable(Route.FILMS) {
                                TvFilmsScreen(
                                    container = container,
                                    onFilm = { film ->
                                        nav.navigate(Route.playFilm(film.streamId, film.playbackExtension))
                                    },
                                )
                            }

                            composable(Route.SERIES) {
                                TvSeriesScreen(
                                    container = container,
                                    onSeries = { series -> nav.navigate(Route.seriesDetail(series.seriesId)) },
                                )
                            }

                            composable(
                                route = Route.SERIES_DETAIL,
                                arguments = listOf(navArgument("id") { type = NavType.IntType }),
                            ) { entry ->
                                SeriesDetailScreen(
                                    container = container,
                                    seriesId = entry.arguments?.getInt("id") ?: 0,
                                    onEpisode = { episode ->
                                        nav.navigate(
                                            Route.playEpisode(episode.episode.id, episode.episode.playbackExtension)
                                        )
                                    },
                                    onBack = { nav.popBackStack() },
                                )
                            }

                            composable(Route.GUIDE) {
                                GuideScreen(
                                    container = container,
                                    onChannel = { id -> nav.navigate(Route.playChannel(id)) },
                                    onCatchUp = { id, start, minutes ->
                                        nav.navigate(Route.playCatchUp(id, start, minutes))
                                    },
                                    onBack = { nav.popBackStack() },
                                )
                            }

                            // Searching, TV only. The phone has its own on
                            // the channel list; the TV had none at all.
                            composable(Route.TV_SEARCH) {
                                val signedIn = current as? AccountState.SignedIn
                                if (signedIn != null) {
                                    TvSearchScreen(
                                        container = container,
                                        onPlay = { channel ->
                                            nav.navigate(Route.tvLive(channel.streamId))
                                        },
                                    )
                                }
                            }

                            // Several channels at once. TV only, and see
                            // core/MultiView.kt before touching it: every pane
                            // is a connection, and a line usually allows one.
                            composable(Route.TV_MULTI) {
                                val signedIn = current as? AccountState.SignedIn
                                if (signedIn != null) {
                                    TvMultiScreen(container = container)
                                }
                            }

                            // What has been kept, and what is waiting to be.
                            // TV only: the phone has no way to start one yet,
                            // and the storage it would need is a stick in a box.
                            composable(Route.RECORDINGS) {
                                val signedIn = current as? AccountState.SignedIn
                                if (signedIn != null) {
                                    TvRecordingsScreen(
                                        container = container,
                                        account = signedIn.account,
                                        onPlay = { recording ->
                                            nav.navigate(
                                                Route.recorded(recording.document, recording.title),
                                            )
                                        },
                                    )
                                }
                            }

                            composable(
                                Route.RECORDED,
                                arguments = listOf(
                                    navArgument("path") { type = NavType.StringType },
                                    navArgument("name") { type = NavType.StringType },
                                ),
                            ) { entry ->
                                val path = entry.arguments?.getString("path").orEmpty()
                                val name = entry.arguments?.getString("name").orEmpty()
                                PlayerScreen(
                                    container = container,
                                    playable = Playable.Recorded(path, name),
                                    onBack = { nav.popBackStack() },
                                )
                            }

                            composable(Route.SETTINGS) {
                                SettingsScreen(
                                    container = container,
                                    onBack = { nav.popBackStack() },
                                    onSignedOut = {
                                        nav.navigate(Route.LOGIN) { popUpTo(0) { inclusive = true } }
                                    },
                                )
                            }

                            composable(
                                route = Route.PLAY_CHANNEL,
                                arguments = listOf(navArgument("id") { type = NavType.IntType }),
                            ) { entry ->
                                PlayerScreen(
                                    container = container,
                                    playable = Playable.Channel(entry.arguments?.getInt("id") ?: 0),
                                    onBack = { nav.popBackStack() },
                                )
                            }

                            composable(
                                route = Route.PLAY_CATCHUP,
                                arguments = listOf(
                                    navArgument("id") { type = NavType.IntType },
                                    navArgument("start") { type = NavType.LongType },
                                    navArgument("minutes") { type = NavType.IntType },
                                ),
                            ) { entry ->
                                PlayerScreen(
                                    container = container,
                                    playable = Playable.CatchUp(
                                        channelId = entry.arguments?.getInt("id") ?: 0,
                                        startSeconds = entry.arguments?.getLong("start") ?: 0L,
                                        minutes = entry.arguments?.getInt("minutes") ?: 0,
                                    ),
                                    onBack = { nav.popBackStack() },
                                )
                            }

                            composable(
                                route = Route.PLAY_FILM,
                                arguments = listOf(
                                    navArgument("id") { type = NavType.IntType },
                                    navArgument("ext") { type = NavType.StringType },
                                ),
                            ) { entry ->
                                PlayerScreen(
                                    container = container,
                                    playable = Playable.Film(
                                        id = entry.arguments?.getInt("id") ?: 0,
                                        extension = entry.arguments?.getString("ext").orEmpty()
                                            .ifBlank { com.ipterebi.core.VodStream.DEFAULT_VOD_EXTENSION },
                                    ),
                                    onBack = { nav.popBackStack() },
                                )
                            }

                            composable(
                                route = Route.PLAY_EPISODE,
                                arguments = listOf(
                                    navArgument("id") { type = NavType.StringType },
                                    navArgument("ext") { type = NavType.StringType },
                                ),
                            ) { entry ->
                                PlayerScreen(
                                    container = container,
                                    playable = Playable.Episode(
                                        id = entry.arguments?.getString("id").orEmpty(),
                                        extension = entry.arguments?.getString("ext").orEmpty()
                                            .ifBlank { com.ipterebi.core.VodStream.DEFAULT_VOD_EXTENSION },
                                    ),
                                    onBack = { nav.popBackStack() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The sections along the bottom on a phone: a pill that floats above the
 * screen's edge, the one you are on filled with the accent, its name beside it.
 * Each tab is focusable and ringed, so a remote on a narrow screen can still
 * reach it — a wide one gets the rail instead, above.
 */
@Composable
private fun FloatingTabBar(current: Section, onSelect: (Section) -> Unit) {
    val bar = Corners.panel
    val tab = Corners.control
    Row(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .fillMaxWidth()
            .nightCard(bar)
            .border(1.dp, Night.hairline, bar)
            .height(62.dp)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Section.entries.forEach { item ->
            val selected = item == current
            var focused by remember { mutableStateOf(false) }
            // As the rail above: the tab you are on is the accent, the one the
            // remote is on is the focus fill. On Dark those used to be the same
            // blue, so a remote on this bar could not say where it was.
            val tint = when {
                focused -> Night.ink
                selected -> Night.onChosen
                else -> Night.inkSoft
            }
            Row(
                modifier = Modifier
                    .clip(tab)
                    .background(if (selected) Night.chosen else Color.Transparent)
                    .onFocusChanged { focused = it.isFocused }
                    .focusFill(tab)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(item) })
                    .height(42.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(item.icon), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(7.dp))
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = tint,
                )
            }
        }
    }
}

/**
 * Moves between top-level sections without stacking them.
 *
 * Anchored on Home rather than on the graph's start destination: that is LOGIN
 * for anyone who was signed out at launch, and it is no longer on the back
 * stack once they sign in, so popping to it would pop nothing and each switch
 * would push another screen on top. Home is always the root of the signed-in
 * stack. Saving and restoring state is what keeps the film library's loaded
 * category and scroll position when switching away and back.
 */
private fun NavController.switchSection(route: String) {
    navigate(route) {
        popUpTo(Route.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Whether the sections are down the left edge rather than along the bottom.
 *
 * 600dp is Material's own line between compact and medium, so a tablet or a
 * phone turned sideways gets the rail as a television does. It is asked in two
 * places — the navigation itself, and the top bar, which leaves its settings
 * cog out where the rail already carries one — so it is one function rather
 * than the same number written twice.
 */
@Composable
fun railShowing(): Boolean = LocalConfiguration.current.screenWidthDp >= 600
