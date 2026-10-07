package com.ipterebi.app.ui.theme

import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ipterebi.app.R
import com.ipterebi.app.ui.isTelevision
import kotlin.math.abs

/**
 * Everything a theme colours, by role, on the pattern of the owner's other app,
 * Debritsu (its `ui/Theme.kt`): flat throughout — no gradients, rims or sheen —
 * so each role is one colour, and what you press is told apart by fill.
 *
 * Two themes, as Debritsu has: [Dark], the default — the TV guide's colours,
 * near-black with dark cells and the blue only as an accent — and [Light], a
 * pale page with white cards and IPTerebi's dark blue as the accent, where
 * Debritsu's Pastel has its dark purple. Switched in Settings on the phone,
 * held by [Appearance]; the TV has Dark alone.
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
    /**
     * What is chosen among peers: the section the rail is on, the chip you are
     * browsing, with [onChosen] on it.
     *
     * Not [cobalt], although on Light it is the same colour. On Dark [cobalt]
     * is also [focus], so a chosen chip and the focused one were the same blue
     * block and a remote could not say which was which. Chosen is therefore the
     * one the theme does not spend on focus.
     */
    val chosen: Color,
    /** Ink on [chosen]. */
    val onChosen: Color,
    /** Primary text. */
    val ink: Color,
    /** Muted text. */
    val inkSoft: Color,
    /** Favourites: the pink of Terebi-kun's smile, deep enough to read on the page. */
    val pink: Color,
    /** The translucent score pill over posters. White goes on it. */
    val badge: Color,
    /**
     * Where the remote is. A fill, not a ring: focus has to be the loudest
     * thing on a screen from across a room, and it has to keep whatever text
     * the caller already chose legible, so it is dark enough for ink on Dark
     * and pale enough for ink on Light.
     */
    val focus: Color,
)

/** Pale, with IPTerebi's dark blue as the accent. The default until the app took the guide's look. */
val Light = Palette(
    dark = false,
    ground = Color(0xFFF2F4FA), veil = Color(0xFFFFFFFF), edge = Color(0xFFDDE3F0),
    hairline = Color(0xFFE6EAF4), quiet = Color(0xFFE6EBF7), quietText = Color(0xFF34436A),
    glass = Color(0xFFE3E9F7), glassIcon = Color(0xFF1B3478), field = Color(0xFFFFFFFF),
    cobalt = Color(0xFF1B3478), accent = Color(0xFF1B3478),
    chosen = Color(0xFF1B3478), onChosen = Color(0xFFFFFFFF),
    ink = Color(0xFF141A30), inkSoft = Color(0xFF6A7494),
    pink = Color(0xFFC2456B), badge = Color(0xB3142A66),
    focus = Color(0xFF7F9CD8),
)

/**
 * [Light] for a television: the same colours, dimmed, and with no pure white
 * anywhere.
 *
 * **The reason is the box's HDR, which is on and cannot be turned off.** The
 * owner's verdict on the light theme there was that it is "a bit too bright and
 * in your face", and they named the cause themselves. With HDR on, the panel
 * maps SDR white towards its *peak* brightness rather than towards paper — so
 * `#FFFFFF` on a card is not a page, it is a lamp, and a screen that is mostly
 * card is mostly lamp. Nothing in the app can turn that off; the only thing it
 * can do is stop asking for peak.
 *
 * So the page drops from 90% luminance to 68% and the cards from 100% to 79%,
 * and [Light]'s white `veil` and `field` become a pale blue-grey. The ink goes
 * *down* to keep up: measured, ink on the page is 11.2:1 and on a card 12.9:1,
 * which is more than [Light] has, because a dimmer page can afford a darker ink
 * without either end touching the limits. Nothing here is a guess — every pair
 * was computed before it was written.
 *
 * Only on a television, and chosen by [Appearance.lightFor] rather than by a
 * setting: a phone is held at arm's length in a lit room and wants [Light]'s
 * brightness, which the owner asked to keep.
 */
val LightTv = Palette(
    dark = false,
    ground = Color(0xFFD2D7E1), veil = Color(0xFFE2E6EE), edge = Color(0xFFBAC1D2),
    hairline = Color(0xFFC6CDDB), quiet = Color(0xFFC4CCDC), quietText = Color(0xFF2A3450),
    glass = Color(0xFFC9D1E0), glassIcon = Color(0xFF1B3478), field = Color(0xFFE2E6EE),
    cobalt = Color(0xFF1B3478), accent = Color(0xFF1B3478),
    chosen = Color(0xFF1B3478), onChosen = Color(0xFFE2E6EE),
    ink = Color(0xFF1A2032), inkSoft = Color(0xFF4E587A),
    pink = Color(0xFF9E2F55), badge = Color(0xB3142A66),
    focus = Color(0xFF6B89C4),
)

