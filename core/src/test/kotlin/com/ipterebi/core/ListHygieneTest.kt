package com.ipterebi.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The channel list is drawn by a LazyColumn keyed on the stream id, and the
 * category row by a LazyRow keyed on the category id. Compose treats a repeated
 * key as a programming error and throws, so "the panel sent two of these" has to
 * be answered here, before the list reaches a screen. Every case below is one
 * this module's own parsing produces from a payload a fork could plausibly send.
 */
class ListHygieneTest {

    @Test
    fun `a channel with no usable stream_id is dropped`() {
        // Absent, unreadable, and explicitly zero all arrive as 0, and the URL
        // built from 0 is /live/u/p/0.ts — a 404 on a healthy panel.
        val channels = listOf(
            LiveStream(streamId = 0, name = "no id at all"),
            LiveStream(streamId = 12345, name = "BBC One HD"),
        )

        assertEquals(listOf(12345), channels.playableChannels().map { it.streamId })
    }

    @Test
    fun `channels repeated inside one category are collapsed`() {
        val channels = listOf(
            LiveStream(streamId = 7, name = "Sky Sports"),
            LiveStream(streamId = 7, name = "Sky Sports"),
        )

        assertEquals(1, channels.playableChannels().size)
    }

    @Test
    fun `a list of unidentified channels does not become a screenful of key zero`() {
        // The crash this exists to prevent: several entries missing stream_id,
        // all defaulting to 0, all claiming the same LazyColumn key.
        val channels = List(20) { LiveStream(name = "channel $it") }

        assertTrue(channels.playableChannels().isEmpty())
    }

    @Test
    fun `keys survive the filtering as a unique set`() {
        val channels = listOf(
            LiveStream(streamId = 0, name = "dropped"),
            LiveStream(streamId = 4, name = "kept"),
            LiveStream(streamId = 4, name = "duplicate"),
            LiveStream(streamId = 9, name = "kept too"),
        ).playableChannels()

        val keys = channels.map { it.streamId }
        assertEquals(keys.size, keys.toSet().size, "duplicate keys survived: $keys")
    }

    @Test
    fun `a category with no id is dropped rather than opened empty`() {
        // A blank category_id cannot be sent as a get_live_streams filter, so
        // the category could only ever open with nothing in it.
        val categories = listOf(
            XtreamCategory(id = "", name = "Uncategorised"),
            XtreamCategory(id = "3", name = "Sport"),
        )

        assertEquals(listOf("3"), categories.usableCategories().map { it.id })
    }

    @Test
    fun `a repeated category is listed once`() {
        val categories = listOf(
            XtreamCategory(id = "3", name = "Sport"),
            XtreamCategory(id = "3", name = "Sport"),
            XtreamCategory(id = "4", name = "News"),
        )

        assertEquals(listOf("3", "4"), categories.usableCategories().map { it.id })
    }

    @Test
    fun `filtering an ordinary list changes nothing`() {
        val channels = listOf(
            LiveStream(streamId = 1, name = "One"),
            LiveStream(streamId = 2, name = "Two"),
        )

        assertEquals(channels, channels.playableChannels())
    }
}
