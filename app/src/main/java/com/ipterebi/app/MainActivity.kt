package com.ipterebi.app

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ipterebi.app.ui.AppNav
import com.ipterebi.app.ui.theme.IPTerebiTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as IPTerebiApp).container
        setContent {
            IPTerebiTheme {
                AppNav(container)
            }
        }
    }

    /**
     * What a picture-in-picture window should look like, set by the player while
     * it is on screen and cleared when it leaves. Null means leaving the app
     * should do what it always did.
     *
     * Android 12 and later are told up front and shrink the window themselves
     * when the user goes home, which also lets them animate it. Earlier versions
     * have to be asked at the moment of leaving — see [onUserLeaveHint].
     */
    var pictureInPicture: PictureInPictureParams? = null
        set(value) {
            field = value
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && supportsPictureInPicture) {
                setPictureInPictureParams(
                    value ?: PictureInPictureParams.Builder().setAutoEnterEnabled(false).build()
                )
            }
        }

    /** Low-memory devices, among others, have no picture-in-picture at all. */
    val supportsPictureInPicture: Boolean
        get() = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /**
     * Called when the user leaves on purpose — the home button, the recents
     * button — and not for a phone call or a notification that takes over. That
     * is the moment to shrink the player on Android 8 to 11, which cannot be told
     * in advance.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && supportsPictureInPicture) {
            pictureInPicture?.let { params ->
                // Refused rather than thrown on, where the system will not have it.
                runCatching { enterPictureInPictureMode(params) }
            }
        }
    }
}
