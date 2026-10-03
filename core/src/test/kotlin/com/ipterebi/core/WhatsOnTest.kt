package com.ipterebi.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Finding a match across the channels carrying it.
 *
 * The fixtures here are not invented. They are what one real line's guide
 * actually held at 10:44 on 3 October 2026, read off the box: the seven feeds
 * of Croatia v England, the pre-match show that overlaps it, and the
 * coincidences the same search turns up. A rule that passes against made-up
 * data and fails against those is no rule at all.
 */
class WhatsOnTest {

    private fun prog(
        channel: String,
        startMinute: Long,
        stopMinute: Long,
        title: String,
        description: String = "",
    ) = XmltvProgramme(channel, startMinute * 60, stopMinute * 60, title, description)

    // 15:15-18:20 on seven channels, five of which say only "Nations League"
    // and put the teams in the description. This is the measured case.
    private val match = listOf(
        prog("ITV1.uk", 915, 1100, "Nations League", "Croatia v England from Zagreb"),
        prog("ITVCentral.uk", 915, 1100, "Nations League", "Croatia v England from Zagreb"),
        prog("ITVGranada.uk", 915, 1100, "Nations League", "Croatia v England from Zagreb"),
        prog("ITVWales.uk", 915, 1100, "UEFA Nations League: Croatia v England"),
        prog("ITVWestcountry.uk", 915, 1100, "Nations League", "Croatia v England from Zagreb"),
        prog("STV.uk", 915, 1100, "Nations League", "Croatia v England from Zagreb"),
        prog("UTV.uk", 915, 1100, "UEFA Nations League: Croatia v England"),
    )

    @Test
    fun `a team in the description is found, which is most of them`() {
        // Five of the seven name the teams nowhere but the description.
        // Searching titles alone found two, and two is not the feature.
        assertEquals(7, match.mentioning("England").size)
    }

    @Test
    fun `the seven feeds are one event, however they titled it`() {
        val showings = match.showings()
        assertEquals(1, showings.size)
        assertEquals(7, showings.single().feeds)
    }

    @Test
    fun `the most specific title is the one shown`() {
        // Five said "Nations League" and two gave the fixture, so a majority
        // would pick the vague one.
        assertEquals("UEFA Nations League: Croatia v England", match.showings().single().title)
    }

    @Test
    fun `a pre-match show is not the match`() {
        // talksport ran "Kick Off - Croatia v England" 14:30-18:30, straight
        // across the game. It overlaps almost entirely and is still a
        // different programme.
        val withPreview = match + prog("talksport.uk", 870, 1110, "Kick Off - Croatia v England")
        val showings = withPreview.showings()
        assertEquals(2, showings.size)
        assertEquals(7, showings.first().feeds)
    }

    @Test
    fun `two things at the same time on different channels stay apart`() {
        val alsoAt1515 = match + prog("EEntertainment.us", 915, 1095, "The Holdovers")
        assertEquals(2, alsoAt1515.showings().size)
    }

    @Test
    fun `the widely carried thing comes first, which is what sinks the noise`() {
        // The same search for "England" really does return these. No word
        // rule separates them - "England" is a whole word in "New England" -
        // so the count has to.
        val noise = listOf(
            prog("news7whdh.us", 840, 900, "7 News Today in New England"),
            prog("NDTV24x7.in", 930, 960, "Out of England"),
            prog("SkySportsCricket.uk", 840, 900, "1st ODI", "England tour"),
        )
        val found = (noise + match).mentioning("England").showings()
        assertEquals("UEFA Nations League: Croatia v England", found.first().title)
        assertEquals(7, found.first().feeds)
        assertTrue(found.drop(1).all { it.feeds == 1 })
    }

    @Test
    fun `an epg id in two different cases is one channel`() {
        // A real line sent both SkySport3.nz and skysport3.nz.
        val both = listOf(
            prog("SkySport3.nz", 735, 960, "World Grand Prix Darts"),
            prog("skysport3.nz", 735, 960, "World Grand Prix Darts"),
        )
        assertEquals(1, both.showings().single().feeds)
    }

    @Test
    fun `a generic slot name on many channels is one showing, as the guide says it`() {
        // "Live: College Football" was on 127 channels - different games on
        // different affiliates, which nothing here can know. Reporting what
        // the guide says beats inventing a distinction.
        val affiliates = (1..20).map { prog("aff$it.us", 1080, 1260, "Live: College Football") }
        assertEquals(20, affiliates.showings().single().feeds)
    }

    @Test
    fun `typing part of a name finds it`() {
        assertEquals(7, match.mentioning("Engl").size)
        // ...but not as a fragment inside an unrelated word.
        assertTrue(listOf(prog("a", 0, 60, "Boxing")).mentioning("Eng").isEmpty())
    }

    @Test
    fun `every word has to be there`() {
        assertEquals(7, match.mentioning("croatia england").size)
        assertTrue(match.mentioning("croatia wales").isEmpty())
    }

    @Test
    fun `accents and case are folded`() {
        val german = listOf(prog("a", 0, 60, "Fußball: 1. FC Köln"))
        assertEquals(1, german.mentioning("koln").size)
    }

