package com.ipterebi.core

import java.util.Locale

/**
 * Up to two letters to stand in for a channel's logo when it has none — or has
 * a dead link, which is a good share of them.
 *
 * Providers put a country or a group in front of the name — "UK: BBC One HD",
 * "SPORT | Arena 1" — and taking initials from that would give every channel in
 * a category the same two letters. So the prefix goes first. Quality tags go
 * too — "HD", "FHD", "4K" — for the same reason from the other end: half a line
 * is "something HD".
 */
fun channelInitials(name: String): String {
    val core = name.substringAfterLast('|').substringAfterLast(':').trim().ifEmpty { name }
    val words = core.split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.isNotEmpty() && it.uppercase(Locale.ROOT) !in QUALITY_TAGS }
    return when {
        words.isEmpty() -> "TV"
        words.size == 1 -> words[0].take(2)
        else -> "${words[0].first()}${words[1].first()}"
    }.uppercase(Locale.ROOT)
}

private val QUALITY_TAGS = setOf("SD", "HD", "FHD", "UHD", "4K", "8K", "HEVC", "H265", "RAW")
