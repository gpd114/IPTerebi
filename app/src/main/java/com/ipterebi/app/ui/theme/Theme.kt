package com.ipterebi.app.ui.theme

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ipterebi.app.R

/**
 * Everything a theme colours, by role, on the pattern of the owner's other app,
 * Debritsu (its `ui/Theme.kt`): flat throughout — no gradients, rims or sheen —
 * so each role is one colour, and what you press is told apart by fill.
 *
 * Two themes, as Debritsu has: [Light], the default — a pale page with white
 * cards and IPTerebi's dark blue as the accent, where Debritsu's Pastel has its
 * dark purple — and [Dark], near-black with neutral cards and the blue only as
 * an accent. Switched in Settings, held by [Appearance].
 *
 * How it got here, so it is not undone: the app was dark-only, and its page was
 * navy lit cobalt from the top. Every button on it read as blue on blue —
 * in navy, in lighter navy with an outline, in white, and once flat — and the
 * owner asked for Debritsu's look instead. Blue is the accent now, never the
 * page and the buttons at once. The player stays dark in both: it frames video.
 */
data class Palette(
    val dark: Boolean,
    /** The page. */
    val ground: Color,
    /** Cards, panels, poster placeholders. */
    val veil: Color,
    /** Dividers, inactive tracks, text-field rims. */
    val edge: Color,
    /** Faint rims — a poster's edge against the page. */
    val hairline: Color,
    /** Quiet pills — categories you are not in, choices not chosen. */
    val quiet: Color,
    val quietText: Color,
    /** Secondary and icon buttons. */
    val glass: Color,
    /** Icons on [glass]. */
    val glassIcon: Color,
    /** Text-field wells. */
    val field: Color,
    /** The accent as a fill: what is selected, the main button. White goes on it. */
    val cobalt: Color,
    /** The accent as text or a small mark on the page. */
    val accent: Color,
    /** Primary text. */
    val ink: Color,
    /** Muted text. */
    val inkSoft: Color,
    /** Favourites: the pink of Terebi-kun's smile, deep enough to read on the page. */
    val pink: Color,
    /** The translucent score pill over posters. White goes on it. */
    val badge: Color,
)

/** Pale, with IPTerebi's dark blue as the accent. The default, as Debritsu's Pastel is. */
val Light = Palette(
    dark = false,
    ground = Color(0xFFF2F4FA), veil = Color(0xFFFFFFFF), edge = Color(0xFFDDE3F0),
    hairline = Color(0xFFE6EAF4), quiet = Color(0xFFE6EBF7), quietText = Color(0xFF34436A),
    glass = Color(0xFFE3E9F7), glassIcon = Color(0xFF1B3478), field = Color(0xFFFFFFFF),
    cobalt = Color(0xFF1B3478), accent = Color(0xFF1B3478),
    ink = Color(0xFF141A30), inkSoft = Color(0xFF6A7494),
    pink = Color(0xFFC2456B), badge = Color(0xB3142A66),
)

/**
 * Near-black with neutral cards and pills — no blue page under blue buttons —
 * and the blue as a fill where something is chosen, lifted for text, which in
 * the dark blue would be unreadable on a page this dark.
 */
val Dark = Palette(
    dark = true,
    ground = Color(0xFF0B0C11), veil = Color(0xFF17191F), edge = Color(0xFF2A2D36),
    hairline = Color(0x14FFFFFF), quiet = Color(0x1AFFFFFF), quietText = Color(0xFFDADDE6),
    glass = Color(0x1FFFFFFF), glassIcon = Color(0xFFE6E9F2), field = Color(0xFF121419),
    cobalt = Color(0xFF2F5FE0), accent = Color(0xFF9DB5FF),
    ink = Color(0xFFF1F3F8), inkSoft = Color(0xFFA3A8B8),
    pink = Color(0xFFFF8FA3), badge = Color(0xB31F3C8C),
)

/**
 * The colours in use, read from the current [Palette], which is held as state:
 * switching theme recolours everything that has read one, without any screen
 * having to pass a palette about. Named `Night` from when the app had only one.
 */
object Night {
    var palette by mutableStateOf(Light)

