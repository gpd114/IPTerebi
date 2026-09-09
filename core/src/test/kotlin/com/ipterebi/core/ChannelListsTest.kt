package com.ipterebi.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Favourites and recently watched: the lists the user builds rather than the
 * provider. They outlive the channel list they were made from, so every way a
 * provider can move underneath them is a case here.
 */
class ChannelListsTest {

    private fun channel(id: Int, name: String = "Channel $id") =
        LiveStream(streamId = id, name = name)

    private val account = XtreamAccount(
        base = "http://line.example.com:8080",
        username = "alice",
        password = "s3cret",
    )

    // Favourites

    @Test
    fun `starring adds and starring again removes`() {
        val bbc = channel(1)

        val starred = emptyList<LiveStream>().withFavouriteToggled(bbc)
        assertEquals(listOf(bbc), starred)

        assertEquals(emptyList(), starred.withFavouriteToggled(bbc))
    }

    @Test
    fun `unstarring matches on id, not on the whole record`() {
        // Providers rename channels constantly — "BBC One" becomes "UK: BBC ONE
        // HD" overnight. Matching the whole record would leave the old entry
        // stranded and unstarrable.
        val stored = listOf(channel(1, "BBC One"))
        val renamed = channel(1, "UK: BBC ONE HD")

        assertTrue(stored.withFavouriteToggled(renamed).isEmpty())
    }

    @Test
    fun `starring appends rather than reordering what is already there`() {
        val list = listOf(channel(1), channel(2)).withFavouriteToggled(channel(3))

        assertEquals(listOf(1, 2, 3), list.map { it.streamId })
    }

    @Test
    fun `unstarring leaves the rest in order`() {
        val list = listOf(channel(1), channel(2), channel(3))
            .withFavouriteToggled(channel(2))

        assertEquals(listOf(1, 3), list.map { it.streamId })
    }

    @Test
    fun `an unplayable channel is never starred`() {
        // Stream id 0 is what the flexible parsing answers for one it could not
        // read, and the URL built from it 404s.
        val list = emptyList<LiveStream>().withFavouriteToggled(channel(0))
        assertTrue(list.isEmpty())
    }

    // Recently watched

    @Test
    fun `the most recently watched is first`() {
        val list = emptyList<LiveStream>()
            .withRecent(channel(1))
            .withRecent(channel(2))

        assertEquals(listOf(2, 1), list.map { it.streamId })
    }

    @Test
    fun `re-watching moves a channel up rather than repeating it`() {
        val list = emptyList<LiveStream>()
            .withRecent(channel(1))
            .withRecent(channel(2))
            .withRecent(channel(1))

        assertEquals(listOf(1, 2), list.map { it.streamId })
    }

    @Test
    fun `re-watching takes the newer name`() {
        val list = listOf(channel(1, "BBC One")).withRecent(channel(1, "UK: BBC ONE HD"))

        assertEquals("UK: BBC ONE HD", list.single().name)
    }

    @Test
    fun `the oldest falls off the end`() {
        var list = emptyList<LiveStream>()
        repeat(RECENTS_LIMIT + 5) { list = list.withRecent(channel(it + 1)) }

        assertEquals(RECENTS_LIMIT, list.size)
        assertEquals(RECENTS_LIMIT + 5, list.first().streamId)
        // 1..5 pushed off the end.
        assertFalse(list.holds(1))
        assertTrue(list.holds(6))
    }

    @Test
    fun `an unplayable channel is never recorded as watched`() {
        assertTrue(emptyList<LiveStream>().withRecent(channel(0)).isEmpty())
    }

    @Test
    fun `the last watched channel is simply the first recent one`() {
        // "Resume the last channel" and "recently watched" are one mechanism,
        // not two — this pins that they cannot drift apart.
        val list = emptyList<LiveStream>().withRecent(channel(7)).withRecent(channel(9))

        assertEquals(9, list.firstOrNull()?.streamId)
    }

    // Line scoping

    @Test
    fun `two lines get different keys`() {
        // Stream ids are a panel's private numbering: id 4271 on one provider
        // and 4271 on another are unrelated channels, so sharing a list between
        // lines would show a favourite that plays something else.
        val other = account.copy(base = "http://other.example.com:8080")
        val sameHostDifferentUser = account.copy(username = "bob")

        assertNotEquals(account.lineKey, other.lineKey)
        assertNotEquals(account.lineKey, sameHostDifferentUser.lineKey)
    }

    @Test
    fun `the key does not change when the format or user agent does`() {
        // Flipping a chip in settings rebuilds the account. Losing the
        // favourites because of it would be absurd.
        val reformatted = account.copy(format = StreamFormat.HLS, userAgent = UserAgents.FFMPEG)

        assertEquals(account.lineKey, reformatted.lineKey)
    }

    @Test
    fun `the key does not leak the username`() {
        assertFalse(account.lineKey.contains("alice"))
        assertFalse(account.lineKey.contains("line.example.com"))
        assertEquals(16, account.lineKey.length)
        assertTrue(account.lineKey.all { it in "0123456789abcdef" }, "not hex: ${account.lineKey}")
    }

    @Test
    fun `the key is stable across calls`() {
        assertEquals(account.lineKey, account.copy().lineKey)
    }

    // Membership

    @Test
    fun `holds finds a channel by id`() {
        val list = listOf(channel(1), channel(2))

        assertTrue(list.holds(2))
        assertFalse(list.holds(3))
        assertFalse(emptyList<LiveStream>().holds(1))
    }
}
