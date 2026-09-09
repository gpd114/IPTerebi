package com.ipterebi.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ipterebi.app.AppContainer
import com.ipterebi.app.data.AccountState
import com.ipterebi.app.ui.channels.ChannelsScreen
import com.ipterebi.app.ui.login.LoginScreen
import com.ipterebi.app.ui.player.PlayerScreen

object Route {
    const val LOGIN = "login"
    const val CHANNELS = "channels"
    const val PLAYER = "player/{streamId}"

    fun player(streamId: Int) = "player/$streamId"
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

                NavHost(navController = nav, startDestination = start) {
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
                            onChannel = { streamId -> nav.navigate(Route.player(streamId)) },
                            onSignedOut = {
                                nav.navigate(Route.LOGIN) { popUpTo(0) { inclusive = true } }
                            },
                        )
                    }

                    composable(
                        route = Route.PLAYER,
                        arguments = listOf(navArgument("streamId") { type = NavType.IntType }),
                    ) { entry ->
                        PlayerScreen(
                            container = container,
                            streamId = entry.arguments?.getInt("streamId") ?: 0,
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
