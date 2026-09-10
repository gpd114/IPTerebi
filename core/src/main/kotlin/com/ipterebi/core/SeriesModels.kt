package com.ipterebi.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale

/**
 * One programme with episodes, as `get_series` lists it.
 *
 * Note the id is `series_id`, not `stream_id`: a series is not itself
 * playable. It is a container, and what plays is an episode inside it, with an
 * id of its own.
 */
@Serializable
data class Series(
    @SerialName("series_id")
    @Serializable(with = FlexibleIntSerializer::class)
    val seriesId: Int = 0,

    @Serializable(with = FlexibleStringSerializer::class)
    val name: String = "",

    /** Poster art. Provider-hosted, frequently dead, never load-bearing. */
    @Serializable(with = FlexibleStringSerializer::class)
    val cover: String = "",

    @Serializable(with = FlexibleStringSerializer::class)
    val plot: String = "",

    @Serializable(with = FlexibleStringSerializer::class)
    val genre: String = "",

    /** camelCase, unlike every other field. That is what the panels send. */
    @SerialName("releaseDate")
    @Serializable(with = FlexibleStringSerializer::class)
    val releaseDate: String = "",

    /** Out of ten, as a string or a number, and frequently blank or zero. */
    @Serializable(with = FlexibleStringSerializer::class)
    val rating: String = "",

    @SerialName("category_id")
    @Serializable(with = FlexibleStringSerializer::class)
    val categoryId: String = "",

    @SerialName("num")
    @Serializable(with = FlexibleIntSerializer::class)
    val number: Int = 0,
) {
    val isListable: Boolean get() = seriesId > 0

    val ratingOutOfTen: Double?
        get() = rating.trim().toDoubleOrNull()?.takeIf { it > 0.0 }
}

/** Same reasoning as [playableChannels]: ids are keys, and a repeated key is a crash. */
fun List<Series>.listableSeries(): List<Series> =
    filter { it.isListable }.distinctBy { it.seriesId }

/**
 * One episode.
 *
 * [id] is a **string** and stays one. It is the id in the playback URL, it is
 * documented and sent as a string, and there is no reason to believe every
 * panel keeps it numeric — so nothing here parses it, and an id that happens to
 * be non-numeric still plays.
 */
@Serializable
data class Episode(
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String = "",

    @SerialName("episode_num")
    @Serializable(with = FlexibleIntSerializer::class)
    val episodeNumber: Int = 0,

    @Serializable(with = FlexibleStringSerializer::class)
    val title: String = "",

    @SerialName("container_extension")
    @Serializable(with = FlexibleStringSerializer::class)
    val containerExtension: String = "",

    /** The season the panel says this belongs to. The grouping key wins if they disagree. */
    @Serializable(with = FlexibleIntSerializer::class)
    val season: Int = 0,
) {
    /** Same fallback and the same reasoning as [VodStream.playbackExtension]. */
    val playbackExtension: String
        get() = containerExtension.trim().removePrefix(".").ifBlank { VodStream.DEFAULT_VOD_EXTENSION }

    val isPlayable: Boolean get() = id.isNotBlank()
}

/**
 * The details that live inside an episode's `info` object, pulled out
 * separately because that object is the one most often malformed — see
 * [parseSeriesDetail].
 */
@Serializable
data class EpisodeDetails(
    @SerialName("movie_image")
    @Serializable(with = FlexibleStringSerializer::class)
    val image: String = "",

    @Serializable(with = FlexibleStringSerializer::class)
    val plot: String = "",

    @SerialName("duration_secs")
    @Serializable(with = FlexibleIntSerializer::class)
    val durationSeconds: Int = 0,
)

/** An episode as the app shows it: the panel's record plus where it sits. */
data class EpisodeEntry(
    val episode: Episode,
    val details: EpisodeDetails,
    val seasonNumber: Int,
) {
    /** e.g. "S02E05". Blank when the panel gave no episode number. */
    val code: String
        get() = if (episode.episodeNumber > 0) {
            String.format(Locale.ROOT, "S%02dE%02d", seasonNumber, episode.episodeNumber)
        } else ""

    /** "42 min", or blank when the panel said nothing. */
    val durationLabel: String
        get() = details.durationSeconds.takeIf { it > 0 }?.let { "${(it + 30) / 60} min" }.orEmpty()
}

data class SeasonEntry(
    val number: Int,
    val name: String,
    val episodes: List<EpisodeEntry>,
)

/** Everything `get_series_info` said, reshaped into something that can be drawn. */
data class SeriesDetail(
    val name: String,
    val plot: String,
    val cover: String,
    val seasons: List<SeasonEntry>,
) {
    val episodeCount: Int get() = seasons.sumOf { it.episodes.size }
}

/**
 * Reads a `get_series_info` response.
 *
 * Done by hand from the JSON tree rather than by annotations, because the
 * quirks in this response are structural rather than about types:
 *
 * - `episodes` is documented as an object mapping a season number to that
 *   season's episodes: `{"1": [...], "2": [...]}`. Some forks send an array of
 *   arrays instead, one per season in order, and some send one flat array with
 *   each episode's own `season` field as the only grouping.
 * - An empty object arrives as `[]`. That is how PHP serialises an empty
 *   associative array, and most panels are PHP, so `episodes: []` and
 *   `info: []` are what "nothing here" looks like — a strict decoder expecting
 *   an object throws on both.
 * - `seasons` is frequently empty while `episodes` is full. It is used for
 *   names when present and never relied on for which seasons exist.
 *
 * A single unreadable episode is skipped rather than losing the season, and an
 * unreadable season is skipped rather than losing the series. A programme with
 * one malformed episode is still worth watching.
 */
