package com.ipterebi.app.playback

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.ipterebi.app.BuildConfig
import com.ipterebi.app.IPTerebiApp
import com.ipterebi.app.TAG_PLAY
import com.ipterebi.app.data.AccountState
import com.ipterebi.core.nextToStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Waking the box up for a recording.
 *
 * One alarm at a time, for the next recording due, re-armed whenever the list
 * changes or a recording ends. Not one alarm per recording: Android counts
 * them against the app, the list is small but unbounded, and the next one is
 * the only one that matters — anything after it gets its alarm when this one
 * fires.
 *
 * **Exact, and declared rather than asked for.** An inexact alarm may be held
 * back by minutes to batch it with others, and a programme that starts without
 * its first minutes is most of the way to useless. From API 31 an exact alarm
 * needs permission, and the usual way to get it is to send the viewer to a
 * Settings screen — which is the same mistake as the file picker was: a
 * television may not have that screen. `USE_EXACT_ALARM` is granted at install
 * with no interface at all, and a thing that records television at an
 * appointed time is what it is for. The box is API 30 and needs none of this;
 * the emulator is 34 and does.
 *
 * `setExactAndAllowWhileIdle` rather than `setExact`, because a box left alone
 * all evening is in doze by the time the programme starts.
 */
object RecordingAlarms {

    /**
     * Sets the alarm for the next recording due, or cancels it if there is
     * none. Safe to call as often as the list changes; it replaces itself.
     */
    suspend fun arm(context: Context) {
        val app = context.applicationContext
        val container = (app as IPTerebiApp).container
        val account = (container.credentials.state.first() as? AccountState.SignedIn)?.account
        val alarms = app.getSystemService(AlarmManager::class.java)
        val pending = pendingIntent(app)

        if (account == null) {
            alarms.cancel(pending)
            return
        }
        val next = nextToStart(container.recordings.current(account), now())
        if (next == null) {
            alarms.cancel(pending)
            if (BuildConfig.DEBUG) Log.d(TAG_PLAY, "recordings: nothing to wake for")
            return
        }

        // Already inside its window — a box that was off at the start should
        // join late rather than skip it, so this fires at once.
        val at = maxOf(next.startSeconds, now()) * 1000
        if (!canBeExact(alarms)) {
            // Better late than not at all, and it says so in the log rather
            // than failing silently at the moment a programme starts.
            if (BuildConfig.DEBUG) Log.d(TAG_PLAY, "recordings: no exact alarms, falling back")
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG_PLAY,
                "recordings: waking in ${(at / 1000 - now()) / 60} min for ${next.title}",
            )
        }
    }

    private fun canBeExact(alarms: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    private fun now() = System.currentTimeMillis() / 1000

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, RecordingAlarmReceiver::class.java).setAction(ACTION_DUE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    const val ACTION_DUE = "com.ipterebi.app.RECORDING_DUE"
}

/**
 * The alarm going off, and the box having been restarted.
 *
 * Both end in the same place: work out what is due now and start it. A reboot
 * loses every alarm Android was holding, so re-arming on boot is not a nicety —
 * without it a box unplugged in the afternoon records nothing that evening.
 */
class RecordingAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? IPTerebiApp ?: return
        val container = app.container
        val pending = goAsync()
        container.scope.launch {
            try {
                val account = (container.credentials.state.first() as? AccountState.SignedIn)?.account
                if (account != null) {
                    val due = nextToStart(
                        container.recordings.current(account),
                        System.currentTimeMillis() / 1000,
                    )
                    // Only start one that is actually due. The alarm can fire
                    // early on some devices, and on boot nothing is due at all
                    // unless a recording was running when the power went.
                    if (due != null && due.wants(System.currentTimeMillis() / 1000)) {
                        if (BuildConfig.DEBUG) Log.d(TAG_PLAY, "recordings: starting ${due.title}")
                        RecordingService.record(app, due.id)
                    }
                }
                RecordingAlarms.arm(app)
            } finally {
                pending.finish()
            }
        }
    }
}
