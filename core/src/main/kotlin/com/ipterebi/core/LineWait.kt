package com.ipterebi.core

/**
 * Waiting for the line, when a channel is refused because the line is full.
 *
 * [StreamReconnect] only retries something that has played, because a refusal
 * at the door usually has a reason worth showing at once. The connection limit
 * is the exception on a television, where it is almost always about to clear:
 *
 * - Moving from the phone to the TV. Stop on the phone, and the panel goes on
 *   counting its connection for a while — a real line refused with 458 for
 *   about fifteen seconds after a connection went.
 * - Changing channel. The old stream is closed before the next is asked for,
 *   but a panel can take a moment to notice. TiviMate's users report this as
 *   "constant error 458".
 *
 * So a refusal for the connection limit is asked again every few seconds for
 * half a minute, under "Waiting for your line", and only then shown as an
 * error. Only the connection limit's own codes: a 403 is also what a refused
 * user agent looks like, and waiting would hold that answer back for half a
 * minute for nothing.
 */
class LineWait(
    private val patienceMs: Long = PATIENCE_MS,
    private val everyMs: Long = EVERY_MS,
) {
    private var since: Long? = null

    /** Refusals are being waited out: what the "Waiting for your line" card is for. */
    val waiting: Boolean get() = since != null

    /**
     * The panel refused with HTTP [code]. Returns how long to wait before
     * asking again, or null to stop waiting and show why.
     */
    fun onRefused(code: Int, nowMs: Long): Long? {
        if (!isConnectionLimit(code)) return null.also { since = null }
        val start = since ?: nowMs.also { since = it }
        if (nowMs - start + everyMs > patienceMs) return null.also { since = null }
        return everyMs
    }

    /** It played, or it was given up on, or another channel was chosen. */
    fun reset() {
        since = null
    }

    companion object {
        /** Twice what a real line took to let go, so a slow panel still gets in. */
        const val PATIENCE_MS = 30_000L
        const val EVERY_MS = 3_000L

        /** What to say once waiting has run out. */
        const val STILL_IN_USE = "Your line is still in use. It allows one stream at a time — if " +
            "something is playing on another device, stop it there (on the phone: Stop in the " +
            "notification, or Free the line in Settings), then try again."
    }
}
