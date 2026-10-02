package com.ipterebi.app

import android.app.Application

import com.ipterebi.app.data.AccountState
import com.ipterebi.app.playback.RecordingAlarms
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class IPTerebiApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Two things about recordings that have to happen before any screen.
        //
        // A recording left in the RECORDING state is one this process did not
        // live through — the box unplugged, or Android reclaiming it — and
        // leaving it looking live would have the scheduler believe the line is
        // busy for ever. And the alarm is re-armed, because an app that was
        // force-stopped has none: Android cancels them all, and nothing else
        // would ever set it again.
        container.scope.launch {
            val account =
                (container.credentials.state.first() as? AccountState.SignedIn)?.account
            if (account != null) {
                container.recordings.failInterrupted(
                    account,
                    "The box stopped before the recording finished.",
                )
            }
            RecordingAlarms.arm(this@IPTerebiApp)
        }
    }
}
