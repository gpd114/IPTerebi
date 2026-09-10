package com.ipterebi.app.ui.player

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.InterruptedIOException
import java.util.concurrent.atomic.AtomicLong

/**
 * Waits before connecting, when told to — how a reconnect backs off.
 *
 * The wait happens here, on the loading thread, rather than on a timer before
 * `prepare()`, so that for its whole length the player reads as *buffering*.
 * A timer would leave it stopped in the meantime, and stopped is what takes
 * the app out of the foreground when the screen is off — after which Android
 * does not let it back in, and a reconnect in a pocket would never happen.
 *
 * [delayMs] is taken once: only the first connection after a drop waits.
 */
@UnstableApi
class DelayedOpenFactory(
    private val upstream: DataSource.Factory,
    private val delayMs: AtomicLong,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = DelayedOpen(upstream.createDataSource(), delayMs)
}

@UnstableApi
private class DelayedOpen(
    private val upstream: DataSource,
    private val delayMs: AtomicLong,
) : DataSource by upstream {
    override fun open(dataSpec: DataSpec): Long {
        val wait = delayMs.getAndSet(0)
        if (wait > 0) {
            try {
                Thread.sleep(wait)
            } catch (e: InterruptedException) {
                // The player was stopped or released mid-wait.
                Thread.currentThread().interrupt()
                throw InterruptedIOException("reconnect cancelled")
            }
        }
        return upstream.open(dataSpec)
    }
}

/**
 * Leaves retrying a live MPEG-TS stream to the screen's reconnect, in the two
 * cases where ExoPlayer's own retry does harm.
 *
 * **Part-way through.** ExoPlayer resumes a progressive stream at the byte it
 * reached, with a Range request. A live stream has no bytes to range over: the
 * panel answers from now, with a 200, and the HTTP layer then reads and throws
 * away every byte already watched to get back to "the same place" — at the
 * pace the panel sends, which is the pace it was watched. After ten minutes,
 * ten minutes of nothing. A fresh connection from the live edge is the right
 * answer, and the reconnect is what makes one.
 *
 * **Refused.** A 403, a 456, a 503 are the panel's answer, not a blip, and
 * ExoPlayer's retry asks again three times in as many seconds — measured
 * against the fake panel, four requests for every one intended. On a line
 * allowing one connection that is hammering the door that just said no. A
 * refusal before the channel ever played is shown at once; one while
 * reconnecting waits its turn in the back-off.
 *
 * Other failures before anything arrived — a lookup, a connect timeout — keep
 * the default retries: those start from byte zero, a fresh connection anyway.
 */
@UnstableApi
class LiveFailsFast : DefaultLoadErrorHandlingPolicy() {
    override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long =
        when {
            info.loadEventInfo.bytesLoaded > 0 -> C.TIME_UNSET
            info.exception is HttpDataSource.InvalidResponseCodeException -> C.TIME_UNSET
            else -> super.getRetryDelayMsFor(info)
        }
}
