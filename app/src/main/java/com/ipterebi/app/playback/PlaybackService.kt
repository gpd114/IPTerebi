package com.ipterebi.app.playback

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Binder
import android.os.IBinder
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import android.util.Log
import com.ipterebi.app.BuildConfig
import com.ipterebi.app.R
import com.ipterebi.app.TAG_PLAY

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
            if (sessions.isEmpty()) {
                // Media3 starts this service itself to go into the foreground,
                // and a started service outlives the screen that bound it
                // unless told. But *when* it is told matters.
                //
                // Media3 promotes the service by calling startForegroundService
                // and then posting the notification that calls startForeground
                // (MediaNotificationManager.startForeground). Android gives it
                // five seconds to make that second call, and kills the app if it
                // does not come. A stopSelf that lands between the two destroys
                // the service before the notification is posted, so the call
                // never comes and the app dies — which is the
                // RemoteServiceException "Context.startForegroundService() did
                // not then call Service.startForeground()" seen once on the box,
                // on 20 September at 20:47.
                //
                // Posting rather than stopping here lets whatever Media3 has
                // already queued run first. It costs one turn of the main
                // thread's loop; the sessions are checked again on the way
                // through in case one was attached in the meantime, which is
                // what switching channel looks like from here.
                Handler(Looper.getMainLooper()).post {
                    if (sessions.isEmpty()) {
                        if (BuildConfig.DEBUG) Log.d(TAG_PLAY, "playback service stopping, no sessions left")
                        stopSelf()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // The app's own set in the status bar, rather than Media3's generic
        // note — which reads as a music player, not a television.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_notification)
            }
        )
    }

    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == ACTION_ATTACH) Attachment() else super.onBind(intent)

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        sessions.firstOrNull()

    companion object {
        const val ACTION_ATTACH = "com.ipterebi.app.playback.ATTACH"
    }
}
