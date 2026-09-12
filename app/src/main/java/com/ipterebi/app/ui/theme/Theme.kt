package com.ipterebi.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.ipterebi.app.R

/**
 * "Night set": the app dressed in its icon. Night blue behind, cobalt for where
 * you are and for what you can act on, glossy white for what is selected, the
 * pink of Terebi-kun's smile for favourites and along the one edge that
 * matters. No yellow in the app itself: the owner asked for it gone. (The
 * icon keeps its yellow eyes and bobbles.)
 *
 * Dark only, and not because a light scheme would be hard: the app is a frame
 * around moving video, and a light chrome around a dark picture is unpleasant
 * to sit in front of. Revisit if there is ever a reason to.
 */
object Night {
    val ground = Color(0xFF0B1122)
    /** The cobalt glow at the top of every section screen. */
    val glow = Color(0xFF1A2B5E)
    val card = Color(0xFF16203F)
    val cardStrong = Color(0xFF1A2548)

    /**
     * What you press — category chips, the search field, the settings button —
     * lighter than the cards and outlined. They were the cards' navy, and sat
     * on a backdrop lit that same blue exactly where they are: measured on the
     * phone, they disappeared into it.
     */
    val control = Color(0xFF26325E)
    val controlEdge = Color(0xFF41508A)
    val chipInk = Color(0xFFE3E9FF)
    val ink = Color(0xFFEEF2FF)
    val inkSoft = Color(0xFF9AA6CA)
    val cobalt = Color(0xFF2F6BFF)
    /** Cobalt lifted for small marks on dark ground, where the full one sinks. */
    val cobaltLight = Color(0xFF8FB4FF)
    val pink = Color(0xFFFF8FA3)
    /** Selected chips: the set's white, not a colour. */
    val glossy = Color(0xFFEAF0FB)

    /**
     * The one-pixel highlight along the top edge of a card — the gloss on the
     * set in the icon, and what stops flat cards reading as holes.
     */
    val gloss = Color(0x12FFFFFF)

    /** Cobalt to pink, for the edge of the one card that matters most on a screen. */
    val edge = Brush.linearGradient(listOf(Color(0xFF3D7BFF), pink))

    /** Behind the section screens: night blue, lit cobalt from the top. */
    val backdrop = Brush.verticalGradient(0f to glow, 0.45f to ground, 1f to ground)
}

private val ColorScheme = darkColorScheme(
    primary = Night.cobalt,
    onPrimary = Color.White,
    primaryContainer = Night.cobalt,
    onPrimaryContainer = Color.White,
    secondary = Night.cobaltLight,
    onSecondary = Night.ground,
    secondaryContainer = Night.cobalt,
    onSecondaryContainer = Color.White,
    tertiary = Night.pink,
    onTertiary = Color(0xFF2A0F1A),
    background = Night.ground,
    onBackground = Night.ink,
    surface = Night.ground,
    onSurface = Night.ink,
    surfaceVariant = Night.control,
    onSurfaceVariant = Night.inkSoft,
    surfaceContainerLowest = Color(0xFF080D1A),
    surfaceContainerLow = Color(0xFF10172D),
    surfaceContainer = Night.card,
    surfaceContainerHigh = Night.cardStrong,
    surfaceContainerHighest = Color(0xFF1C2548),
    outline = Night.controlEdge,
    outlineVariant = Color(0xFF26305A),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF1A0A08),
)

/**
 * M PLUS Rounded 1c, bundled — soft like the mascot, and it carries Japanese,
 * which an app named after テレビ ought to be able to show. Three weights;
 * anything asked for in between takes the nearest. Its licence ships in
 * assets/licenses.
 */
private val Rounded = FontFamily(
    Font(R.font.mplus_rounded_medium, FontWeight.Normal),
    Font(R.font.mplus_rounded_medium, FontWeight.Medium),
    Font(R.font.mplus_rounded_bold, FontWeight.SemiBold),
    Font(R.font.mplus_rounded_bold, FontWeight.Bold),
    Font(R.font.mplus_rounded_extrabold, FontWeight.ExtraBold),
)

private fun TextStyle.rounded(weight: FontWeight? = null) =
    copy(fontFamily = Rounded, fontWeight = weight ?: fontWeight)

private val Base = Typography()

private val Type = Typography(
    displayLarge = Base.displayLarge.rounded(FontWeight.ExtraBold),
    displayMedium = Base.displayMedium.rounded(FontWeight.ExtraBold),
    displaySmall = Base.displaySmall.rounded(FontWeight.ExtraBold),
    headlineLarge = Base.headlineLarge.rounded(FontWeight.ExtraBold),
    headlineMedium = Base.headlineMedium.rounded(FontWeight.ExtraBold),
    headlineSmall = Base.headlineSmall.rounded(FontWeight.ExtraBold),
    titleLarge = Base.titleLarge.rounded(FontWeight.ExtraBold),
    titleMedium = Base.titleMedium.rounded(FontWeight.Bold),
    titleSmall = Base.titleSmall.rounded(FontWeight.Bold),
    bodyLarge = Base.bodyLarge.rounded(FontWeight.Bold),
    bodyMedium = Base.bodyMedium.rounded(FontWeight.Medium),
    bodySmall = Base.bodySmall.rounded(FontWeight.Medium),
    labelLarge = Base.labelLarge.rounded(FontWeight.Bold),
    labelMedium = Base.labelMedium.rounded(FontWeight.Bold),
    labelSmall = Base.labelSmall.rounded(FontWeight.Bold),
)

@Composable
fun IPTerebiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColorScheme, typography = Type, content = content)
}
