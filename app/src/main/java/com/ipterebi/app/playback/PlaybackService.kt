package com.ipterebi.app.playback

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Keeps a stream playing while the screen is off.
 *
 * The player is not in here. It belongs to the player screen, which built it
 * and makes every decision about it; this service only lends it what a screen
 * cannot have — permission to keep running with nothing on screen, and a
 * notification with controls in it. Media3 does both for a session it holds:
 * while the session plays it puts the notification up and the service in the
 * foreground, and when it stops it takes them down again.
 *
 * The screen binds with [ACTION_ATTACH] and hands its session over. Other
 * binds — the lock screen, headphones, a watch — are Media3's, and get
 * whatever is playing.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class PlaybackService : MediaSessionService() {

    inner class Attachment : Binder() {
        fun show(session: MediaSession) = addSession(session)

        fun hide(session: MediaSession) {
            removeSession(session)
            // Media3 starts this service itself to go into the foreground, and
            // a started service outlives the screen that bound it unless told.
            if (sessions.isEmpty()) stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == ACTION_ATTACH) Attachment() else super.onBind(intent)

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        sessions.firstOrNull()

    companion object {
        const val ACTION_ATTACH = "com.ipterebi.app.playback.ATTACH"
    }
}
