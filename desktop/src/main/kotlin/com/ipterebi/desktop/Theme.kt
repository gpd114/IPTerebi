package com.ipterebi.desktop

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The app's Dark palette, by hand.
 *
 * Not shared with `:app`, and that is a deliberate copy rather than an
 * oversight: the real palette lives in an Android Compose module behind
 * `Night`, which carries a `Palette` as mutable state so switching theme
 * recolours the screen. None of that machinery can be depended on from here
 * without dragging in Android.
 *
 * So these are the same hex values as `Dark` in
 * `app/.../ui/theme/Theme.kt` and they have to be kept in step by hand. If
 * the desktop build ever grows a light theme the honest fix is to move
 * `Palette` itself into `:core`, where both can read it -- it is only data
 * and it would have no business being anywhere else.
 */
object Ink {
    val ground = Color(0xFF0B0C11)
    val veil = Color(0xFF191B23)
    val edge = Color(0xFF2A2D36)
    val hairline = Color(0x14FFFFFF)
    val quiet = Color(0xFF20263A)
    val field = Color(0xFF121419)
    val cobalt = Color(0xFF2F5FE0)
    val accent = Color(0xFF9DB5FF)
    val ink = Color(0xFFF1F3F8)
    val inkSoft = Color(0xFFA3A8B8)
    val pink = Color(0xFFFF8FA3)
}

/** Corners are roles, not numbers -- the same four the app uses. */
object Corners {
    val panel = RoundedCornerShape(12.dp)
    val card = RoundedCornerShape(10.dp)
    val control = RoundedCornerShape(8.dp)
}
