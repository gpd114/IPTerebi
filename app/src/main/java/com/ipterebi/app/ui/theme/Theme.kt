package com.ipterebi.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ipterebi.app.R

/**
 * IPTerebi's colours, by role, on the pattern of the owner's other app,
 * Debritsu: flat throughout — no gradients, rims or sheen — so each role is one
 * colour, and what you press is told apart by fill, not by decoration.
 *
 * The page is IPTerebi's own dark blue, which is what makes it this app rather
 * than that one. Cobalt is the accent: solid where it is a fill (the thing
 * selected, the main button), lifted to [accent] where it is text or a small
 * mark, which in full cobalt would sink into the page. Pink, from the icon's
 * smile, is for favourites. No yellow: the owner asked for it out.
 *
 * Earlier versions lit the page cobalt from the top and set navy buttons on
 * it, and on the phone every button was blue on blue however it was outlined.
 * A flat page and white-tinted pills are what fixed that; keep them.
 *
 * Dark only, and not because a light scheme would be hard: the app is a frame
 * around moving video, and a light chrome around a dark picture is unpleasant
 * to sit in front of.
 */
object Night {
    /** The page. IPTerebi's dark blue. */
    val ground = Color(0xFF0B1122)
    /** Cards, panels, poster placeholders: one step up from the page. */
    val veil = Color(0xFF151D38)
    /** Dividers, inactive tracks, text-field rims. */
    val edge = Color(0xFF26305A)
    /** Faint rims — a poster's edge against the page. */
    val hairline = Color(0x14FFFFFF)
    /** Quiet pills — the categories you are not in, choices not chosen. */
    val quiet = Color(0x1AFFFFFF)
    val quietText = Color(0xFFD6DEF7)
    /** Secondary and icon buttons. */
    val glass = Color(0x1FFFFFFF)
    /** Text-field wells: a step *below* the panel they sit in. */
    val field = Color(0xFF0E1530)
    /** The accent as a fill: what is selected, the main button. */
    val cobalt = Color(0xFF2F6BFF)
    /** The accent as text or a small mark on the dark page. */
    val accent = Color(0xFF8FB4FF)
    val ink = Color(0xFFEEF2FF)
    /** Muted text. */
    val inkSoft = Color(0xFF9AA6CA)
    /** Favourites: the pink of Terebi-kun's smile. */
    val pink = Color(0xFFFF8FA3)
    /** The translucent score pill over posters. */
    val badge = Color(0xB31F3C8C)
}

private val ColorScheme = darkColorScheme(
    primary = Night.cobalt,
    onPrimary = Color.White,
    primaryContainer = Night.cobalt,
    onPrimaryContainer = Color.White,
    secondary = Night.accent,
    onSecondary = Night.ground,
    secondaryContainer = Night.cobalt,
    onSecondaryContainer = Color.White,
    tertiary = Night.pink,
    onTertiary = Color(0xFF2A0F1A),
    background = Night.ground,
    onBackground = Night.ink,
    surface = Night.ground,
    onSurface = Night.ink,
    surfaceVariant = Night.edge,
    onSurfaceVariant = Night.inkSoft,
    // Sheets, dialogs and menus draw from these; left unset they are
    // Material's baseline greys, which never looked like they belonged here.
    surfaceContainerLowest = Night.ground,
    surfaceContainerLow = Night.veil,
    surfaceContainer = Night.veil,
    surfaceContainerHigh = Color(0xFF1B2444),
    surfaceContainerHighest = Color(0xFF222C50),
    outline = Night.edge,
    outlineVariant = Night.hairline,
    error = Color(0xFFFF8A80),
    onError = Color(0xFF1A0A08),
)

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
    MaterialTheme(colorScheme = ColorScheme, typography = Type, content = content)
}
