package com.ipterebi.app.data

import com.ipterebi.core.EpisodeEntry
import com.ipterebi.core.displayTitle

/**
 * An episode together with the series it belongs to, which is what the player
 * needs to put a sensible title on it — "Slow Horses · S02E03 Hello Goodbye"
 * rather than whatever the panel's raw title happens to be.
 */
data class EpisodeListing(val seriesName: String, val entry: EpisodeEntry) {
    val id: String get() = entry.episode.id

    val title: String
        get() = listOf(seriesName, "${entry.code} ${entry.displayTitle(seriesName)}".trim())
            .filter { it.isNotBlank() }
            .joinToString(" · ")
}