    @Test
    fun `a blank search finds nothing rather than everything`() {
        // Unlike a channel-name search, where everything is a fair answer:
        // here everything is a hundred thousand programmes.
        assertTrue(match.mentioning("").isEmpty())
        assertTrue(match.mentioning("   ").isEmpty())
    }

    // What else is on, from the feed being watched.

    @Test
    fun `the other feeds are offered and the one being watched is not`() {
        val playing = match.first { it.channel == "ITV1.uk" }
        val others = assertNotNull(alsoShowing(playing, match.filterNot { it === playing }))
        assertEquals(6, others.feeds)
        assertTrue(others.channels.none { it == "itv1.uk" })
    }

    @Test
    fun `nothing else carrying it is nothing to offer`() {
        val alone = prog("Obscure.tv", 915, 1100, "Regional News")
        assertNull(alsoShowing(alone, match))
    }

    @Test
    fun `the whole event keeps the channel asked about, so a backup feed survives`() {
        // ITV1 HD, ITV1 FHD and ITV1 BACKUP are three streams under one guide
        // channel, and when the one being watched dies the backup is often the
        // best thing to press. Taking the guide channel out would hide all
        // three, so showingOf keeps it and only the failed *stream* is
        // dropped, by the caller that knows which one that was.
        val playing = match.first { it.channel == "ITV1.uk" }
        val whole = assertNotNull(showingOf(playing, match.filterNot { it === playing }))
        assertEquals(7, whole.feeds)
        assertTrue(whole.channels.contains("itv1.uk"))

        val line = listOf(
            stream(1, "ITV1 HD", "ITV1.uk"),
            stream(2, "ITV1 BACKUP", "itv1.uk"),
            stream(3, "STV", "STV.uk"),
        )
        // What the error card offers after ITV1 HD failed: the backup first.
        assertEquals(
            listOf(2, 3),
            whole.streams(line).filterNot { it.streamId == 1 }.map { it.streamId },
        )
    }

    @Test
    fun `the feed being watched is not needed in the candidates`() {
        // The caller asks the guide for a time window; whether that includes
        // the row it is already on should not change the answer.
        val playing = match.first { it.channel == "ITVWales.uk" }
        assertEquals(6, assertNotNull(alsoShowing(playing, match)).feeds)
    }

    // Guide channels to things that can actually be pressed.

    private fun stream(id: Int, name: String, epg: String) =
        LiveStream(streamId = id, name = name, epgChannelId = epg)

    @Test
    fun `several streams on one guide channel are all offered`() {
        // The HD cut, the backup and the 4K one share an epg id. A showing on
        // seven guide channels is more than seven things to press, and that
        // multiplication is the point.
        val line = listOf(
            stream(1, "ITV1 HD", "ITV1.uk"),
            stream(2, "ITV1 FHD", "ITV1.uk"),
            stream(3, "ITV1 BACKUP", "itv1.uk"),
            stream(4, "STV", "STV.uk"),
            stream(5, "Dave", "Dave.uk"),
        )
        val showing = match.showings().single()
        assertEquals(listOf(1, 2, 3, 4), showing.streams(line).map { it.streamId })
    }

    @Test
    fun `a stream with no guide id is not guessed at`() {
        val line = listOf(stream(1, "ITV1 HD", ""), stream(2, "STV", "STV.uk"))
        assertEquals(listOf(2), match.showings().single().streams(line).map { it.streamId })
    }

    @Test
    fun `a hidden channel is not an answer`() {
        // Hiding is applied before this is called, so a line with the channel
        // taken out simply does not offer it.
        val line = listOf(stream(1, "ITV1 HD", "ITV1.uk"), stream(4, "STV", "STV.uk"))
        val visible = line.withoutHidden(listOf(stream(1, "ITV1 HD", "ITV1.uk")).hiddenIds())
        assertEquals(listOf(4), match.showings().single().streams(visible).map { it.streamId })
    }

    @Test
    fun `a showing nothing on the line carries is an empty list, not a crash`() {
        assertEquals(emptyList(), match.showings().single().streams(emptyList()))
    }

    // The index, which is what a screen actually searches.

    @Test
    fun `the index gives the same answer as searching the list`() {
        // Two routes to one rule is two chances to disagree, so this pins
        // them together.
        val noise = listOf(prog("Dave.uk", 915, 1095, "Top Gear"))
        val all = match + noise
        assertEquals(
            all.mentioning("England").showings(),
            WhatsOnIndex(all).search("England"),
        )
    }

    @Test
    fun `the index finds the seven feeds as one showing`() {
        val found = WhatsOnIndex(match).search("Croatia")
        assertEquals(1, found.size)
        assertEquals(7, found.single().feeds)
    }

    @Test
    fun `an index over nothing, and a blank search of a full one`() {
        assertEquals(0, WhatsOnIndex(emptyList()).size)
        assertTrue(WhatsOnIndex(emptyList()).search("England").isEmpty())
        assertTrue(WhatsOnIndex(match).search("").isEmpty())
    }
}
