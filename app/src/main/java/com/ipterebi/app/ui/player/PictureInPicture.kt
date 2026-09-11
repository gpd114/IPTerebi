package com.ipterebi.app.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import com.ipterebi.app.MainActivity
import com.ipterebi.core.pictureInPictureShape

/**
 * Keeps the video playing in a small window when the user leaves the app.
 *
 * Offered only while a player is on screen — leaving from the channel list
 * does what it always did — and withdrawn as soon as the player goes. Returns
 * whether the window is in picture-in-picture right now, which the player uses
 * to hide everything it draws over the video: a title, a guide and a row of
 * buttons in a window the size of a stamp are noise, and the system draws its
 * own controls there anyway.
 *
 * Nothing else changes in picture-in-picture, deliberately. The app is paused
 * there rather than stopped, so the player's stop-in-the-background — which
 * frees the line's one connection — does not fire and the video carries on.
 * When the window is closed the app is stopped, and that same code lets the
 * connection go.
 *
 * Not [offered] while the stream has been handed to another player: there is
 * nothing playing here to shrink.
 */
@Composable
fun rememberPictureInPicture(activity: Activity?, videoAspect: Rational?, offered: Boolean = true): Boolean {
    val host = activity as? MainActivity
    var inPictureInPicture by remember { mutableStateOf(host?.isInPictureInPictureMode == true) }

    DisposableEffect(host, videoAspect, offered) {
        if (!offered) {
            host?.pictureInPicture = null
        } else if (host != null && host.supportsPictureInPicture) {
            host.pictureInPicture = PictureInPictureParams.Builder()
                .setAspectRatio(videoAspect ?: Rational(16, 9))
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        // Android 12+: shrink on the way home, animated,
                        // without being asked at that moment.
                        setAutoEnterEnabled(true)
                        // Resizing a video in place is not seamless; the
                        // system cross-fades instead, which looks better.
                        setSeamlessResizeEnabled(false)
                    }
                }
                .build()
        }
        onDispose { host?.pictureInPicture = null }
    }

    DisposableEffect(host) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            inPictureInPicture = info.isInPictureInPictureMode
        }
        host?.addOnPictureInPictureModeChangedListener(listener)
        onDispose { host?.removeOnPictureInPictureModeChangedListener(listener) }
    }

    return inPictureInPicture
}

/** The shape of the video, clamped to what the system accepts — see [pictureInPictureShape]. */
fun pictureInPictureAspect(width: Int, height: Int, pixelWidthHeightRatio: Float): Rational? =
    pictureInPictureShape(width, height, pixelWidthHeightRatio)
        ?.let { Rational(it.numerator, it.denominator) }
