package com.ipterebi.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Matching a second guide's channels to the line's.
 *
 * The names here are the owner's provider's, taken from their own channel
 * list and guide: `UK: BBC ONE LONDON 4K ◉`, `ENGLISH: AND FLIX`, guide ids
 * like `BBCOneOxford.uk` and `AndFlix.in`. A rule that works on tidy
 * invented names and not on those is no use.
 */
class ExtraGuideTest {

    private fun ours(id: Int, name: String, epg: String) =
        LiveStream(streamId = id, name = name, epgChannelId = epg)

    private fun theirs(id: String, name: String) = XmltvChannel(id, name, "")

    // Reducing a name to what identifies it.

    @Test
    fun `a country prefix comes off`() {
        assertEquals("bbc one london", guideKey("UK: BBC ONE LONDON"))
        assertEquals("and flix", guideKey("ENGLISH: AND FLIX"))
    }

    @Test
    fun `a quality comes off`() {
        assertEquals("bbc one london", guideKey("UK: BBC ONE LONDON 4K"))
        assertEquals("and flix", guideKey("HINDI: AND FLIX HD"))
        assertEquals("sky sports main event", guideKey("UK: SKY SPORTS MAIN EVENT FHD"))
    }

    @Test
    fun `the app's own marks come off with the punctuation`() {
        // The replay mark this app puts on a channel with an archive.
        assertEquals("bbc one london", guideKey("UK: BBC ONE LONDON 4K ◉"))
    }

    @Test
    fun `the two sides meet in the middle`() {
        // Which is the whole point: a provider's decoration and a public
        // guide's plain name reduce to the same thing.
        assertEquals(guideKey("UK: BBC ONE LONDON 4K ◉"), guideKey("BBC One London"))
        assertEquals(guideKey("ENGLISH: AND FLIX"), guideKey("&flix"))
    }

    @Test
    fun `a long first half is not a prefix`() {
        // "Match of the Day: The Final" is a programme-shaped name; dropping
        // everything before the colon would throw away the half that matters.
        assertTrue(guideKey("Match of the Day: The Final").startsWith("match of the day"))
    }

    // Matching one list to the other.

    @Test
    fun `the same id on both sides is the easy case`() {
        val matched = matchGuideChannels(
            ours = listOf(ours(1, "UK: BBC ONE LONDON 4K", "BBCOneLondon.uk")),
            theirs = listOf(theirs("bbconelondon.uk", "Anything At All")),
        )
        // Case-folded, and the name is not even consulted.
        assertEquals("BBCOneLondon.uk", matched["bbconelondon.uk"])
    }

    @Test
    fun `failing that, the reduced name matches`() {
        val matched = matchGuideChannels(
            ours = listOf(ours(1, "UK: BBC ONE LONDON 4K ◉", "BBCOneOxford.uk")),
            theirs = listOf(theirs("bbc.one.london", "BBC One London")),
        )
        // Note the provider's guide id is nothing like theirs — this line
        // really does carry BBC One London under BBCOneOxford.uk.
        assertEquals("BBCOneOxford.uk", matched["bbc.one.london"])
    }

    @Test
    fun `two of ours reducing to one name is refused, not guessed`() {
        // A guide on the wrong channel is worse than no guide: it is wrong
        // with confidence, on a screen built to be trusted.
        val matched = matchGuideChannels(
            ours = listOf(
                ours(1, "UK: BBC ONE LONDON 4K", "BBCOne4K.uk"),
                ours(2, "UK: BBC ONE LONDON HD", "BBCOneHD.uk"),
            ),
            theirs = listOf(theirs("bbc.one.london", "BBC One London")),
        )
        assertNull(matched["bbc.one.london"])
    }

