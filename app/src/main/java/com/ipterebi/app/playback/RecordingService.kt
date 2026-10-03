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
import com.ipterebi.core.StreamReconnect
import com.ipterebi.core.XtreamAccount
import com.ipterebi.core.describeStreamHttpError
import com.ipterebi.core.recordingFileName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
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

        val written = runCatching { file.outputStream().buffered() }.getOrElse { e ->
            return Outcome.Failed("The file could not be opened: ${e.message}", 0, file.absolutePath)
        }

        // One file, however many connections it takes to fill it.
        //
        // A panel closing the connection is not the end of the programme. That
        // is the oldest lesson in this app — a channel restarting hangs up
        // cleanly and ExoPlayer calls it ENDED, which for live is never true —
        // and the recorder used to fall for it exactly as the player once did:
        // the read returned -1, the recording was marked Done, and a row
        // claimed success over a file that stopped twenty minutes in.
        //
        // So the clock decides when a recording is over, never the socket. The
        // same back-off the player uses (1, 2, 4, 8, 15 s, then give up) asks
        // the panel again, and the bytes keep going into the same file. The
        // delays matter as much as the retrying: a line that allows one stream
        // counts the connection it just lost for a few seconds, so an instant
        // reconnect is refused.
        val reconnect = StreamReconnect()
        var total = 0L
        var attempt = 0

        return written.use { out ->
            while (true) {
                currentCoroutineContext().ensureActive()
                attempt++

                val asked = runCatching { container.http.newCall(request).execute() }
                val response = asked.getOrNull()
                val body = response?.takeIf { it.isSuccessful }?.body

                if (body == null) {
                    val code = response?.code
                    response?.close()
                    // Refused before a single byte: that is the panel saying no
                    // — the wrong format, a dead channel, the connection limit
                    // — and the reason is worth showing at once. Refused after
                    // bytes have arrived is just a failed attempt at getting
                    // back in, which is what the back-off is for.
                    if (total == 0L && attempt == 1) {
                        file.delete()
                        return@use Outcome.Failed(
                            code?.let(::describeStreamHttpError)
                                ?: "The panel could not be reached: " +
                                asked.exceptionOrNull()?.message.orEmpty(),
                        )
                    }
                    val wait = reconnect.onDropped(System.currentTimeMillis())
                        ?: return@use giveUp(recording, total, file.absolutePath)
                    if (over(recording)) return@use done(total, file.absolutePath)
                    kotlinx.coroutines.delay(wait)
                    continue
                }

                val chunk = response.use {
                    stream(container, account, recording, body.byteStream(), out, file.absolutePath, total, reconnect)
                }
                total = chunk.total

                when (chunk) {
                    is Chunk.Finished -> return@use Outcome.Done(total, file.absolutePath)
                    is Chunk.Stopped -> return@use Outcome.Failed(chunk.why, total, file.absolutePath)
                    is Chunk.Dropped -> {
                        val wait = reconnect.onDropped(System.currentTimeMillis())
                            ?: return@use giveUp(recording, total, file.absolutePath)
                        // The programme can end while a back-off is being
                        // waited out, and then there is nothing left to ask
                        // for: one more connection would be opened only to
                        // be closed by the clock.
                        if (over(recording)) return@use done(total, file.absolutePath)
                        if (BuildConfig.DEBUG) {
                            Log.d(
                                TAG_PLAY,
                                "recording ${recording.id} dropped at ${total / (1024 * 1024)} MB; " +
                                    "asking again in $wait ms",
                            )
                        }
                        kotlinx.coroutines.delay(wait)
                    }
                }
            }
            @Suppress("UNREACHABLE_CODE")
            done(total, file.absolutePath)
        }
    }

    /** Whether the clock has passed the end of what was asked for. */
    private fun over(recording: Recording): Boolean =
        System.currentTimeMillis() / 1000 >= recording.stopSeconds

    /**
     * The end of the programme, reached.
     *
     * Nothing at all is a failure rather than a success, however the loop
     * got here: a row saying Done over an empty file is the thing this whole
     * change exists to stop.
     */
    private fun done(total: Long, document: String): Outcome =
        if (total > 0) Outcome.Done(total, document)
        else Outcome.Failed("Nothing arrived from the panel.", 0, document)

    /**
     * Out of attempts, with the programme still running.
     *
     * Kept rather than deleted, and said plainly. A short recording of the
     * right programme is worth something; a row that claims it worked is
     * worth less than nothing.
     */
    private fun giveUp(recording: Recording, total: Long, document: String): Outcome {
        val left = (recording.stopSeconds - System.currentTimeMillis() / 1000) / 60
        return Outcome.Failed(
            "The stream kept dropping and would not come back, with about $left minutes " +
                "still to record. What was recorded up to then is kept.",
            total,
            document,
        )
    }

    /** Why one connection's worth of copying stopped. */
    private sealed interface Chunk {
        val total: Long

        /** The recording's end time arrived, which is the only real finish. */
        data class Finished(override val total: Long) : Chunk

        /** The stream ended or failed while the clock says there is more. */
        data class Dropped(override val total: Long) : Chunk

        /** Something retrying cannot fix: a full disk, a file at its limit. */
        data class Stopped(override val total: Long, val why: String) : Chunk
    }

    /**
     * The loop. Reads until the recording's end, the volume fills, the file
     * grows past what FAT32 holds, or the connection goes.
     *
     * The store is updated every few seconds rather than every buffer: a row
     * that shows the size growing is worth having, and a DataStore write per
     * 64 KB is not.
     */
    private suspend fun stream(
        container: AppContainer,
        account: XtreamAccount,
        recording: Recording,
        input: java.io.InputStream,
        out: OutputStream,
        document: String,
        alreadyWritten: Long,
        reconnect: StreamReconnect,
    ): Chunk = withContext(Dispatchers.IO) {
        val buffer = ByteArray(64 * 1024)
        var total = alreadyWritten
        var lastTold = System.currentTimeMillis()
        var lastNotified = 0L

        while (true) {
            ensureActive()
            val now = System.currentTimeMillis() / 1000
            if (now >= recording.stopSeconds) return@withContext Chunk.Finished(total)

            val read = runCatching { input.read(buffer) }.getOrElse {
                return@withContext Chunk.Dropped(total)
            }
            // The end of the body, which for live television means the panel
            // hung up rather than that the programme is over.
            if (read < 0) return@withContext Chunk.Dropped(total)

            runCatching { out.write(buffer, 0, read) }.getOrElse { e ->
                return@withContext Chunk.Stopped(total, "Writing failed: ${e.message}")
            }
            total += read
            // Bytes arriving is this stream playing, which is what lets the
            // back-off forgive its attempts once it has run steadily. Without
            // it, a recording that drops once an hour would run out of tries.
            reconnect.onPlaying(System.currentTimeMillis())

            if (total >= FAT32_MAX_BYTES) {
                return@withContext Chunk.Stopped(
                    total,
                    "The recording reached four gigabytes, which is as much as one file can " +
                        "hold on this stick. What was recorded up to then is kept.",
                )
            }

            val millis = System.currentTimeMillis()
            if (millis - lastTold > TELL_EVERY_MS) {
                lastTold = millis
                if (freeBytes(document) < SPACE_HEADROOM_BYTES) {
                    return@withContext Chunk.Stopped(
                        total,
                        "The disk is full. What was recorded up to then is kept.",
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
        @Suppress("UNREACHABLE_CODE")
        Chunk.Finished(total)
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
