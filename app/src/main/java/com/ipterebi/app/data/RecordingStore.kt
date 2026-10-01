package com.ipterebi.app.data

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ipterebi.app.BuildConfig
import com.ipterebi.app.TAG_API
import com.ipterebi.core.LenientJson
import com.ipterebi.core.Recording
import com.ipterebi.core.RecordingState
import com.ipterebi.core.Scheduling
import com.ipterebi.core.XtreamAccount
import com.ipterebi.core.lineKey
import com.ipterebi.core.schedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer

private val Context.recordingStore by preferencesDataStore(name = "recordings")

/**
 * What the viewer asked to keep, and where it is written.
 *
 * Two different lifetimes live in one store, deliberately:
 *
 * - **The recordings are per line**, like favourites and lists and for the
 *   same reason — a stream id means nothing off its own panel, so a recording
 *   of channel 4271 carried to another provider would point at something else.
 * - **The folder is per device.** A USB stick belongs to the box, not to the
 *   line signed in on it, and signing out should not make the viewer find it
 *   again. It is the path of a directory on one of the volumes the box has,
 *   because a television has no file picker to browse one with — see
 *   [recordingVolumes].
 *
 * Every rule about what may be scheduled is `Recordings` in core, where it is
 * tested. This file reads and writes and nothing else.
 */
class RecordingStore(private val context: Context) {

    private fun key(account: XtreamAccount) = stringPreferencesKey("recordings:${account.lineKey}")

    private val folderKey = stringPreferencesKey("folder")

    fun recordings(account: XtreamAccount): Flow<List<Recording>> =
        context.recordingStore.data.map { it[key(account)].decode() }

    suspend fun current(account: XtreamAccount): List<Recording> = recordings(account).first()

    /**
     * Where recordings are written, or empty when no stick has been chosen.
     *
     * Empty is the normal state on a box nobody has set up yet, not an error:
     * the screens offer to pick a folder rather than reporting a fault.
     */
    val folder: Flow<String> = context.recordingStore.data.map { it[folderKey].orEmpty() }

    suspend fun folderNow(): String = folder.first()

    suspend fun setFolder(path: String) {
        context.recordingStore.edit { it[folderKey] = path }
    }

    suspend fun forgetFolder() {
        context.recordingStore.edit { it.remove(folderKey) }
    }

    /**
     * Books [candidate], merges it into one already there, or refuses it.
     *
     * The decision is core's; this only writes the result when there is one.
     * The outcome comes back so the screen can say which existing recording
     * has the line, which is the only useful thing to tell someone whose
     * recording was refused.
     */
    suspend fun book(account: XtreamAccount, candidate: Recording): Scheduling {
        lateinit var outcome: Scheduling
        context.recordingStore.edit { prefs ->
            val k = key(account)
            val result = schedule(prefs[k].decode(), candidate)
            outcome = result
            when (result) {
                is Scheduling.Booked -> prefs[k] = result.all.encode()
                is Scheduling.Merged -> prefs[k] = result.all.encode()
                is Scheduling.Clash -> Unit
            }
        }
        return outcome
    }

    /** Replaces one by id — how a recording moves between states as it runs. */
    suspend fun put(account: XtreamAccount, recording: Recording) {
        context.recordingStore.edit { prefs ->
            val k = key(account)
            val rest = prefs[k].decode().filterNot { it.id == recording.id }
            prefs[k] = (rest + recording).sortedBy { it.startSeconds }.encode()
        }
    }

    suspend fun remove(account: XtreamAccount, id: String) {
        context.recordingStore.edit { prefs ->
            val k = key(account)
            prefs[k] = prefs[k].decode().filterNot { it.id == id }.encode()
        }
    }

    /**
     * Marks anything left mid-recording as failed.
     *
     * Called when the app starts: a recording in the RECORDING state at that
     * moment is one the process did not live through — the box was unplugged,
     * or Android killed it — and leaving it looking live would have the
     * scheduler think the line is busy forever.
     */
    suspend fun failInterrupted(account: XtreamAccount, reason: String) {
        context.recordingStore.edit { prefs ->
            val k = key(account)
            val all = prefs[k].decode()
            if (all.none { it.state == RecordingState.RECORDING }) return@edit
            prefs[k] = all.map {
                if (it.state == RecordingState.RECORDING) {
                    it.copy(state = RecordingState.FAILED, failure = reason)
                } else {
                    it
                }
            }.encode()
        }
    }

    /** Forgets every recording for one line, when that line is signed out of. */
    suspend fun clear(account: XtreamAccount) {
        context.recordingStore.edit { it.remove(key(account)) }
    }
}

private val serializer = ListSerializer(Recording.serializer())

private fun List<Recording>.encode(): String = LenientJson.encodeToString(serializer, this)

/**
 * Unreadable is empty, as everywhere else — but a dropped recording list is a
 * programme nobody gets back, so it says so in the log rather than passing
 * over it. Only an upgrade that changed the shape can cause it.
 */
private fun String?.decode(): List<Recording> {
    if (this.isNullOrBlank()) return emptyList()
    return try {
        LenientJson.decodeFromString(serializer, this)
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) Log.w(TAG_API, "stored recordings were unreadable, dropping them", e)
        emptyList()
    }
}

/**
 * A place recordings can be written, as a screen offers it.
 *
 * Not a folder the viewer browsed to: **Android TV has no file picker.** The
 * box resolves `OPEN_DOCUMENT_TREE` to nothing at all, so launching it throws,
 * and the Google TV emulator resolves it to a framework stub that does
 * nothing. Measured on both before this was written.
 *
 * What does work everywhere, with no permission and no picker, is the app's
 * own directory on each mounted volume — `getExternalFilesDirs` returns one
 * per volume, internal first and removable after. So the viewer picks a
 * volume rather than a folder, which is the only question they actually have:
 * the stick, or the box.
 */
data class RecordingVolume(
    /** The directory to write into. Its own path is the stored value. */
    val path: String,
    val label: String,
    val freeBytes: Long,
    val removable: Boolean,
)

/**
 * Everywhere a recording could go, roomiest sort of first.
 *
 * A volume that cannot be written to is left out rather than offered and
 * refused later — a stick pulled out is the common case, and the time to
 * find out is while choosing, not when a programme starts.
 */
fun recordingVolumes(context: Context): List<RecordingVolume> =
    context.getExternalFilesDirs(null)
        .filterNotNull()
        .filter { runCatching { it.mkdirs(); it.canWrite() }.getOrDefault(false) }
        .map { dir ->
            val removable = runCatching { Environment.isExternalStorageRemovable(dir) }
                .getOrDefault(false)
            RecordingVolume(
                path = dir.absolutePath,
                label = if (removable) "USB or card" else "Box storage",
                freeBytes = runCatching { StatFs(dir.path).availableBytes }.getOrDefault(0L),
                removable = removable,
            )
        }
