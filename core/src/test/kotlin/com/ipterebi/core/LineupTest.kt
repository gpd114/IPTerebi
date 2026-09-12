package com.ipterebi.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The whole line as a remote sees it: groups, numbers, channel up and down. */
class LineupTest {

    private fun ch(id: Int, category: String = "1", number: Int = 0, name: String = "Channel $id") =
        LiveStream(streamId = id, name = name, categoryId = category, number = number)

    private val news = XtreamCategory(id = "1", name = "News")
    private val sport = XtreamCategory(id = "2", name = "Sport")

    @Test
    fun `groups follow the panel's category order and skip empty ones`() {
        val lineup = Lineup(
            channels = listOf(ch(1, "2"), ch(2, "1"), ch(3, "2")),
            categories = listOf(news, XtreamCategory(id = "9", name = "Empty"), sport),
        )
        assertEquals(listOf("News", "Sport"), lineup.groups.map { it.name })
        assertEquals(listOf(1, 3), lineup.group("2")!!.channels.map { it.streamId })
    }

    @Test
    fun `channels in a category the panel never listed are still reachable`() {
        val lineup = Lineup(listOf(ch(1, "1"), ch(2, "77"), ch(3, "")), listOf(news))
        val other = lineup.groups.last()
        assertEquals(Lineup.OTHER_GROUP_ID, other.id)
        assertEquals(listOf(2, 3), other.channels.map { it.streamId })
    }

    @Test
    fun `no other group when every channel has a named category`() {
        val lineup = Lineup(listOf(ch(1, "1"), ch(2, "2")), listOf(news, sport))
        assertNull(lineup.group(Lineup.OTHER_GROUP_ID))
    }

    @Test
    fun `the provider's numbers are used when each names one channel`() {
        val lineup = Lineup(listOf(ch(10, number = 101), ch(11, number = 102)), listOf(news))
        assertEquals(102, lineup.numberOf(11))
        assertEquals(10, lineup.byNumber(101)?.streamId)
    }

    @Test
    fun `positions are used when the provider's numbers repeat`() {
        // Numbering that starts again in every category: 1, 2, 1.
        val lineup = Lineup(
            listOf(ch(10, "1", number = 1), ch(11, "1", number = 2), ch(12, "2", number = 1)),
            listOf(news, sport),
        )
        assertEquals(listOf(1, 2, 3), listOf(10, 11, 12).map(lineup::numberOf))
        assertEquals(12, lineup.byNumber(3)?.streamId)
    }

    @Test
    fun `positions are used when some channels have no number`() {
        val lineup = Lineup(listOf(ch(10, number = 5), ch(11, number = 0)), listOf(news))
        assertEquals(2, lineup.numberOf(11))
        assertNull(lineup.byNumber(5))
    }

    @Test
    fun `a channel not on the line has no number`() {
        assertEquals(0, Lineup(listOf(ch(1)), listOf(news)).numberOf(99))
    }

    @Test
    fun `channel up and down wrap at either end`() {
        val list = listOf(ch(1), ch(2), ch(3))
        assertEquals(2, list.zap(fromId = 1, step = 1)?.streamId)
        assertEquals(1, list.zap(fromId = 3, step = 1)?.streamId)
        assertEquals(3, list.zap(fromId = 1, step = -1)?.streamId)
    }

    @Test
    fun `from a channel outside the list, up starts at the top and down at the bottom`() {
        val list = listOf(ch(1), ch(2), ch(3))
        assertEquals(1, list.zap(fromId = 42, step = 1)?.streamId)
        assertEquals(3, list.zap(fromId = 42, step = -1)?.streamId)
    }

    @Test
    fun `an empty list goes nowhere`() {
        assertNull(emptyList<LiveStream>().zap(fromId = 1, step = 1))
    }

    @Test
    fun `typed numbers grow to four digits, then start again`() {
        assertEquals("1", typedChannelNumber("", '1'))
        assertEquals("102", typedChannelNumber("10", '2'))
        assertEquals("7", typedChannelNumber("1234", '7'))
    }
}
