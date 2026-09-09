package com.ipterebi.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One film.
 *
 * The same `player_api.php` with `get_vod_streams` instead of
 * `get_live_streams`, and mostly the same fields — but two differences matter
 * enough that a film cannot be treated as a channel that happens to end.
 *
 * A film is a *file*, so its URL ends in that file's own extension rather than
 * in a container this app chose. `mp4`, `mkv` and `avi` are all common on the
 * same line, and asking for `.ts` because that is what live television uses
 * gets a 404 from a panel holding the film quite happily.
 *
 * A film also has a duration, is seekable, and ends — none of which is true of
 * a channel, and all of which the player has to be told about rather than
 * inferring, because the reasoning that hides the transport controls for live
 * would hide them here too.
 */
@Serializable
data class VodStream(
    @SerialName("stream_id")
    @Serializable(with = FlexibleIntSerializer::class)
    val streamId: Int = 0,

    @SerialName("name")
    @Serializable(with = FlexibleStringSerializer::class)
    val name: String = "",

    @SerialName("stream_icon")
    @Serializable(with = FlexibleStringSerializer::class)
    val icon: String = "",

    @SerialName("category_id")
    @Serializable(with = FlexibleStringSerializer::class)
    val categoryId: String = "",

    /**
     * The file's extension, without the dot: `mp4`, `mkv`, `avi`.
     *
     * Blank happens, and [playbackExtension] is what to use rather than this —
     * a film with no extension is still worth offering, and `mp4` is right far
     * more often than not.
     */
    @SerialName("container_extension")
    @Serializable(with = FlexibleStringSerializer::class)
    val containerExtension: String = "",

    /** Out of ten, as a string, and frequently blank or "0". */
    @Serializable(with = FlexibleStringSerializer::class)
    val rating: String = "",

    /** When the provider added it, `"Y-m-d H:i:s"` or a unix string. Display only. */
    @Serializable(with = FlexibleStringSerializer::class)
    val added: String = "",

    @SerialName("num")
    @Serializable(with = FlexibleIntSerializer::class)
    val number: Int = 0,
) {
    /**
     * The extension to actually ask for.
     *
     * Falls back to `mp4`, which is what the overwhelming majority of VOD on
     * these panels is. A film whose `container_extension` did not survive
     * parsing is far better offered with a guess that usually works than hidden
     * from the list entirely.
     */
    val playbackExtension: String
        get() = containerExtension.trim().removePrefix(".").ifBlank { DEFAULT_VOD_EXTENSION }

    val isPlayable: Boolean get() = streamId > 0

    /** Out of ten as a number, or null when the panel said nothing useful. */
    val ratingOutOfTen: Double?
        get() = rating.trim().toDoubleOrNull()?.takeIf { it > 0.0 }

    companion object {
        const val DEFAULT_VOD_EXTENSION: String = "mp4"
    }
}

/**
 * Drops films that could not be played and would collide as list keys.
 *
 * Exactly the reasoning in [playableChannels], for exactly the same reasons —
 * `stream_id` is optional here too, and the flexible parsing answers 0 for one
 * it cannot read.
 */
fun List<VodStream>.playableFilms(): List<VodStream> =
    filter { it.isPlayable }.distinctBy { it.streamId }
