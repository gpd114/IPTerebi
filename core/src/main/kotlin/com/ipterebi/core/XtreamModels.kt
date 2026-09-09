package com.ipterebi.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

/**
 * "Xtream Codes" is not one implementation. The original panel was abandoned in
 * 2019 and what providers actually run today is a spread of forks and rewrites
 * that agree on the URL shape and on very little else. The same field arrives
 * as `12345` from one panel and `"12345"` from the next; fields documented as
 * present are absent; nulls turn up where a string is expected.
 *
 * So: unknown keys ignored, so a panel with extra fields does not fail;
 * `coerceInputValues` on, so an explicit null falls back to the property's
 * default instead of throwing; and the numeric fields that matter go through
 * the flexible serialisers below, which accept either spelling.
 *
 * The alternative — strict parsing — means the app works against the panel it
 * was written on and fails at the login screen against every other one.
 */
val LenientJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

/** Reads a JSON string or number as a string. `12345` and `"12345"` both win. */
object FlexibleStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val input = decoder as? JsonDecoder ?: return decoder.decodeString()
        return (input.decodeJsonElement() as? JsonPrimitive)?.content ?: ""
    }

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

/**
 * Reads a JSON string or number as an Int. Goes via Double so that `"3.0"`,
 * which at least one panel emits for `num`, does not come back as zero.
 */
object FlexibleIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int {
        val input = decoder as? JsonDecoder ?: return decoder.decodeInt()
        val primitive = input.decodeJsonElement() as? JsonPrimitive ?: return 0
        val text = primitive.content
        return text.toIntOrNull() ?: text.toDoubleOrNull()?.toInt() ?: 0
    }

    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}

@Serializable
data class LiveCategory(
    @SerialName("category_id")
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String = "",

    @SerialName("category_name")
    @Serializable(with = FlexibleStringSerializer::class)
    val name: String = "",
)

@Serializable
data class LiveStream(
    @SerialName("stream_id")
    @Serializable(with = FlexibleIntSerializer::class)
    val streamId: Int = 0,

    @SerialName("name")
    @Serializable(with = FlexibleStringSerializer::class)
    val name: String = "",

    /** Channel logo. Frequently an empty string, and frequently a dead link. */
    @SerialName("stream_icon")
    @Serializable(with = FlexibleStringSerializer::class)
    val icon: String = "",

    @SerialName("category_id")
    @Serializable(with = FlexibleStringSerializer::class)
    val categoryId: String = "",

    /** The provider's own channel number. Not unique, and not always set. */
    @SerialName("num")
    @Serializable(with = FlexibleIntSerializer::class)
    val number: Int = 0,

    /** Key into the XMLTV guide. Empty on a channel the provider has no EPG for. */
    @SerialName("epg_channel_id")
    @Serializable(with = FlexibleStringSerializer::class)
    val epgChannelId: String = "",
)

@Serializable
data class UserInfo(
    /** 1 when the line is real. Everything else, including 0, is a rejection. */
    @Serializable(with = FlexibleIntSerializer::class)
    val auth: Int = 0,

    /** "Active", "Expired", "Banned", "Disabled". Free text, not an enum. */
    @Serializable(with = FlexibleStringSerializer::class)
    val status: String = "",

    @Serializable(with = FlexibleStringSerializer::class)
    val message: String = "",

    /** Unix seconds, as a string. Empty means the line does not expire. */
    @SerialName("exp_date")
    @Serializable(with = FlexibleStringSerializer::class)
    val expiryEpochSeconds: String = "",

    /**
     * How many streams the line may pull at once — usually 1. Exceeding it is
     * the single most common cause of a channel that refuses to start while
     * every other channel is fine, because the phone still holds the previous
     * connection open. Worth showing the user.
     */
    @SerialName("max_connections")
    @Serializable(with = FlexibleStringSerializer::class)
    val maxConnections: String = "",

    @SerialName("active_cons")
    @Serializable(with = FlexibleStringSerializer::class)
    val activeConnections: String = "",

    /** Typically ["m3u8", "ts", "rtmp"]. Aspirational on some panels. */
    @SerialName("allowed_output_formats")
    val allowedOutputFormats: List<String> = emptyList(),
) {
    val isAuthenticated: Boolean get() = auth == 1
    val isActive: Boolean get() = status.equals("Active", ignoreCase = true)
}

@Serializable
data class AuthResponse(
    @SerialName("user_info") val userInfo: UserInfo = UserInfo(),
)

/**
 * Drops channels the app could not play and would crash on trying to list.
 *
 * `stream_id` is optional like everything else here, and [FlexibleIntSerializer]
 * answers 0 for one that is missing or unreadable. Zero is not a channel: the
 * stream URL built from it is `/live/u/p/0.ts`, which 404s against a panel that
 * is working perfectly well. Worse, a screenful of them share an id, and a
 * LazyColumn keyed on that id throws `Key "0" was already used` — the list is
 * gone rather than one row of it.
 *
 * Duplicate ids get the same treatment for the same reason. A panel repeating a
 * channel inside one category is a fork bug, but it is our crash.
 */
fun List<LiveStream>.playableChannels(): List<LiveStream> =
    filter { it.streamId > 0 }.distinctBy { it.streamId }

/**
 * Drops categories that cannot be asked for or listed.
 *
 * A blank `category_id` cannot be passed to `get_live_streams` as a filter, so
 * the category could only ever open empty; and blanks and duplicates collide as
 * LazyRow keys exactly as channel ids do.
 */
fun List<LiveCategory>.usableCategories(): List<LiveCategory> =
    filter { it.id.isNotBlank() }.distinctBy { it.id }
