package com.ipterebi.core

/**
 * When to reconnect a stream that stopped by itself, and when to give up and
 * say so. A channel and a film alike: the player decides *how* — a channel
 * rejoins at the live edge, a film carries on where it was — and this decides
 * whether, and after how long.
 *
 * Streams drop. The panel restarts a channel, a phone walks out of Wi-Fi
 * range, the provider's source hiccups — and without this the picture freezes
 * on its last frame, or a film stops on an error card halfway through. A
 * television reconnects, so this does.
 *
 * Only something that has *played* is reconnected. One that never started was
 * refused — wrong format, the connection limit, a dead channel, a missing file
 * — and the reason for that is worth showing at once rather than papering over
 * with half a minute of retrying.
 *
 * Retries back off, because the panel is the likely reason for the drop and
 * the connection limit makes haste expensive: a line allowing one stream
 * counts the old connection for a few seconds after it goes, so an instant
 * reconnect is refused. A refusal mid-way is just another failed attempt. The
 * whole sequence gives up after about half a minute.
 *
 * Attempts are forgiven once the stream has played steadily for a while, so a
 * channel that drops once an hour reconnects every time, while one that drops
 * every few seconds runs out of attempts.
 */
class StreamReconnect(
    private val delaysMs: List<Long> = DEFAULT_DELAYS_MS,
    private val steadyMs: Long = STEADY_MS,
) {
    private var attempts = 0
    private var played = false
    private var playingSince: Long? = null

    /** Dropped and not yet playing again: what a "Reconnecting…" label is for. */
    var reconnecting = false
        private set

    /** Playback is under way; [nowMs] is any monotonic clock. */
    fun onPlaying(nowMs: Long) {
        played = true
        reconnecting = false
        if (playingSince == null) playingSince = nowMs
    }

    /**
     * The stream stopped on its own — ended, or failed. Returns how long to
     * wait before reconnecting, or null to give up and show why.
     */
    fun onDropped(nowMs: Long): Long? {
        val since = playingSince
        playingSince = null
        if (since != null && nowMs - since >= steadyMs) attempts = 0
        if (!played || attempts >= delaysMs.size) {
            reconnecting = false
            return null
        }
        reconnecting = true
        return delaysMs[attempts++]
    }

    /** Try again, by hand: a fresh start, as if it had just been opened. */
    fun reset() {
        attempts = 0
        played = false
        playingSince = null
        reconnecting = false
    }

    companion object {
        /** Adds up to 30 s. The first waits a second, for the panel to let go. */
        val DEFAULT_DELAYS_MS = listOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L)
        const val STEADY_MS = 30_000L
    }
}
