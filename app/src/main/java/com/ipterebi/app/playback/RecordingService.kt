package com.ipterebi.app.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.ipterebi.app.AppContainer
import com.ipterebi.app.data.AccountState
import com.ipterebi.app.BuildConfig
import com.ipterebi.app.IPTerebiApp
import com.ipterebi.app.MainActivity
import com.ipterebi.app.R
import com.ipterebi.app.TAG_PLAY
import com.ipterebi.core.FAT32_MAX_BYTES
import com.ipterebi.core.Recording
import com.ipterebi.core.RecordingState
import com.ipterebi.core.SPACE_HEADROOM_BYTES
import com.ipterebi.core.XtreamAccount
import com.ipterebi.core.describeStreamHttpError
import com.ipterebi.core.recordingFileName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.OutputStream

/**
 * Writing a channel to a file while the box gets on with something else.
 *
 * A foreground service because that is the only way Android lets a process
 * keep a socket open with the screen off, and because a recording is exactly
 * the kind of thing a viewer should be able to see is happening. It does the
 * plain thing: open the live URL, read bytes, write bytes, stop at the end.
 *
 * What is not plain is everything around it, and all of it follows from the
 * one connection:
 *
 * - **The line is taken before the stream is opened.** Whatever is playing is
 *   stopped through [ActivePlayback] first, and the panel is given a moment to
 *   notice — a real line answered 458 to every reconnect for about fifteen
 *   seconds after a connection went away. Opening first and stopping second
 *   gets the recording refused by our own player.
 * - **A refusal is not retried hard.** The panel has just said no; asking four
 *   times in three seconds is what made a film fail on an error card. The
 *   back-off is slow and the recording is given up on rather than hammered.
 * - **The volume can fill and the file can grow too big.** A USB stick is
 *   usually FAT32, which stops dead at four gigabytes, and the box's own
 *   storage has under a gigabyte free. Both are checked as it writes, and both
 *   end the recording tidily with a sentence saying what happened — a file
 *   that stops early and plays is worth far more than a write error.
 *
 * Nothing here decides *whether* a recording may run; that is `Recordings` in
 * core, where it is tested.
 */
class RecordingService : Service() {

    private val jobs = SupervisorJob()
    private val scope = CoroutineScope(jobs + Dispatchers.IO)

    /** The one recording running, if any. One connection, so at most one. */
    @Volatile
    private var running: Job? = null