    val ground get() = palette.ground
    val veil get() = palette.veil
    val edge get() = palette.edge
    val hairline get() = palette.hairline
    val quiet get() = palette.quiet
    val quietText get() = palette.quietText
    val glass get() = palette.glass
    val glassIcon get() = palette.glassIcon
    val field get() = palette.field
    val cobalt get() = palette.cobalt
    val accent get() = palette.accent
    val ink get() = palette.ink
    val inkSoft get() = palette.inkSoft
    val pink get() = palette.pink
    val badge get() = palette.badge
}

/**
 * Colours over video, which are the same whichever theme: the picture is dark,
 * and a white panel over it would glare.
 */
object OverVideo {
    val panel = Color(0xEB15171F)
    val ink = Color.White
    val inkSoft = Color(0xB3FFFFFF)
    val accent = Color(0xFF9DB5FF)
    /** The main button over video: bright enough on the dark panel in either theme. */
    val button = Color(0xFF2F5FE0)
}

/** Which theme is chosen, kept in plain preferences so it can be read before the first frame. */
object Appearance {
    private const val FILE = "appearance"
    private const val KEY = "theme"

    fun load(context: Context) {
        Night.palette = if (prefs(context).getString(KEY, null) == "dark") Dark else Light
    }

    fun set(context: Context, dark: Boolean) {
        prefs(context).edit().putString(KEY, if (dark) "dark" else "light").apply()
        Night.palette = if (dark) Dark else Light
    }

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}

/**
 * M PLUS Rounded 1c — soft like the mascot — in Latin-only cuts, about 50 KB a
 * weight, the same cuts Debritsu ships. The full font carries every kanji and
 * added ten megabytes; a channel named in Japanese or Arabic falls back to the
 * system font on its own. Its licence ships in assets/licenses.
 */
private val Rounded = FontFamily(
    Font(R.font.mplus_rounded_medium, FontWeight.Normal),
    Font(R.font.mplus_rounded_medium, FontWeight.Medium),
    Font(R.font.mplus_rounded_bold, FontWeight.SemiBold),
    Font(R.font.mplus_rounded_bold, FontWeight.Bold),
    Font(R.font.mplus_rounded_extrabold, FontWeight.ExtraBold),
)

/** Material's colours, from the palette, for the components still drawn by Material. */
private fun scheme(p: Palette) = if (p.dark) darkColorScheme(
    primary = p.cobalt, onPrimary = Color.White,
    primaryContainer = p.cobalt, onPrimaryContainer = Color.White,
    secondary = p.accent, onSecondary = p.ground,
    secondaryContainer = p.cobalt, onSecondaryContainer = Color.White,
    tertiary = p.pink,
    background = p.ground, onBackground = p.ink,
    surface = p.ground, onSurface = p.ink,
    surfaceVariant = p.edge, onSurfaceVariant = p.inkSoft,
    // Sheets, dialogs and menus draw from these; left unset they are
    // Material's baseline greys, which never looked like they belonged here.
    surfaceContainerLowest = p.ground, surfaceContainerLow = p.veil, surfaceContainer = p.veil,
    surfaceContainerHigh = p.veil, surfaceContainerHighest = p.edge,
    outline = p.edge, outlineVariant = p.hairline,
    error = Color(0xFFFF8A80), onError = Color(0xFF1A0A08),
) else lightColorScheme(
    primary = p.cobalt, onPrimary = Color.White,
    primaryContainer = p.quiet, onPrimaryContainer = p.ink,
    secondary = p.accent, onSecondary = Color.White,
    secondaryContainer = p.cobalt, onSecondaryContainer = Color.White,
    tertiary = p.pink,
    background = p.ground, onBackground = p.ink,
    surface = p.ground, onSurface = p.ink,
    surfaceVariant = p.edge, onSurfaceVariant = p.inkSoft,
    surfaceContainerLowest = p.veil, surfaceContainerLow = p.veil, surfaceContainer = p.veil,
    surfaceContainerHigh = p.quiet, surfaceContainerHighest = p.edge,
    outline = p.edge, outlineVariant = p.hairline,
    error = Color(0xFFB3261E), onError = Color.White,
)

/** One rounded face throughout, in three weights; Debritsu's scale. */
private val Type = Typography(
    displaySmall = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Bold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Bold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp),
    labelMedium = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Bold, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.2.sp),
)

@Composable
fun IPTerebiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme(Night.palette), typography = Type, content = content)
}