    @Test
    fun `several of their channels can match several of ours`() {
        val matched = matchGuideChannels(
            ours = listOf(
                ours(1, "UK: BBC ONE LONDON", "one.uk"),
                ours(2, "UK: ITV1 LONDON", "itv.uk"),
            ),
            theirs = listOf(
                theirs("a", "BBC One London"),
                theirs("b", "ITV1 London"),
            ),
        )
        assertEquals("one.uk", matched["a"])
        assertEquals("itv.uk", matched["b"])
    }

    @Test
    fun `a channel of ours with no guide id cannot be matched`() {
        val matched = matchGuideChannels(
            ours = listOf(ours(1, "UK: BBC ONE LONDON", "")),
            theirs = listOf(theirs("a", "BBC One London")),
        )
        assertTrue(matched.isEmpty())
    }

    @Test
    fun `nothing in common is nothing matched`() {
        val matched = matchGuideChannels(
            ours = listOf(ours(1, "UK: BBC ONE LONDON", "one.uk")),
            theirs = listOf(theirs("x", "Rai Uno")),
        )
        assertTrue(matched.isEmpty())
    }

    // What to keep once they are matched.

    private fun prog(start: Long, stop: Long, title: String = "Something") =
        XmltvProgramme("ch", start, stop, title, "")

    @Test
    fun `the second source fills silence`() {
        // On a real line most of the week is silence: the provider published
        // 40 hours and keeps seven days of recordings.
        assertTrue(worthKeeping(prog(500, 600), oursFor = emptyList()))
        assertTrue(worthKeeping(prog(500, 600), oursFor = listOf(prog(100, 200))))
    }

    @Test
    fun `where the provider has something, it wins`() {
        // It is closer to what is actually being broadcast, and two
        // programmes over one stretch of one channel is a guide nobody can
        // read.
        assertFalse(worthKeeping(prog(500, 600), oursFor = listOf(prog(450, 550))))
        assertFalse(worthKeeping(prog(500, 600), oursFor = listOf(prog(500, 600))))
        assertFalse(worthKeeping(prog(500, 600), oursFor = listOf(prog(400, 700))))
    }

    @Test
    fun `touching at the edges is not overlapping`() {
        // One ending exactly as the other starts is the ordinary case for a
        // schedule, not a clash.
        assertTrue(worthKeeping(prog(500, 600), oursFor = listOf(prog(400, 500))))
        assertTrue(worthKeeping(prog(500, 600), oursFor = listOf(prog(600, 700))))
    }

    // How far the provider reached, and what a second source may add.

    @Test
    fun `a channel the provider said nothing about takes everything`() {
        // The ordinary case: a line carried 21,077 channels and the
        // provider's xmltv covered 1,366 of them.
        assertTrue(NO_SPAN.empty)
        assertTrue(worthKeepingBeyond(prog(500, 600), NO_SPAN))
    }

    @Test
    fun `beyond the provider horizon is kept`() {
        // Where a second source earns its place: this line published 40
        // hours one day and 17 the next while keeping seven days of
        // recordings.
        val ours = NO_SPAN.plus(prog(100, 200)).plus(prog(200, 300))
        assertEquals(100, ours.earliest)
        assertEquals(300, ours.latest)
        assertTrue(worthKeepingBeyond(prog(300, 400), ours))
        assertTrue(worthKeepingBeyond(prog(0, 100), ours))
    }

    @Test
    fun `inside the provider stretch is left alone`() {
        // The provider is closer to what it is actually broadcasting, a gap
        // there is usually a junction, and two sources interleaved across
        // one evening is a guide nobody can read.
        val ours = NO_SPAN.plus(prog(100, 300))
        assertFalse(worthKeepingBeyond(prog(150, 250), ours))
        assertFalse(worthKeepingBeyond(prog(50, 150), ours))
        assertFalse(worthKeepingBeyond(prog(250, 350), ours))
    }

    @Test
    fun `touching the edge counts as outside`() {
        val ours = NO_SPAN.plus(prog(100, 300))
        assertTrue(worthKeepingBeyond(prog(0, 100), ours))
        assertTrue(worthKeepingBeyond(prog(300, 400), ours))
    }
}
