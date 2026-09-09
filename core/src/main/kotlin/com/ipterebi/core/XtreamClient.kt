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
        return info
    }

    suspend fun liveCategories(account: XtreamAccount): List<LiveCategory> = decode(
        body = get(account, action = "get_live_categories"),
        deserializer = ListSerializer(LiveCategory.serializer()),
        what = "the category list",
    )

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
    )

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
            throw XtreamException("Could not make sense of $what from this panel.", e)
        }
    }

    private fun requireBase(account: XtreamAccount): HttpUrl =
        account.base.toHttpUrlOrNull()
            ?: throw XtreamException("\"${account.base}\" is not a usable server address.")
}

/** The same URL with the credentials replaced, safe to write to a log. */
fun HttpUrl.withoutCredentials(): String = newBuilder()
    .setQueryParameter("username", "***")
    .setQueryParameter("password", "***")
    .build()
    .toString()