/**
 * The TV guide's colours: its near-black page, its dark cells for cards and
 * quiet fills, the soft blue for what is marked, pink for favourites. The
 * owner asked for the whole app to look like the guide, so this is the
 * default; the TV app has nothing else (see [Appearance.SWITCHABLE]).
 *
 * The blue is a fill where something is chosen and lifted for text, which in
 * the dark blue would be unreadable on a page this dark.
 */
val Dark = Palette(
    dark = true,
    ground = Color(0xFF0B0C11), veil = Color(0xFF191B23), edge = Color(0xFF2A2D36),
    hairline = Color(0x14FFFFFF), quiet = Color(0xFF20263A), quietText = Color(0xFFDADDE6),
    glass = Color(0xFF20263A), glassIcon = Color(0xFFE6E9F2), field = Color(0xFF121419),
    cobalt = Color(0xFF2F5FE0), accent = Color(0xFF9DB5FF),
    chosen = Color(0xFF9DB5FF), onChosen = Color(0xFF0B0C11),
    ink = Color(0xFFF1F3F8), inkSoft = Color(0xFFA3A8B8),
    pink = Color(0xFFFF8FA3), badge = Color(0xB31F3C8C),
    focus = Color(0xFF2F5FE0),
)

/**
 * The colours in use, read from the current [Palette], which is held as state:
 * switching theme recolours everything that has read one, without any screen
 * having to pass a palette about. Named `Night` from when the app had only one.
 */
object Night {
    var palette by mutableStateOf(Dark)

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
    val chosen get() = palette.chosen
    val onChosen get() = palette.onChosen
    val ink get() = palette.ink
    val inkSoft get() = palette.inkSoft
    val pink get() = palette.pink
    val badge get() = palette.badge

    /** The block of colour behind whatever the remote is on. */
    val focus get() = palette.focus

    /**
     * Ink on a [focus] fill.
     *
     * Not a palette role because it follows from one, and now it is worked out
     * rather than assumed: it used to be white on Dark and the ink on Light,
     * which was right only because the focus fill was the one colour per theme.
     * [focusOver] can hand back a lifted fill, and white on a pale blue is not
     * readable, so the ink follows the fill it is going on.
     */
    val onFocus get() = inkOn(palette.focus)

    /**
     * The focus fill to paint over [under], which is not always [focus].
     *
     * **Because the accent cannot show up on itself.** Measured on Dark:
     * `focus` and `cobalt` are the same `#2F5FE0`, so a chip that is already
     * chosen, or the main button, gained focus and *nothing changed* — a
     * contrast of **1.00**. That is the owner's report that focus is hardly
     * visible in Settings, and Settings is the screen it bites hardest on,
     * because it is almost nothing but chosen chips and the remote lands on
     * the chosen one.
     *
     * Where the fill underneath is a different colour this answers [focus] and
     * nothing changes. Where it is near enough to vanish into, the fill is
     * lifted clear — towards the ink on Dark, towards the page on Light — which
     * on Dark turns the block over a cobalt button into a pale blue at 3.2:1
     * against it. Derived from [focus] rather than stored beside it, so there
     * is no second colour to fall out of step with the first.
     *
     * Only the few components whose own background can *be* the accent need to
     * say what they are painting over; everything else leaves it alone.
     */
    fun focusOver(under: Color?): Color {
        val fill = palette.focus
        if (under == null || !near(under, fill)) return fill
        return lerp(fill, if (palette.dark) palette.ink else palette.ground, 0.62f)
    }

    /**
     * White or the theme's darkest ink, whichever reads on [fill].
     *
     * By luminance rather than by theme, so a lifted focus fill gets dark ink
     * on Dark — where everything else gets white — without the caller having to
     * know which fill it ended up with.
     */
    fun inkOn(fill: Color): Color =
        if (fill.luminance() > 0.4f) (if (palette.dark) palette.ground else palette.ink) else Color.White

    /**
     * Whether two fills are close enough that one painted over the other would
     * not be seen. Channel-wise rather than by luminance, which would call two
     * colours of the same weight and different hue the same thing.
     */
    private fun near(a: Color, b: Color) =
        abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue) < 0.35f
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

