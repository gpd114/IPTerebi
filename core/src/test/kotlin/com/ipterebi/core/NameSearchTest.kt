package com.ipterebi.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Searching channel names the way providers write them and people type them.
 * Every name below is in the shape real lines use.
 */
class NameSearchTest {

    private fun search(query: String, vararg names: String) =
        names.toList().searchByName(query) { it }

    @Test
    fun `words match in any order`() {
        assertEquals(listOf("BBC One HD"), search("one bbc", "BBC One HD", "BBC Two HD"))
    }

    @Test
    fun `every word has to be there`() {
        assertEquals(listOf("BBC One HD"), search("bbc one", "BBC One HD", "BBC Two HD", "One Sports"))
    }

    @Test
    fun `case and punctuation are ignored`() {
        assertEquals(listOf("UK: BBC ONE HD"), search("uk bbc", "UK: BBC ONE HD", "US: CNN"))
        assertEquals(listOf("|FR| TF1 FHD"), search("fr tf1", "|FR| TF1 FHD", "|FR| M6"))
    }

    @Test
    fun `accents are ignored both ways`() {
        assertEquals(listOf("Télé Loisirs"), search("tele", "Télé Loisirs", "Tennis TV"))
        assertEquals(listOf("TELE 5"), search("télé", "TELE 5", "Arte"))
    }

    @Test
    fun `a word can match inside a run-together name`() {
        // Providers write "SkySportsMain"; people type "sports".
        assertEquals(listOf("SkySportsMain Event"), search("sports", "SkySportsMain Event", "Sky News"))
    }

    @Test
    fun `an exact name comes first, then names starting with it, then the rest`() {
        val results = search(
            "sky news",
            "UK: Sky News Arabia",   // contains it
            "Sky News",              // exactly it
            "Sky News HD",           // starts with it
        )
        assertEquals(listOf("Sky News", "Sky News HD", "UK: Sky News Arabia"), results)
    }

    @Test
    fun `a word starting with the query beats one merely containing it`() {
        // "one" starts a word in "BBC One" and is buried in "Phone Shop".
        assertEquals(listOf("BBC One", "Phone Shop"), search("one", "Phone Shop", "BBC One"))
    }

    @Test
    fun `equal ranks keep the provider's order`() {
        // Usually channel-number order, which is the order people expect.
        val results = search("news", "UK: Sky News", "US: CNN News", "FR: BFM News")
        assertEquals(listOf("UK: Sky News", "US: CNN News", "FR: BFM News"), results)
    }

    @Test
    fun `a blank or punctuation-only query returns everything unchanged`() {
        val names = arrayOf("B", "A", "C")
        assertEquals(names.toList(), search("", *names))
        assertEquals(names.toList(), search("  :| ", *names))
    }

    @Test
    fun `nothing matching is an empty list`() {
        assertTrue(search("zdf", "BBC One", "ITV").isEmpty())
    }

    @Test
    fun `normalising keeps letters and digits from any script`() {
        // Cyrillic and Greek lines are common, and must not normalise to nothing.
        assertEquals("канал 1", normaliseForSearch("Канал-1"))
        assertEquals("ερτ 1", normaliseForSearch("ΕΡΤ #1"))
        assertEquals("rai uno", normaliseForSearch("  RAI  —  Uno  "))
    }

    @Test
    fun `an index built once answers repeated searches the same as searching the list`() {
        val names = listOf("UK: BBC ONE HD", "UK: BBC TWO", "Sky News", "|FR| TF1", "Télé Loisirs")
        val index = NameIndex(names) { it }

        listOf("bbc", "one bbc", "tele", "news", "", "zzz").forEach { query ->
            assertEquals(names.searchByName(query) { it }, index.search(query), "query \"$query\"")
        }
        assertEquals(5, index.size)
    }

    @Test
    fun `it searches any kind of item by the name it is given`() {
        val channels = listOf(LiveStream(streamId = 1, name = "BBC One"), LiveStream(streamId = 2, name = "ITV"))

        assertEquals(listOf(1), channels.searchByName("bbc") { it.name }.map { it.streamId })
    }
}
