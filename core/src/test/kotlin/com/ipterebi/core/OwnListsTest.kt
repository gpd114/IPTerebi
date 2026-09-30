package com.ipterebi.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Lists the viewer makes. Every one of these is a way to lose something they
 * put somewhere on purpose, which is worse than a feature that never worked:
 * a name that quietly becomes a second list, an entry dropped by adding it
 * twice, a list reordering itself under the remote.
 */
class OwnListsTest {

    private fun film(id: String, name: String = "A Film") =
        ListedItem(SavedKind.FILM, id, name, extension = "mkv")

    private fun series(id: String, name: String = "A Series") =
        ListedItem(SavedKind.SERIES, id, name)

    @Test
    fun `a name is trimmed and its spaces collapsed, because a remote leaves both`() {
        assertEquals("Saturday", cleanListName("  Saturday  "))
        assertEquals("Saturday night", cleanListName("Saturday   night"))
        assertEquals("Saturday night", cleanListName("\tSaturday \n night "))
    }

    @Test
    fun `a name of nothing is not a name`() {
        assertNull(cleanListName(""))
        assertNull(cleanListName("   "))
        assertNull(cleanListName("\n\t "))
    }

    @Test
    fun `a name too long for a chip is cut rather than refused`() {
        val long = "x".repeat(MAX_LIST_NAME + 20)
        assertEquals(MAX_LIST_NAME, cleanListName(long)?.length)
    }

    @Test
    fun `the same name twice is one list, whatever the case`() {
        val lists = emptyList<OwnList>().withNewList("Saturday").withNewList("saturday")
        assertEquals(1, lists.size)
        assertEquals("Saturday", lists.single().name)
    }

    @Test
    fun `lists keep the order they were made in`() {
        val lists = emptyList<OwnList>().withNewList("One").withNewList("Two").withNewList("Three")
        assertEquals(listOf("One", "Two", "Three"), lists.map { it.name })
    }

    @Test
    fun `adding to a list that does not exist makes it`() {
        val lists = emptyList<OwnList>().withItem("Saturday", film("1"))
        assertEquals(listOf("Saturday"), lists.map { it.name })
        assertTrue(lists.single().holds(SavedKind.FILM, "1"))
    }

    @Test
    fun `the same thing twice stays once, and keeps what it knew first`() {
        // The second may come from a screen with no poster to hand — a home
        // row, a search result — and overwriting would blank the card.
        val withPoster = film("1").copy(poster = "http://panel/poster.jpg")
        val lists = emptyList<OwnList>()
            .withItem("Saturday", withPoster)
            .withItem("Saturday", film("1"))
        assertEquals(1, lists.single().items.size)
        assertEquals("http://panel/poster.jpg", lists.single().items.single().poster)
    }

    @Test
    fun `things stay in the order they were put in`() {
        // Curated, so newest-first would move what someone just pointed at.
        val lists = emptyList<OwnList>()
            .withItem("Saturday", film("1", "First"))
            .withItem("Saturday", film("2", "Second"))
            .withItem("Saturday", film("3", "Third"))
        assertEquals(listOf("First", "Second", "Third"), lists.single().items.map { it.name })
    }

    @Test
    fun `a film and a series may share an id without being the same thing`() {
        // Two different numberings on the same panel, and nothing says they
        // cannot collide.
        val lists = emptyList<OwnList>()
            .withItem("Saturday", film("7"))
            .withItem("Saturday", series("7"))
        assertEquals(2, lists.single().items.size)
        assertTrue(lists.single().holds(SavedKind.FILM, "7"))
        assertTrue(lists.single().holds(SavedKind.SERIES, "7"))
    }

    @Test
    fun `taking something out leaves the list itself`() {
        val lists = emptyList<OwnList>()
            .withItem("Saturday", film("1"))
            .withItem("Saturday", film("2"))
            .withoutItem("Saturday", SavedKind.FILM, "1")
        assertEquals(1, lists.size)
        assertEquals(listOf("2"), lists.single().items.map { it.id })
    }

    @Test
    fun `taking the last thing out leaves an empty list, not no list`() {
        // Emptying a list is not deleting it: the name was someone's decision.
        val lists = emptyList<OwnList>()
            .withItem("Saturday", film("1"))
            .withoutItem("Saturday", SavedKind.FILM, "1")
        assertEquals(1, lists.size)
        assertTrue(lists.single().items.isEmpty())
    }

    @Test
    fun `deleting a list takes its contents with it and leaves the others`() {
        val lists = emptyList<OwnList>()
            .withItem("Saturday", film("1"))
            .withItem("Sunday", film("2"))
            .withoutList("saturday")
        assertEquals(listOf("Sunday"), lists.map { it.name })
    }

    @Test
    fun `a screen can ask which lists hold something, to offer taking it out`() {
        val lists = emptyList<OwnList>()
            .withItem("Saturday", film("1"))
            .withItem("Later", film("1"))
            .withItem("Later", film("2"))
        assertEquals(listOf("Saturday", "Later"), lists.listsHolding(SavedKind.FILM, "1"))
        assertEquals(listOf("Later"), lists.listsHolding(SavedKind.FILM, "2"))
        assertEquals(emptyList(), lists.listsHolding(SavedKind.SERIES, "1"))
    }

    @Test
    fun `the films screen is only shown lists with films in them`() {
        val lists = emptyList<OwnList>()
            .withItem("Films only", film("1"))
            .withItem("Series only", series("9"))
            .withItem("Both", film("2"))
            .withItem("Both", series("8"))
            .withNewList("Empty")
        assertEquals(listOf("Films only", "Both"), lists.withAnyOf(SavedKind.FILM).map { it.name })
        assertEquals(listOf("Series only", "Both"), lists.withAnyOf(SavedKind.SERIES).map { it.name })
    }

    @Test
    fun `there is a ceiling on how many lists, and it does not throw`() {
        var lists = emptyList<OwnList>()
        repeat(MAX_LISTS + 5) { lists = lists.withNewList("List $it") }
        assertEquals(MAX_LISTS, lists.size)
        // And the one that would not fit did not quietly replace another.
        assertTrue(lists.hasList("List 0"))
        assertFalse(lists.hasList("List ${MAX_LISTS + 4}"))
    }

    @Test
    fun `a name that is only spaces adds nothing`() {
        assertEquals(emptyList(), emptyList<OwnList>().withNewList("   "))
        assertEquals(emptyList(), emptyList<OwnList>().withItem("  ", film("1")))
    }
}
