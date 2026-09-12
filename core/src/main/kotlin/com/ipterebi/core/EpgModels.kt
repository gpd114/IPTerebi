package com.ipterebi.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64

/**
 * Reads a JSON string or number as a Long.
 *
 * Separate from [FlexibleIntSerializer] because these are unix seconds. They fit
 * in an Int today and stop fitting in January 2038, and an EPG is the one place
 * in this app that is routinely asked about times in the future.
 */
object FlexibleLongSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleLong", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Long {
        val input = decoder as? JsonDecoder ?: return decoder.decodeLong()
        val primitive = input.decodeJsonElement() as? JsonPrimitive ?: return 0L
        val text = primitive.content
        return text.toLongOrNull() ?: text.toDoubleOrNull()?.toLong() ?: 0L
    }

    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
}

/**
 * One programme.
 *
 * Two things about this shape are worth knowing before trusting any of it.
 *
 * `title` and `description` arrive **base64 encoded** — that is the documented
 * behaviour, not a quirk of one fork — so the raw fields are unreadable and
 * [titleText] and [descriptionText] are what you want. Some forks send plain
 * text anyway, which [decodeEpgText] copes with.
 *
 * `start` and `end` are `"Y-m-d H:i:s"` strings **in no stated timezone**. It is
 * the panel's local time, which is not the viewer's and is not documented
 * anywhere, so rendering them directly puts a programme an arbitrary number of
 * hours out. They are kept for debugging and nothing reads them: every time
 * question here is answered from [startTimestamp] and [stopTimestamp], which are
 * unix seconds and therefore unambiguous.
 */
@Serializable
data class EpgListing(
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String = "",

    @SerialName("epg_id")
    @Serializable(with = FlexibleStringSerializer::class)
    val epgId: String = "",

    /** Base64. Read [titleText] instead. */
    @Serializable(with = FlexibleStringSerializer::class)
    val title: String = "",

    /** Base64. Read [descriptionText] instead. */
    @Serializable(with = FlexibleStringSerializer::class)
    val description: String = "",

    /** Panel-local wall clock, timezone unstated. Kept for logs; do not render. */
    @Serializable(with = FlexibleStringSerializer::class)
    val start: String = "",

    /** Panel-local wall clock, timezone unstated. Kept for logs; do not render. */
    @Serializable(with = FlexibleStringSerializer::class)
    val end: String = "",

    @SerialName("start_timestamp")
    @Serializable(with = FlexibleLongSerializer::class)
    val startTimestamp: Long = 0,

    @SerialName("stop_timestamp")
    @Serializable(with = FlexibleLongSerializer::class)
    val stopTimestamp: Long = 0,

    /** Only sent by `get_simple_data_table`, and only trusted when we cannot work it out. */
    @SerialName("now_playing")
    @Serializable(with = FlexibleIntSerializer::class)
    val nowPlaying: Int = 0,

    @SerialName("has_archive")
    @Serializable(with = FlexibleIntSerializer::class)
    val hasArchive: Int = 0,

    /**
     * True for a programme from the full guide (`xmltv.php`), whose text is
     * plain: decoding it as base64 would turn a title that happens to be valid
     * base64 into noise. Never sent by a panel.
     */
    @Transient
    val plainText: Boolean = false,
) {
    val titleText: String get() = if (plainText) title else title.decodeEpgText()

    val descriptionText: String get() = if (plainText) description else description.decodeEpgText()

    /**
     * Whether both ends of the programme are known and the right way round. A
     * panel with a broken guide sends zeroes, and a zero timestamp is 1970
     * rather than "unknown" to everything downstream unless it is checked here.
     */
    val hasKnownTimes: Boolean
        get() = startTimestamp > 0 && stopTimestamp > startTimestamp

    fun isOnAt(instant: Instant): Boolean {
        if (!hasKnownTimes) return false
        val seconds = instant.epochSecond
        return seconds >= startTimestamp && seconds < stopTimestamp
    }

    /** 0f at the opening titles, 1f at the credits. Null when the times are not known. */
    fun progressAt(instant: Instant): Float? {
        if (!hasKnownTimes) return null
        val span = (stopTimestamp - startTimestamp).toDouble()
        val through = (instant.epochSecond - startTimestamp).toDouble()
        return (through / span).coerceIn(0.0, 1.0).toFloat()
    }

    /** e.g. "20:00 – 21:00", in the viewer's timezone. Blank when the times are not known. */
    fun timeLabel(zone: ZoneId = ZoneId.systemDefault()): String {
        if (!hasKnownTimes) return ""
        val format = DateTimeFormatter.ofPattern("HH:mm")
        val from = Instant.ofEpochSecond(startTimestamp).atZone(zone).format(format)
        val to = Instant.ofEpochSecond(stopTimestamp).atZone(zone).format(format)
        return "$from – $to"
    }
}

@Serializable
data class ShortEpgResponse(
    @SerialName("epg_listings") val listings: List<EpgListing> = emptyList(),
)

/**
 * Decodes a base64 EPG field, and hands back what it was given when that is not
 * what it is.
 *
 * The documentation says these fields are base64 and most panels oblige, but
 * forks that send plain text exist, and a title shown as `TW9ybmluZyBOZXdz` is
 * worse than no guide at all.
 *
 * Detecting which is not as simple as "did it decode": a short plain title like
 * `News` is itself valid base64 and decodes happily to three bytes of noise. So
 * the decoded bytes have to be *read* — if they are not valid UTF-8, or carry
 * control characters no title contains, the input was never base64 and is
 * returned untouched. A plain title that survives both checks is possible in
 * principle and vanishingly unlikely in practice.
 */
fun String.decodeEpgText(): String {
    val trimmed = trim()
    if (trimmed.isEmpty()) return ""
    val decoded = try {
        // MIME rather than the strict decoder: panels wrap long descriptions at
        // 76 characters, and the strict decoder rejects the line breaks.
        Base64.getMimeDecoder().decode(trimmed)
    } catch (e: IllegalArgumentException) {
        return trimmed
    }
    return decoded.asReadableTextOrNull() ?: trimmed
}

private fun ByteArray.asReadableTextOrNull(): String? {
    if (isEmpty()) return null
    val text = toString(Charsets.UTF_8)
    // U+FFFD is what the decoder substitutes for bytes that were not UTF-8 —
    // proof the input was never text, so it was never base64 either.
    if (text.contains('�')) return null
    if (text.any { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' }) return null
    if (text.isBlank()) return null
    return text
}

/**
 * The programme on at [instant], and the one after it.
 *
 * Worked out from the timestamps rather than read from `now_playing`, which only
 * `get_simple_data_table` sends and which is computed in the panel's timezone
 * against the panel's clock. Ours is the one the viewer is actually in.
 *
 * Listings are sorted here because arrival order is not promised, and a guide
 * that renders "next" as something that already finished is worse than blank.
 */
data class NowAndNext(val now: EpgListing?, val next: EpgListing?)

fun List<EpgListing>.nowAndNext(instant: Instant): NowAndNext {
    val timed = filter { it.hasKnownTimes }.sortedBy { it.startTimestamp }
    val now = timed.firstOrNull { it.isOnAt(instant) }
    val next = timed.firstOrNull { it.startTimestamp > instant.epochSecond }
    return NowAndNext(now = now, next = next)
}
