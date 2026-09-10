package com.ipterebi.app.ui.player

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.ipterebi.app.MainActivity
import com.ipterebi.app.playback.PlaybackService

/**
 * Puts [player] in a media session, which is what the notification, the lock
 * screen, headphone buttons and the picture-in-picture window's own controls
 * all drive — and hands that session to [PlaybackService], which is what lets
 * it keep playing with the screen off.
 *
 * Whether it *does* keep playing is the player screen's decision, made when the
 * app is stopped; this only makes it possible.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
fun MediaSessionFor(player: Player, live: Boolean) {
    val context = LocalContext.current
    DisposableEffect(player) {
        val app = context.applicationContext
        val session = MediaSession.Builder(app, if (live) LivePlayer(player) else player)
            // Unique within the process. A swipe builds the next player before
            // the last one's session is gone, and two sessions with one id throw.
            .setId("player-${nextSessionId++}")
            .setSessionActivity(
                PendingIntent.getActivity(
                    app,
                    0,
                    Intent(app, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
            .setCallback(SessionCommands(live))
            .build()

        var attachment: PlaybackService.Attachment? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                attachment = (binder as? PlaybackService.Attachment)?.also { it.show(session) }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                attachment = null
            }
        }
        app.bindService(
            Intent(app, PlaybackService::class.java).setAction(PlaybackService.ACTION_ATTACH),
            connection,
            Context.BIND_AUTO_CREATE,
        )

        onDispose {
            attachment?.hide(session)
            app.unbindService(connection)
            session.release()
        }
    }
}

private var nextSessionId = 0

/**
 * What the lock screen and the notification may ask for.
 *
 * Next and previous are taken away because there is nothing behind them — one
 * thing plays at a time — and a notification with two inert buttons reads as
 * broken. A channel loses seeking too: live has nowhere to seek to.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
private class SessionCommands(private val live: Boolean) : MediaSession.Callback {
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        val offered = super.onConnect(session, controller)
        val commands = offered.availablePlayerCommands.buildUpon()
            .removeAll(
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            )
            .apply {
                if (live) {
                    removeAll(
                        Player.COMMAND_SEEK_BACK,
                        Player.COMMAND_SEEK_FORWARD,
                        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                    )
                }
            }
            .build()
        return MediaSession.ConnectionResult.accept(offered.availableSessionCommands, commands)
    }
}

/**
 * A channel as the session sees it: resuming from stopped rejoins the broadcast
 * at its live edge, as coming back to the app does. `stop()` keeps the old
 * position, and for HLS that has usually slid out of the live window by the
 * time anyone presses play on the lock screen.
 */
private class LivePlayer(player: Player) : ForwardingPlayer(player) {
    override fun prepare() {
        if (playbackState == Player.STATE_IDLE) seekToDefaultPosition()
        super.prepare()
    }
}
