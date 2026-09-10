package com.ipterebi.core

import java.text.Normalizer
import java.util.Locale

/**
 * Finding a channel by name, the way people type channel names.
 *
 * Providers name channels "UK: BBC ONE HD", "|FR| TF1 FHD", "SkySportsMain Event".
 * People type "bbc one", "tf1", "sky sports". So:
 *
 * - **Every word typed must appear, in any order.** "one bbc" finds "BBC One";
 *   a plain substring match would not.
 * - **Punctuation and case are ignored.** "uk bbc" finds "UK: BBC ONE HD".
 * - **Accents are ignored.** "tele" finds "Télé"; a French line typed on an
 *   English keyboard should not come back empty.
 * - **A word may match inside a longer one**, because providers run words
 *   together: "sports" has to find "SkySportsMain".
 *
 * Results are ranked so the channel you meant is near the top: a name that is
 * exactly the query first, then names that start with it, then names where a
 * word starts with it, then everything else that matched. Within a rank the
 * provider's own order is kept, which is usually channel-number order.
 */
fun <T> List<T>.searchByName(query: String, nameOf: (T) -> String): List<T> =
    NameIndex(this, nameOf).search(query)

/**
 * The same search, over names normalised once up front.
 *
 * For a list searched repeatedly — every keystroke, over every channel on a
 * line — normalising each name each time is the expensive part: accent
 * stripping and two regular expressions, tens of thousands of times, per key.
 * Build this once when the list arrives and [search] only compares strings.
 */
class NameIndex<T>(items: List<T>, nameOf: (T) -> String) {

    private val entries: List<Pair<T, String>> = items.map { it to normaliseForSearch(nameOf(it)) }

    val size: Int get() = entries.size

    /** Everything, unchanged, for a blank query; otherwise ranked matches. */
    fun search(query: String): List<T> {
        val needle = normaliseForSearch(query)
        if (needle.isEmpty()) return entries.map { it.first }
        val words = needle.split(' ')

        return entries
            .mapNotNull { (item, name) ->
                if (words.all { it in name }) item to rank(name, needle, words.first()) else null
            }
            // sortedBy is stable, so equal ranks keep the provider's order.
            .sortedBy { (_, rank) -> rank }
            .map { (item, _) -> item }
    }
}

/**
 * Lower case, accents stripped, anything that is not a letter or a digit turned
 * into a single space. Public so that callers can reuse it for highlighting,
 * and so the tests can say exactly what is compared.
 */
fun normaliseForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

private fun rank(name: String, needle: String, firstWord: String): Int = when {
    name == needle -> 0
    name.startsWith(needle) -> 1
    name.split(' ').any { it.startsWith(firstWord) } -> 2
    else -> 3
}