fun parseSeriesDetail(json: Json, root: JsonElement, fallbackName: String = ""): SeriesDetail {
    val obj = root as? JsonObject ?: return SeriesDetail(fallbackName, "", "", emptyList())

    val info = (obj["info"] as? JsonObject)
        ?.let { runCatching { json.decodeFromJsonElement(Series.serializer(), it) }.getOrNull() }

    val seasonNames = seasonNames(obj["seasons"])

    val bySeason = mutableMapOf<Int, MutableList<EpisodeEntry>>()
    fun add(element: JsonElement, seasonKey: Int?) {
        val record = element as? JsonObject ?: return
        val episode = runCatching { json.decodeFromJsonElement(Episode.serializer(), record) }
            .getOrNull() ?: return
        if (!episode.isPlayable) return
        // Only an object is read as details. `[]` — PHP's empty object — and
        // anything else mean "no details" rather than a failure.
        val details = (record["info"] as? JsonObject)
            ?.let { runCatching { json.decodeFromJsonElement(EpisodeDetails.serializer(), it) }.getOrNull() }
            ?: EpisodeDetails()
        // The grouping key the panel filed it under beats the field inside it:
        // that is the season the panel will show it in, and the field is the
        // one that is sometimes left at zero.
        val season = seasonKey ?: episode.season
        bySeason.getOrPut(season) { mutableListOf() } += EpisodeEntry(episode, details, season)
    }

    when (val episodes = obj["episodes"]) {
        is JsonObject -> episodes.forEach { (key, value) ->
            val seasonKey = key.trim().toIntOrNull()
            (value as? JsonArray)?.forEach { add(it, seasonKey) }
        }
        is JsonArray -> episodes.forEachIndexed { index, value ->
            when (value) {
                // An array of arrays: seasons in order, and the only clue to a
                // season's number is its position — unless the episodes say.
                is JsonArray -> value.forEach { element ->
                    val own = ((element as? JsonObject)?.get("season") as? JsonPrimitive)
                        ?.content?.trim()?.toIntOrNull()?.takeIf { it > 0 }
                    add(element, own ?: (index + 1))
                }
                // One flat array: each episode carries its own season.
                is JsonObject -> add(value, null)
                else -> Unit
            }
        }
        else -> Unit
    }

    val seasons = bySeason.map { (number, entries) ->
        SeasonEntry(
            number = number,
            name = seasonNames[number]?.takeIf { it.isNotBlank() } ?: defaultSeasonName(number),
            episodes = entries
                .distinctBy { it.episode.id }
                .sortedWith(compareBy({ it.episode.episodeNumber == 0 }, { it.episode.episodeNumber })),
        )
    }
        // Season 0 is where panels put specials. Listed last rather than first:
        // someone opening a series expects to start at the beginning, not at a
        // Christmas special from year four.
        .sortedWith(compareBy({ it.number == 0 }, { it.number }))

    return SeriesDetail(
        name = info?.name?.takeIf { it.isNotBlank() } ?: fallbackName,
        plot = info?.plot.orEmpty(),
        cover = info?.cover.orEmpty(),
        seasons = seasons,
    )
}

private fun defaultSeasonName(number: Int): String =
    if (number == 0) "Specials" else "Season $number"

/** Season names from `seasons`, when it is there and readable. */
private fun seasonNames(element: JsonElement?): Map<Int, String> {
    val entries: List<JsonElement> = when (element) {
        is JsonArray -> element
        is JsonObject -> element.values.toList()
        else -> return emptyMap()
    }
    return entries.mapNotNull { entry ->
        val o = entry as? JsonObject ?: return@mapNotNull null
        val number = (o["season_number"] as? JsonPrimitive)?.content?.trim()?.toIntOrNull()
            ?: return@mapNotNull null
        val name = (o["name"] as? JsonPrimitive)?.content.orEmpty()
        number to name
    }.toMap()
}

/**
 * The title with the series name taken off the front.
 *
 * Panels commonly title every episode "Show Name - S01E03 - The Title", which
 * on a screen already headed "Show Name", beside a row already labelled
 * S01E03, is the same words three times. Only the exact series name and an
 * exact episode code are removed, and only from the front — if what is left
 * would be empty, the panel's title is kept as sent.
 */
fun EpisodeEntry.displayTitle(seriesName: String): String {
    val original = episode.title.trim()
    var title = original
    fun dropLeading(prefix: String) {
        if (prefix.isNotBlank() && title.startsWith(prefix, ignoreCase = true)) {
            title = title.substring(prefix.length).trimStart(' ', '-', '–', '—', ':', '|', '.')
        }
    }
    dropLeading(seriesName.trim())
    dropLeading(code)
    return title.ifBlank { original.ifBlank { code.ifBlank { "Episode" } } }
}
