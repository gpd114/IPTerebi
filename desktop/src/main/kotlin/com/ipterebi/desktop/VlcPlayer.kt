package com.ipterebi.desktop

import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import java.io.File

/**
 * The player, which is the one thing that could not come across from `:app`.
 *
 * Media3 is Android-only, so something else has to decode the stream, and on a
 * line like the owner's that means libvlc rather than anything in the JDK.
 * JavaFX's media player handles a short list of containers and MPEG-TS over
 * HTTP is not on it; a real line serves TS, HLS, and films in mp4, mkv and avi
 * from the same account. VLC is also what the rest of the world points IPTV at,
 * so a stream that plays in VLC and not here is a bug on this side, which makes
 * every report easier to read.
 *
 * **It needs VLC installed, and that is this build's one awkward dependency.**
 * `NativeDiscovery` finds a 64-bit install in Program Files without being told
 * where to look. It must be the 64-bit VLC against a 64-bit JVM: mixing them
 * fails at the `libvlc.dll` load with a message about the image format, which
 * reads like a corrupt download rather than the wrong architecture. The
 * installer could carry its own copy of libvlc instead -- about 50 MB and
 * legal, VLC being LGPL -- and that is the obvious next step if asking people
 * to install VLC first turns out to be the thing that stops them.
 */
class VlcPlayer private constructor(
    val component: EmbeddedMediaPlayerComponent,
) {

    /** What the player last said went wrong, for the screen to show. */
    var onFailure: (() -> Unit)? = null

    /** Called once frames are actually arriving, so the screen can stop saying "opening". */
    var onPlaying: (() -> Unit)? = null

    init {
        component.mediaPlayer().events().addMediaPlayerEventListener(
            object : MediaPlayerEventAdapter() {
                override fun error(mediaPlayer: MediaPlayer) {
                    onFailure?.invoke()
                }

                override fun playing(mediaPlayer: MediaPlayer) {
                    onPlaying?.invoke()
                }
            }
        )
    }

    /**
     * Opens [url].
     *
     * **The URL is never logged.** The credentials are in its path, not in a
     * header, so a logged URL is a logged password -- the same rule the Android
     * build follows, and the reason `withoutCredentialValues` exists in
     * `:core`.
     *
     * The user agent is a media option rather than a global one, because it is
     * per account: a panel that refuses VLC's string wants whatever the viewer
     * put in Settings instead, and that choice travels with the account. A
     * default user agent is refused by a meaningful share of panels, which
     * shows up as a 403 on the stream while every API call succeeds.
     */
    fun play(url: String, userAgent: String) {
        component.mediaPlayer().media().play(
            url,
            ":http-user-agent=$userAgent",
            // Follows the redirect a lot of panels answer a stream request
            // with, rather than stopping on it.
            ":http-continuous",
        )
    }

    /**
     * Lets the line go.
     *
     * Stop rather than pause, and that is the connection limit again: a paused
     * player keeps its socket open and so keeps the single stream a line
     * usually allows, which is exactly why `PlayerScreen` on Android stops on
     * `ON_STOP`. Here it matters when the window closes or the viewer picks
     * another channel.
     */
    fun stop() {
        runCatching { component.mediaPlayer().controls().stop() }
    }

    fun release() {
        runCatching { component.release() }
    }

    companion object {
        /**
         * Finds libvlc and builds a player, or returns null when VLC is not
         * installed. Null is a sentence on the screen, not a crash: "install
         * VLC" is something the viewer can act on, and a stack trace about a
         * missing DLL is not.
         */
        fun createOrNull(): VlcPlayer? = runCatching {
            if (!NativeDiscovery().discover()) return null
            // `--quiet`, because without it libvlc writes a line per plugin to
            // stdout on every start -- three hundred of them, all saying
            // "stale plugins cache" because the DLLs in Program Files are
            // newer than the cache VLC built when it was installed. Harmless,
            // and it buries anything this app has to say. Nothing is lost: a
            // real failure arrives through the event listener above rather
            // than through libvlc's own logging.
            //
            // Not `--reset-plugins-cache`, which would try to rewrite that
            // cache inside Program Files and fail without administrator
            // rights, and not `--no-plugins-cache`, which makes every start
            // rescan the plugin directory.
            VlcPlayer(EmbeddedMediaPlayerComponent("--quiet"))
        }.getOrNull()

        /**
         * Whether a VLC install can be seen, for the message on the sign-in
         * screen. Checked by looking rather than by trying to start a player,
         * so the answer is available before anything is playing.
         */
        fun vlcLooksInstalled(): Boolean =
            runCatching { NativeDiscovery().discover() }.getOrDefault(false)

        /** Where people usually have it, for the hint when it is missing. */
        val usualPlace: String = File(
            System.getenv("ProgramFiles") ?: "C:\\Program Files",
            "VideoLAN\\VLC",
        ).path
    }
}
