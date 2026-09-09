package com.ipterebi.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

fun defaultXtreamHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    // Panels on cheap hosting take their time assembling a full channel list;
    // ten seconds is not enough and produces a timeout that looks like a dead
    // server.
    .readTimeout(30, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .build()

/**
 * Everything this app asks an Xtream panel for. Live television only: VOD and
 * series use the same `player_api.php` with different actions and are not
 * wired up yet.
 *
 * [log] receives one line per request with the credentials stripped. Wire it to
 * something that only fires in debug builds — see IPTerebiApi in :app.
 */
class XtreamClient(
    private val http: OkHttpClient = defaultXtreamHttpClient(),
    private val json: Json = LenientJson,
    private val log: (String) -> Unit = {},
) {

    /**
     * Confirms the line exists and is usable, and returns what the panel says
     * about it. Note that a rejected login is HTTP 200 with `auth: 0` in the
     * body — never a 401 — so the status code alone tells you nothing.
     */
    suspend fun authenticate(account: XtreamAccount): UserInfo {
        val info = decode(
            body = get(account),
            deserializer = AuthResponse.serializer(),
            what = "the sign-in response",
        ).userInfo

        if (!info.isAuthenticated) {
            throw XtreamException(
                info.message.takeIf { it.isNotBlank() }
                    ?: "The panel rejected that username and password."
            )
        }
        if (!info.isActive) {
            throw XtreamException(
                "The line is not active. The panel reports its status as " +
                    "\"${info.status}\"."
            )
        }
        log(
            "  line ok: status=${info.status}, connections=${info.activeConnections}/" +
                "${info.maxConnections}, formats=${info.allowedOutputFormats}"
        )
        return info
    }

    suspend fun liveCategories(account: XtreamAccount): List<XtreamCategory> = decode(
        body = get(account, action = "get_live_categories"),
        deserializer = ListSerializer(XtreamCategory.serializer()),
        what = "the category list",
    ).usableCategories().also { log("  parsed ${it.size} usable categories") }

    /**
     * Channels, optionally in one category. Asking for everything at once is a
     * single request but can be several megabytes and tens of thousands of
     * entries on a big line, so the UI loads one category at a time.
     */
    suspend fun liveStreams(
        account: XtreamAccount,
        categoryId: String? = null,
    ): List<LiveStream> = decode(
        body = get(
            account = account,
            action = "get_live_streams",
            params = categoryId?.let { mapOf("category_id" to it) }.orEmpty(),
        ),
        deserializer = ListSerializer(LiveStream.serializer()),
        what = "the channel list",
    ).let { parsed ->
        val channels = parsed.playableChannels()
        log(
            "  parsed ${parsed.size} channels in category ${categoryId ?: "(all)"}" +
                (if (channels.size != parsed.size) {
                    ", ${parsed.size - channels.size} dropped as unplayable"
                } else "") +
                (channels.firstOrNull()?.let { ", first: ${it.name} (id ${it.streamId})" } ?: "")
        )
        channels
    }

    /**
     * The next few programmes on one channel.
     *
     * Per channel, not per list: there is no call that returns the guide for
     * everything at once short of `xmltv.php`, which hands back the entire
     * schedule for every channel on the line as XML and is measured in tens of
     * megabytes. So this is asked once when a channel is opened, and the channel
     * list stays guide-less rather than firing several hundred requests to fill
     * in rows nobody is looking at.
     *
     * An absent guide is normal and not an error. Plenty of providers carry no
     * EPG at all, plenty of channels are missing from one that exists, and some
     * forks answer the literal `false` — all of which arrive here as an empty
     * list, because a screen with no programme on it is a better answer than an
     * error about a feature the user never asked for.
     */
    suspend fun shortEpg(
        account: XtreamAccount,
        streamId: Int,
        limit: Int = 4,
    ): List<EpgListing> {
        val body = try {
            get(
                account = account,
                action = "get_short_epg",
                params = mapOf("stream_id" to "$streamId", "limit" to "$limit"),
            )
        } catch (e: XtreamException) {
            log("  no guide for stream $streamId: ${e.message}")
            return emptyList()
        }

        // Same reasoning as above: every one of these is "this panel has no
        // guide", which is a fact about the provider rather than a fault.
        if (body.isBlank() || body.trim() == "false" || body.trimStart().startsWith("<")) {
            log("  no guide for stream $streamId: panel sent nothing usable")
            return emptyList()
        }

        return try {
            json.decodeFromString(ShortEpgResponse.serializer(), body).listings
                .also { log("  parsed ${it.size} programmes for stream $streamId") }
        } catch (e: SerializationException) {
            log("  unreadable guide for stream $streamId, body starts: ${body.take(120)}")
            emptyList()
        }
    }

    /**
     * Where the video actually is. The credentials sit in the path, not in a
     * header, so this string is as sensitive as the password itself — never log
     * it, and be careful about putting it anywhere a crash reporter can see.
     */
    fun liveStreamUrl(account: XtreamAccount, streamId: Int): String =
        requireBase(account).newBuilder()
            .addPathSegment("live")
            .addPathSegment(account.username)
            .addPathSegment(account.password)
            .addPathSegment("$streamId.${account.format.extension}")
            .build()
            .toString()

    private suspend fun get(
        account: XtreamAccount,
        action: String? = null,
        params: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        val base = requireBase(account)
        val url = base.newBuilder()
            .addPathSegment("player_api.php")
            .addQueryParameter("username", account.username)
            .addQueryParameter("password", account.password)
            .apply {
                action?.let { addQueryParameter("action", it) }
                params.forEach { (key, value) -> addQueryParameter(key, value) }
            }
            .build()

        log("GET ${url.withoutCredentials()}")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", account.userAgent)
            .build()

        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw XtreamException(
                "Could not reach ${base.host}: ${e.message ?: "no response"}.",
                e,
            )
        }

        response.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw XtreamException(describeApiHttpError(it.code, base.host))
            log("  ${it.code}, ${body.length} chars")
            body
        }
    }

    private fun <T> decode(body: String, deserializer: DeserializationStrategy<T>, what: String): T {
        // A panel behind Cloudflare, or one that has been suspended, answers
        // with a perfectly good HTTP 200 carrying an HTML page. Saying so beats
        // "Unexpected JSON token at offset 0".
        if (body.trimStart().startsWith("<")) {
            throw XtreamException(
                "The server sent a web page instead of $what. The panel is " +
                    "probably down, suspended, or behind a challenge page."
            )
        }
        if (body.isBlank()) {
            throw XtreamException("The server sent an empty reply instead of $what.")
        }
        return try {
            json.decodeFromString(deserializer, body)
        } catch (e: SerializationException) {
            // `false` is a real answer from real panels — it is how some forks
            // say "no" to an action they do not implement.
            if (body.trim() == "false") {
                throw XtreamException("The panel does not support $what.", e)
            }
            log("  unparseable $what, body starts: ${body.take(200).withoutCredentialValues()}")
            throw XtreamException("Could not make sense of $what from this panel.", e)
        }
    }

    private fun requireBase(account: XtreamAccount): HttpUrl =
        account.base.toHttpUrlOrNull()
            ?: throw XtreamException("\"${account.base}\" is not a usable server address.")
}

/**
 * Strips credential values out of a JSON body so it can be logged.
 *
 * This is not optional for the sign-in response: Xtream panels echo the
 * username *and the password in clear* back inside `user_info`, so logging a
 * raw response body writes the user's password to logcat, where any app holding
 * READ_LOGS on an older device can read it.
 *
 * The value is matched quoted *or* bare. Lines on these panels are very often
 * all digits, and this file's own rule is that every type here is negotiable —
 * a fork that emits `"password":12345` unquoted would walk a scrubber that
 * insisted on quotes, and the failure would be silent and in clear.
 */
fun String.withoutCredentialValues(): String = replace(
    Regex("\"(password|username)\"\\s*:\\s*(\"[^\"]*\"|[^,}\\]\\s]+)"),
    "\"$1\":\"***\"",
)

/** The same URL with the credentials replaced, safe to write to a log. */
fun HttpUrl.withoutCredentials(): String = newBuilder()
    .setQueryParameter("username", "***")
    .setQueryParameter("password", "***")
    .build()
    .toString()