/**
 * Which theme is chosen, kept in plain preferences so it can be read before the
 * first frame. [Dark], the guide's look, unless Light has been chosen.
 */
object Appearance {
    private const val FILE = "appearance"

    // A new key, not "theme": when the guide's colours became the default,
    // a Light chosen back when Light was the default should not keep anyone
    // from seeing them. Light is one tap away in Settings.
    private const val KEY = "theme.guide"

    /** Whether Settings offers Light as well. The TV app sets this false. */
    const val SWITCHABLE = true

    fun load(context: Context) {
        val light = SWITCHABLE && prefs(context).getString(KEY, null) == "light"
        Night.palette = if (light) lightFor(context) else Dark
    }

    fun set(context: Context, dark: Boolean) {
        prefs(context).edit().putString(KEY, if (dark) "dark" else "light").apply()
        Night.palette = if (dark) Dark else lightFor(context)
    }

    /**
     * Which light palette: [LightTv] on a television, [Light] anywhere else.
     *
     * Not a third setting. There is one choice, Dark or Light, and this decides
     * what Light *means* on the box — where HDR is on and cannot be turned off,
     * so a white card reads as a lamp rather than as paper. See [LightTv]. A
     * viewer should not have to understand their television's tone mapping to
     * find a screen they can look at.
     */
    private fun lightFor(context: Context) = if (isTelevision(context)) LightTv else Light

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}

/**
 * Inter, in Latin-only cuts of about 50 KB a weight.
 *
 * The app wore M PLUS Rounded 1c, which came over from Debritsu along with the
 * rest of that app's look. It suits a soft little library; over a guide grid it
 * reads as a toy — the round terminals close up the counters at cell sizes and
 * the digits go pudgy, so a clock down the edge of a schedule never quite
 * lines up. Inter was drawn for screen interfaces at small sizes and is
 * deliberately characterless, which is what a page full of channel names and
 * times wants: it gets out of the way.
 *
 * The cut is the same treatment as before — Latin only, because the full font
 * carries every script and costs megabytes, and a channel named in Japanese or
 * Arabic falls back to the system font on its own. Inter is under the SIL Open
 * Font License, which allows bundling and subsetting on condition the licence
 * travels with it: assets/licenses/inter_OFL.txt, and it must stay.
 */
private val Ui = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
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

/**
 * The scale. One face, four weights, and the hierarchy carried by size and
 * colour rather than by weight: everything titled used to be ExtraBold, which
 * is what made the app read young. Bold is kept for the one thing on a screen
 * that matters, and body text sits at Regular.
 *
 * Times, channel numbers, durations and bitrates all stack in columns, so
 * anything showing digits takes [tabular] — see the note there.
 */
private val Type = Typography(
    displaySmall = TextStyle(fontFamily = Ui, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Ui, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = Ui, fontWeight = FontWeight.Bold, fontSize = 21.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = Ui, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Ui, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = (-0.1).sp),
    titleSmall = TextStyle(fontFamily = Ui, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = Ui, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Ui, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Ui, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Ui, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    labelMedium = TextStyle(fontFamily = Ui, fontWeight = FontWeight.Medium, fontSize = 12.5.sp),
    labelSmall = TextStyle(fontFamily = Ui, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.2.sp),
)

/**
 * Digits that line up in a column: a clock down a guide's edge, channel
 * numbers down a list, times under one another in a programme's details.
 *
 * A proportional 1 is narrower than a 0, so a clock ticking from 19:11 to
 * 19:12 shifts the whole line sideways, and numbers in a column do not line
 * up at all. `tnum` gives every digit the same width. Put it on anything
 * whose text is mostly numbers; it does nothing to words.
 */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

/**
 * The corners, as roles rather than a number per call site. They were 18 and
 * 22 with pills for anything chosen, which is a soft look for a schedule; a
 * guide wants edges near square. One step for each kind of thing, so a card
 * inside a panel is visibly the smaller object.
 */
object Corners {
    /** Settings sections, sheets, the card over video. */
    val panel = RoundedCornerShape(12.dp)
    /** A channel row, a poster, a message card. */
    val card = RoundedCornerShape(10.dp)
    /** Buttons, choice cells, text fields, the focus ring. */
    val control = RoundedCornerShape(8.dp)
    /** A channel number, a small status label, a guide cell. */
    val tag = RoundedCornerShape(6.dp)
}

@Composable
fun IPTerebiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme(Night.palette), typography = Type, content = content)
}
