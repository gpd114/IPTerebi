package com.ipterebi.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ipterebi.app.AppContainer
import com.ipterebi.app.data.AccountState
import com.ipterebi.app.ui.channels.ChannelsScreen
import com.ipterebi.app.ui.films.FilmsScreen
import com.ipterebi.app.ui.login.LoginScreen
import com.ipterebi.app.ui.player.Playable
import com.ipterebi.app.ui.player.PlayerScreen
import com.ipterebi.app.ui.series.SeriesDetailScreen
import com.ipterebi.app.ui.series.SeriesScreen
import com.ipterebi.app.ui.settings.SettingsScreen

object Route {
    const val LOGIN = "login"
    const val CHANNELS = "channels"
    const val FILMS = "films"
    const val SERIES = "series"
    const val SERIES_DETAIL = "series/{id}"
    const val SETTINGS = "settings"
    const val PLAY_CHANNEL = "player/channel/{id}"
    const val PLAY_FILM = "player/film/{id}/{ext}"
    const val PLAY_EPISODE = "player/episode/{id}/{ext}"

    fun playChannel(id: Int) = "player/channel/$id"

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

/** The sections the bottom bar, or on a wide screen the rail, switches between. */
private enum class Section(val route: String, val label: String, val icon: ImageVector) {
    LIVE(Route.CHANNELS, "Live TV", Icons.Filled.LiveTv),
    FILMS(Route.FILMS, "Films", Icons.Filled.Movie),
    SERIES(Route.SERIES, "Series", Icons.Filled.VideoLibrary),
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
                    if (current is AccountState.SignedIn) Route.CHANNELS else Route.LOGIN
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
                val wide = LocalConfiguration.current.screenWidthDp >= 600

                Scaffold(
                    // The bar is shown on the section screens only. The screens
                    // inside draw their own top bars and handle their own top
                    // insets, so this Scaffold claims none of them — see the
                    // consumeWindowInsets below for the bottom.
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        if (section != null && !wide) {
                            NavigationBar {
                                Section.entries.forEach { item ->
                                    NavigationBarItem(
                                        selected = item == section,
                                        onClick = { nav.switchSection(item.route) },
                                        icon = { Icon(item.icon, contentDescription = null) },
                                        label = { Text(item.label) },
                                    )
                                }
                            }
                        }
                    },
                ) { padding ->
                    // Consumed as well as applied: the bar already sits above the
                    // system navigation inset, and without this each screen's own
                    // Scaffold adds that inset a second time and leaves a band of
                    // empty space above the bar.
                    Row(Modifier.padding(padding).consumeWindowInsets(padding)) {
                        if (section != null && wide) {
                            NavigationRail {
                                Spacer(Modifier.weight(1f))
                                Section.entries.forEach { item ->
                                    NavigationRailItem(
                                        selected = item == section,
                                        onClick = { nav.switchSection(item.route) },
                                        icon = { Icon(item.icon, contentDescription = null) },
                                        label = { Text(item.label) },
                                        modifier = Modifier.focusRing(),
                                    )
                                }
                                Spacer(Modifier.weight(1f))
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
                                        nav.navigate(Route.CHANNELS) {
                                            popUpTo(Route.LOGIN) { inclusive = true }
                                        }
                                    },
                                )
                            }

                            composable(Route.CHANNELS) {
                                ChannelsScreen(
                                    container = container,
                                    onChannel = { id -> nav.navigate(Route.playChannel(id)) },
                                    onSettings = { nav.navigate(Route.SETTINGS) },
                                )
                            }

                            composable(Route.FILMS) {
                                FilmsScreen(
                                    container = container,
                                    onFilm = { film ->
                                        nav.navigate(Route.playFilm(film.streamId, film.playbackExtension))
                                    },
                                    onSettings = { nav.navigate(Route.SETTINGS) },
                                )
                            }

                            composable(Route.SERIES) {
                                SeriesScreen(
                                    container = container,
                                    onSeries = { series -> nav.navigate(Route.seriesDetail(series.seriesId)) },
                                    onSettings = { nav.navigate(Route.SETTINGS) },
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
 * Moves between top-level sections without stacking them.
 *
 * Anchored on the channel list rather than on the graph's start destination:
 * that is LOGIN for anyone who was signed out at launch, and it is no longer on
 * the back stack once they sign in, so popping to it would pop nothing and each
 * switch would push another screen on top. The channel list is always the root
 * of the signed-in stack. Saving and restoring state is what keeps the film
 * library's loaded category and scroll position when switching away and back.
 */
private fun NavController.switchSection(route: String) {
    navigate(route) {
        popUpTo(Route.CHANNELS) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
