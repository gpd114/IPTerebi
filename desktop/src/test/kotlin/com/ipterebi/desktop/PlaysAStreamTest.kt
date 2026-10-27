package com.ipterebi.desktop

import com.ipterebi.core.StreamFormat
import com.ipterebi.core.XtreamAccount
import com.ipterebi.core.XtreamClient
import kotlinx.coroutines.runBlocking
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.AudioPlayerComponent
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The one thing about this module that no unit test in `:core` can answer:
 * whether libvlc loads and will actually take a stream from an Xtream panel.
 *
 * Everything above the player is `:core`, which is tested properly and on
 * every machine. The player is native code found at runtime, and the way it
 * fails is specific and unhelpful — a 32-bit VLC against a 64-bit JVM throws
 * about the image format, which reads like a corrupt download. So this walks
 * the whole path once: sign in, ask for a category, ask for its channels,
 * build the stream URL the app builds, and wait for libvlc to say it is
 * playing.
 *
 * **It skips rather than fails when it cannot run**, and both reasons are
 * ordinary: CI has no VLC, and the fake panel is something you start by hand.
 * A test that goes red on every machine but one is a test people learn to
 * ignore.
 *
 *     cd tools/fakepanel && java FakePanel.java media 8080
 *     gradle :desktop:test
 */
class PlaysAStreamTest {

    private val base = "http://127.0.0.1:8080"

    @Test
    fun `libvlc plays a channel from the panel`() {
        if (!panelIsUp()) {
            println("skipped: no fake panel on $base — start it in tools/fakepanel")
            return
        }
        if (!NativeDiscovery().discover()) {
            println("skipped: no libvlc on this machine")
            return
        }

        val client = XtreamClient()
        val account = XtreamAccount(
            base = base,
            username = "demo",
            password = "demo",
            format = StreamFormat.TS,
        )

        val url = runBlocking {
            val status = client.authenticate(account)
            assertTrue(status.info.auth == 1, "the fake panel refused the demo line")

            val groups = client.liveCategories(account)
            assertTrue(groups.isNotEmpty(), "no categories came back")

            // The first group that actually holds something: a real line is
            // full of empty categories and the fake panel copies that. A plain
            // loop rather than a sequence, because a sequence's lambda is not
            // a place a suspending call may be made from.
            var first: Int? = null
            for (group in groups) {
                val channels = client.liveStreams(account, group.id)
                if (channels.isNotEmpty()) {
                    first = channels.first().streamId
                    break
                }
            }
            assertTrue(first != null, "no category held a channel")

            client.liveStreamUrl(account, first!!)
        }

        // Headless: this decodes without needing a window, which is the only
        // way this can run from a build.
        val component = AudioPlayerComponent()
        val playing = CountDownLatch(1)
        component.mediaPlayer().events().addMediaPlayerEventListener(
            object : MediaPlayerEventAdapter() {
                override fun playing(mediaPlayer: MediaPlayer) = playing.countDown()
            }
        )

        try {
            val accepted = component.mediaPlayer().media().play(
                url,
                ":http-user-agent=${account.userAgent}",
            )
            assertTrue(accepted, "libvlc would not open the stream at all")
            assertTrue(
                playing.await(20, TimeUnit.SECONDS),
                "libvlc never reached playing. The URL is not printed here on " +
                    "purpose: the credentials are in its path.",
            )
        } finally {
            component.mediaPlayer().controls().stop()
            component.release()
        }
    }

    private fun panelIsUp(): Boolean = runCatching {
        val connection = (URL("$base/player_api.php").openConnection() as HttpURLConnection).apply {
            connectTimeout = 500
            readTimeout = 500
            requestMethod = "GET"
        }
        connection.responseCode in 200..499
    }.getOrDefault(false)
}
