package com.ipterebi.app.ui.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.ui.AspectRatioFrameLayout

/**
 * How the picture fills the screen.
 *
 * A provider's line is not all one shape: a 4:3 channel on a 16:9 screen is
 * pillarboxed, an anamorphic film arrives letterboxed inside its own frame,
 * and a panel that lies about a stream's aspect ratio is common enough that
 * every set-top box has a button for this. Without one the only options were
 * to live with the bars or to stop watching.
 *
 * Kept in plain preferences and applied to the view as it is built, so the
 * choice survives the app being closed and does not flash the wrong shape on
 * the way in.
 */
enum class PictureFit(val label: String, val detail: String, val resizeMode: Int) {
    /** The whole picture, bars and all. Nothing is lost; nothing is hidden. */
    FIT(
        "Fit",
        "The whole picture, with bars where its shape does not match the screen.",
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
    ),

    /** Fills the screen by cropping. The shape is right; the edges are gone. */
    FILL(
        "Fill",
        "Fills the screen by cropping the edges. Shapes stay right.",
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    ),

    /**
     * Fills the screen by stretching. Everyone is slightly wider, and some
     * people prefer that to bars — it is on every television for a reason.
     */
    STRETCH(
        "Stretch",
        "Fills the screen by stretching. Nothing is cropped; everything is wider.",
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
    ),
}

object Picture {

    private const val FILE = "playback"
    private const val KEY = "picture.fit"

    var fit by mutableStateOf(PictureFit.FIT)
        private set

    fun load(context: Context) {
        val stored = prefs(context).getString(KEY, null)
        fit = PictureFit.entries.firstOrNull { it.name == stored } ?: PictureFit.FIT
    }

    fun set(context: Context, value: PictureFit) {
        prefs(context).edit().putString(KEY, value.name).apply()
        fit = value
    }

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}

/** The lengths offered. Long enough to fall asleep to; short enough to be useful. */
val SLEEP_CHOICES = listOf(15, 30, 45, 60, 90)

/**
 * Stop playing in a while.
 *
 * Deliberately *not* stored on disk and deliberately not a service: a sleep
 * timer that survived the app being killed would stop something hours later
 * with no way to see it coming, which is the opposite of what it is for. It
 * lives as long as the app does, and the panel says how long is left.
 *
 * When it goes off the player is stopped rather than paused, which frees the
 * line — the same thing "Free the line" and the notification's Stop do, and
 * the right thing if the reason you fell asleep is that you are done for the
 * night. The screen then offers Play here, as it does after those.
 */
object SleepTimer {

    /** When it goes off, in [android.os.SystemClock.elapsedRealtime] terms, or 0. */
    var deadline by mutableLongStateOf(0L)
        private set

    val running: Boolean get() = deadline > 0

    fun set(minutes: Int, now: Long) {
        deadline = now + minutes * 60_000L
    }

    fun cancel() {
        deadline = 0L
    }

    /** Milliseconds left, or 0 when it is not running or has just gone off. */
    fun remaining(now: Long): Long = if (deadline <= 0) 0 else (deadline - now).coerceAtLeast(0)

    fun hasFired(now: Long): Boolean = running && now >= deadline
}

/** "Stops in 24 minutes", for the panel to show under the choice. */
fun sleepRemainingLabel(remainingMs: Long): String {
    if (remainingMs <= 0) return ""
    val minutes = (remainingMs + 59_999) / 60_000
    return if (minutes == 1L) "Stops in a minute" else "Stops in $minutes minutes"
}
