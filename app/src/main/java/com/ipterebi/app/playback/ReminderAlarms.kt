package com.ipterebi.app.playback

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ipterebi.app.BuildConfig
import com.ipterebi.app.IPTerebiApp
import com.ipterebi.app.MainActivity
import com.ipterebi.app.R
import com.ipterebi.app.TAG_PLAY
import com.ipterebi.app.data.AccountState
import com.ipterebi.core.Reminder
import com.ipterebi.core.dueSeconds
import com.ipterebi.core.nextReminder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Telling someone a programme is about to start.
 *
 * One alarm at a time, for whichever reminder is due next, re-armed whenever
 * the list changes and after a reboot. The same shape recording uses, and for
 * the same reason: Android gives no prize for holding a hundred alarms, and a
 * single one re-armed from the list cannot drift out of step with it.
 *
 * Unlike a recording this wants nothing — no line, no stream, no file. It
 * posts a notification and gets out of the way. Tapping it opens the app on
 * the channel, which is the one thing the viewer actually wanted doing.
 */
object ReminderAlarms {

    /** Sets the alarm for the next reminder due, or cancels it if none is. */
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
        val next = container.reminders.now(account).nextReminder(now())
        if (next == null) {
            alarms.cancel(pending)
            return
        }

        val at = next.dueSeconds * 1000
        // Exact where the system allows it. A reminder a few minutes adrift
        // is still useful, which is why this falls back rather than refusing:
        // the point is to be told before kick-off, not to the second.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms()) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG_PLAY, "reminder: waking in ${(at / 1000 - now()) / 60} min for ${next.title}")
        }
    }

    /** Posts the notification for one that has come due. */
    fun tell(context: Context, reminder: Reminder) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    "Reminders",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = "A programme you asked to be told about is starting." },
            )
        }
        // Android 13 wants permission to say anything at all. Without it the
        // post is dropped silently, so there is no point building one.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            if (BuildConfig.DEBUG) Log.d(TAG_PLAY, "reminder: not allowed to post notifications")
            return
        }

        val open = PendingIntent.getActivity(
            context,
            reminder.streamId,
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(EXTRA_CHANNEL, reminder.streamId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        manager.notify(
            reminder.id.hashCode(),
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_nav_live)
                .setContentTitle(reminder.title)
                .setContentText("Starting soon on ${reminder.channelName}")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build(),
        )
        if (BuildConfig.DEBUG) Log.d(TAG_PLAY, "reminder: told about ${reminder.title}")
    }

    private fun now() = System.currentTimeMillis() / 1000

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        1,
        Intent(context, ReminderAlarmReceiver::class.java).setAction(ACTION_DUE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    const val ACTION_DUE = "com.ipterebi.app.REMINDER_DUE"
    const val EXTRA_CHANNEL = "reminder_channel"
    private const val CHANNEL = "reminders"
}

/**
 * The alarm going off, and the device having been restarted.
 *
 * A reboot loses every alarm Android was holding, so `BOOT_COMPLETED` is not
 * a nicety here any more than it is for recording: a phone restarted in the
 * afternoon would otherwise say nothing that evening.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as IPTerebiApp
        val pending = goAsync()
        app.container.scope.launch {
            try {
                val account =
                    (app.container.credentials.state.first() as? AccountState.SignedIn)?.account
                if (account != null && intent.action == ReminderAlarms.ACTION_DUE) {
                    // Everything due, not just the first: two matches can
                    // start together, and the alarm only carries one wake-up.
                    val now = System.currentTimeMillis() / 1000
                    app.container.reminders.now(account)
                        .filter { it.dueSeconds <= now }
                        .forEach { due ->
                            ReminderAlarms.tell(app, due)
                            app.container.reminders.forget(account, due.id)
                        }
                }
                // Re-armed whichever way we got here, so a reboot picks the
                // list back up and a fired one moves on to the next.
                ReminderAlarms.arm(app)
            } finally {
                pending.finish()
            }
        }
    }
}