    @Volatile
    private var runningId: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as IPTerebiApp).container
        when (intent?.action) {
            ACTION_STOP -> {
                stopRecording()
                return START_NOT_STICKY
            }
            ACTION_RECORD -> {
                val id = intent.getStringExtra(EXTRA_ID).orEmpty()
                if (id.isBlank() || id == runningId) return START_NOT_STICKY
                start(container, id)
            }
            else -> return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    private fun start(container: AppContainer, id: String) {
        running?.cancel()
        runningId = id
        startForeground(NOTIFICATION_ID, notification("Starting", "Opening the channel"))
        running = scope.launch {
            val account = (container.credentials.state.first() as? AccountState.SignedIn)?.account
            if (account == null) {
                stopSelf()
                return@launch
            }
            val recording = container.recordings.current(account).firstOrNull { it.id == id }
            if (recording == null) {
                stopSelf()
                return@launch
            }
            val outcome = runCatching { record(container, account, recording) }
                .getOrElse { e ->
                    if (BuildConfig.DEBUG) Log.w(TAG_PLAY, "recording ${recording.id} threw", e)
                    Outcome.Failed(e.message ?: "The recording stopped unexpectedly.")
                }
            container.recordings.put(account, outcome.applyTo(recording))
            // The next one, now this is out of the way. Without this a box
            // left alone records the first thing booked and nothing after it.
            RecordingAlarms.arm(this@RecordingService)
            runningId = ""
            stopSelf()
        }
    }

    private fun stopRecording() {
        running?.cancel()
        running = null
        runningId = ""
        stopSelf()
    }

    /** Where a recording got to, and what to write into the store about it. */
    private sealed interface Outcome {
        data class Done(val bytes: Long, val document: String) : Outcome
        data class Failed(val why: String, val bytes: Long = 0, val document: String = "") : Outcome

        fun applyTo(recording: Recording): Recording = when (this) {
            is Done -> recording.copy(
                state = RecordingState.DONE,
                bytes = bytes,
                document = document,
            )
            is Failed -> recording.copy(
                state = RecordingState.FAILED,
                bytes = bytes,
                document = document,
                failure = why,
            )
        }
    }

    private suspend fun record(
        container: AppContainer,
        account: XtreamAccount,
        recording: Recording,
    ): Outcome {
        val folder = container.recordings.folderNow()
        if (folder.isBlank()) {
            return Outcome.Failed(
                "No folder to record into. Plug in a USB stick and choose it under Settings.",
            )
        }
        val dir = java.io.File(folder)
        if (!runCatching { dir.mkdirs(); dir.canWrite() }.getOrDefault(false)) {
            return Outcome.Failed(
                "The recording folder cannot be written to. If a stick was taken out, " +
                    "put it back, or choose somewhere else under Settings.",
            )
        }

        // The line first, and a moment for the panel to notice. Opening before
        // stopping is how a recording gets refused by this app's own player.
        //
        // On the main thread, because stopping goes through the ExoPlayer the
        // screen owns and Media3 throws if it is touched from anywhere else.
        // The emulator found this the first time a recording was started: the
        // whole service is on IO, which is right for the socket and wrong for
        // this one call.
        val wasPlaying = withContext(Dispatchers.Main) { ActivePlayback.stop() }
        if (wasPlaying) {
            if (BuildConfig.DEBUG) Log.d(TAG_PLAY, "recording ${recording.id}: stopped playback for the line")
            kotlinx.coroutines.delay(LINE_SETTLE_MS)
        }

        val name = recordingFileName(recording.channelName, recording.title, recording.startSeconds)
        val file = java.io.File(dir, name)

        container.recordings.put(
            account,
            recording.copy(state = RecordingState.RECORDING, document = file.absolutePath),
        )

        val url = container.xtream.liveStreamUrl(account, recording.streamId)
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG_PLAY,
                "recording ${recording.id} to $name: ${account.base}/live/***/***/" +
                    "${recording.streamId}.${account.format.extension}",
            )
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", account.userAgent)
            .build()

        val response = runCatching { container.http.newCall(request).execute() }
            .getOrElse { e ->
                return Outcome.Failed("The panel could not be reached: ", 0, file.absolutePath)
            }

        response.use { reply ->
            if (!reply.isSuccessful) {
                file.delete()
                return Outcome.Failed(describeStreamHttpError(reply.code))
            }
            val body = reply.body ?: return Outcome.Failed("The panel sent nothing.", 0, file.absolutePath)

            val written = runCatching { file.outputStream() }.getOrElse { e ->
                return Outcome.Failed("The file could not be opened: ", 0, file.absolutePath)
            }

            return written.use { out ->
                copy(container, account, recording, body.byteStream().buffered(), out, file.absolutePath)
            }
        }
    }

    /**
     * The loop. Reads until the recording's end, the volume fills, the file
     * grows past what FAT32 holds, or the viewer stops it.
     *
     * The store is updated every few seconds rather than every buffer: a row
     * that shows the size growing is worth having, and a DataStore write per
     * 64 KB is not.
     */
    private suspend fun copy(
        container: AppContainer,
        account: XtreamAccount,
        recording: Recording,
        input: java.io.InputStream,
        out: OutputStream,
        document: String,
    ): Outcome = withContext(Dispatchers.IO) {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        var lastTold = System.currentTimeMillis()
        var lastNotified = 0L

        while (true) {
            ensureActive()
            val now = System.currentTimeMillis() / 1000
            if (now >= recording.stopSeconds) break

            val read = runCatching { input.read(buffer) }.getOrElse { e ->
                return@withContext Outcome.Failed(
                    "The stream stopped: ${e.message}",
                    total,
                    document,
                )
            }
            if (read < 0) break

            runCatching { out.write(buffer, 0, read) }.getOrElse { e ->
                return@withContext Outcome.Failed("Writing failed: ${e.message}", total, document)
            }
            total += read

            if (total >= FAT32_MAX_BYTES) {
                return@withContext Outcome.Failed(
                    "The recording reached four gigabytes, which is as much as one file can " +
                        "hold on this stick. What was recorded up to then is kept.",
                    total,
                    document,
                )
            }

            val millis = System.currentTimeMillis()
            if (millis - lastTold > TELL_EVERY_MS) {
                lastTold = millis
                if (freeBytes(document) < SPACE_HEADROOM_BYTES) {
                    return@withContext Outcome.Failed(
                        "The disk is full. What was recorded up to then is kept.",
                        total,
                        document,
                    )
                }
                container.recordings.put(
                    account,
                    recording.copy(
                        state = RecordingState.RECORDING,
                        bytes = total,
                        document = document,
                    ),
                )
            }
            if (millis - lastNotified > NOTIFY_EVERY_MS) {
                lastNotified = millis
                notify(recording, total)
            }
        }
        Outcome.Done(total, document)
    }

    /**
     * Free space where the recording is going.
     *
     * Asked of the volume rather than of the app's own storage, because the
     * stick is not the app's storage. An unknown answer is treated as plenty:
     * refusing to record because a figure could not be read would be worse
     * than filling a stick.
     */
    /**
     * Free space on the volume being written to.
     *
     * A plain path, so a plain question. An unknown answer is treated as
     * plenty: refusing to record because a figure could not be read would be
     * worse than filling a stick.
     */
    private fun freeBytes(path: String): Long = runCatching {
        android.os.StatFs(java.io.File(path).parent).availableBytes
    }.getOrDefault(Long.MAX_VALUE)

    private fun notify(recording: Recording, bytes: Long) {
        val mb = bytes / (1024 * 1024)
        manager().notify(
            NOTIFICATION_ID,
            notification(recording.title.ifBlank { recording.channelName }, "Recording · $mb MB"),
        )
    }

    private fun notification(title: String, text: String): Notification {
        ensureChannel()
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Recording",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Shown while a programme is being recorded." }
        manager().createNotificationChannel(channel)
    }

    private fun manager() = getSystemService(NotificationManager::class.java)

    override fun onDestroy() {
        jobs.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_RECORD = "com.ipterebi.app.RECORD"
        private const val ACTION_STOP = "com.ipterebi.app.STOP_RECORDING"
        private const val EXTRA_ID = "id"

        /**
         * How long to let the panel notice the connection we just gave up.
         *
         * A real line answered 458 to every reconnect for about fifteen
         * seconds after a phone left Wi-Fi, but that was a connection dropped
         * rather than closed; a clean stop is noticed much sooner. Two seconds
         * is what the player already relies on when switching channel.
         */
        private const val LINE_SETTLE_MS = 2_000L
        private const val TELL_EVERY_MS = 5_000L
        private const val NOTIFY_EVERY_MS = 10_000L

        fun record(context: Context, id: String) {
            val intent = Intent(context, RecordingService::class.java)
                .setAction(ACTION_RECORD)
                .putExtra(EXTRA_ID, id)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, RecordingService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
