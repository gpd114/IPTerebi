package com.ipterebi.app.playback

/**
 * The stream playing in the app, if there is one, and how to stop it — so the
 * notification's Stop and Settings' "Free the line" can let go of the line
 * without reaching into the player screen.
 *
 * Moving to the TV is the case this is for. The line allows one connection,
 * so the phone has to give it up first — and the phone may be locked, playing
 * on as sound, with nothing on screen to press.
 *
 * The player screen registers while it has a player and is the one that stops:
 * it knows how, and what to show afterwards. Main thread only, like everything
 * that touches the player.
 */
object ActivePlayback {
    private var stop: (() -> Boolean)? = null

    /**
     * Registers [stop], which stops playback and says whether anything had been
     * playing. Returns the call that unregisters it.
     */
    fun register(stop: () -> Boolean): () -> Unit {
        this.stop = stop
        return { if (this.stop === stop) this.stop = null }
    }

    /** Stops whatever is playing. False if nothing was. */
    fun stop(): Boolean = stop?.invoke() ?: false
}
