package com.ipterebi.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun `a real line's worth of programmes groups without grinding`() {
        // The bug this pins cost an evening of testing and was invisible on
        // the fake panel. Grouping compared each programme against every
        // event so far and normalised both titles inside the comparison, so
        // a window around one programme on a real line — 3,163 rows across
        // 160 start times — turned into a quarter of a million comparisons
        // and half a million NFD normalisations. The box sat silent: no
        // error, no card, nothing in the log after "looking for".
        //
        // Twenty channels could never show it, so the shape of a real line is
        // the test. The bound is deliberately loose — this is a guard against
        // quadratic work, not a benchmark.
        val programmes = buildList {
            for (slot in 0 until 200) {
                val start = slot * 30L * 60
                for (channel in 0 until 20) {
                    add(
                        prog(
                            channel = "ch$channel.uk",
                            startMinute = start / 60,
                            stopMinute = start / 60 + 30,
                            // One long distinct word, so nothing groups and
                            // the grouping does its most work. Titles that
                            // differ only by a number do *not* count as
                            // distinct here — see the test below.
                            title = "Programme${slot}Feed$channel",
                            description = "Some words about it, long enough to cost something to fold.",
                        ),
                    )
                }
            }
        }
        assertEquals(4000, programmes.size)

        val began = System.nanoTime()
        val found = programmes.showings()
        val tookMillis = (System.nanoTime() - began) / 1_000_000

        // Every title here is distinct, so nothing groups: 4,000 events.
        assertEquals(4000, found.size)
        assertTrue(tookMillis < 4_000, "grouping 4,000 programmes took ${tookMillis}ms")
    }

    @Test
    fun `two titles differing only by a number are taken for one event`() {
        // Found by writing the test above, not by design: words of one or two
        // letters are skipped so that a title made only of them cannot glue
        // to everything, and a digit is one of those. So "Match Day 3" and
        // "Match Day 4" at the same moment read as the same event.
        //
        // Recorded rather than fixed, because the same rule is what merges
        // the variants that matter — "Nations League" into "UEFA Nations
        // League: Croatia v England" — and on a real line the damage is a
        // channel offered that is showing the next fixture rather than this
        // one. If a provider turns up where this misleads, the fix is to
        // keep short tokens when they are all that differs.
        val two = listOf(
            prog("a.uk", 900, 960, "Match Day 3"),
            prog("b.uk", 900, 960, "Match Day 4"),
        )
        assertEquals(1, two.showings().size)

        // Words long enough to tell apart do tell them apart.
        val apart = listOf(
            prog("a.uk", 900, 960, "Live: EFL League One"),
            prog("b.uk", 900, 960, "Live: EFL League Two"),
        )
        assertEquals(2, apart.showings().size)
    }

    @Test
    fun `and it still groups them when they share a title`() {
        // The other half of the same shape: one title per slot across twenty
        // channels, which is what a syndicated programme looks like.
        val programmes = buildList {
            for (slot in 0 until 200) {
                for (channel in 0 until 20) {
                    add(
                        prog(
                            channel = "ch$channel.uk",
                            startMinute = slot * 30L,
                            stopMinute = slot * 30L + 30,
                            title = "Slot $slot",
                        ),
                    )
                }
            }
        }
        val found = programmes.showings()
        assertEquals(200, found.size)
        assertTrue(found.all { it.feeds == 20 })
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

    // One per channel before any second. Measured on the owner's line: an
    // event on three guide channels was being listed as thirty-six, because
    // the provider carries each one as HD, SD, RAW, HEVC HD and HEVC 4K.

    private fun stream(id: Int, epg: String) =
        LiveStream(streamId = id, name = "ch$id", epgChannelId = epg)

    @Test
    fun `every different channel comes before any second stream`() {
        val spread = listOf(
            stream(1, "sky.uk"), stream(2, "sky.uk"), stream(3, "sky.uk"),
            stream(4, "tnt.uk"), stream(5, "tnt.uk"),
            stream(6, "bbc.uk"),
        ).spreadByChannel()
        // Three different channels first, then the seconds, then the third.
        assertEquals(listOf(1, 4, 6, 2, 5, 3), spread.map { it.streamId })
    }

    @Test
    fun `the variants are kept, not filtered`() {
        // A line carries ITV1 as the HD cut, the FHD cut and a backup under
        // one guide id, and when a feed dies another stream of the same
        // channel is very often the best thing to switch to.
        val streams = listOf(stream(1, "itv.uk"), stream(2, "itv.uk"), stream(3, "itv.uk"))
        assertEquals(3, streams.spreadByChannel().size)
    }

    @Test
    fun `guide ids are folded, as a real line sends them`() {
        // One line sent SkySport3.nz beside skysport3.nz.
        val spread = listOf(
            stream(1, "SkySport3.nz"), stream(2, "skysport3.nz"), stream(3, "other.uk"),
        ).spreadByChannel()
        assertEquals(listOf(1, 3, 2), spread.map { it.streamId })
    }

    @Test
    fun `a channel with no guide id is not spread with the others`() {
        // Nothing is known to share it, so nothing should be interleaved
        // with it as though it were the same channel.
        val spread = listOf(
            stream(1, ""), stream(2, ""), stream(3, "sky.uk"),
        ).spreadByChannel()
        assertEquals(3, spread.size)
        assertEquals(setOf(1, 2, 3), spread.map { it.streamId }.toSet())
    }

    @Test
    fun `one channel per stream is left exactly as it was`() {
        // The common case, and it must not be reordered: the provider's own
        // numbering decides ties.
        val streams = listOf(stream(1, "a.uk"), stream(2, "b.uk"), stream(3, "c.uk"))
        assertEquals(streams, streams.spreadByChannel())
    }

    @Test
    fun `nothing and one thing are returned unchanged`() {
        assertTrue(emptyList<LiveStream>().spreadByChannel().isEmpty())
        val one = listOf(stream(1, "a.uk"))
        assertEquals(one, one.spreadByChannel())
    }

    // Saying what is carrying it.

    @Test
    fun `the label separates channels from feeds`() {
        // "on 36 channels" overstated how many places there were to go when
        // one failed, and read as nonsense beside a list that was plainly the
        // same three names repeated.
        assertEquals("on 3 channels · 36 feeds", carriedOnLabel(guideChannels = 3, feeds = 36))
    }

    @Test
    fun `one number is enough when they agree`() {
        assertEquals("on 4 channels", carriedOnLabel(guideChannels = 4, feeds = 4))
        assertEquals("on 1 channel", carriedOnLabel(guideChannels = 1, feeds = 1))
    }

    // What the guide calls sport. Asked of the categories, never of the
    // title: a fixture is listed as "Nations League" or "Kick Off - Croatia v
    // England" depending on who wrote it, and no list of words separates
    // those from a documentary about the same team.

    @Test
    fun `the guide's own word for it is taken`() {
        // Measured across the owner's five sources: UK1 alone tags 2,351
        // programmes Sports, beside Football and Motor Sports.
        assertTrue(isSport(listOf("Sports")))
        assertTrue(isSport(listOf("Football")))
        assertTrue(isSport(listOf("American Football")))
        assertTrue(isSport(listOf("Motor Sports")))
        assertTrue(isSport(listOf("Sports Show")))
    }

    @Test
    fun `Transport is not sport`() {
        // A real category in that guide, and the reason this matches whole
        // words rather than substrings. A looser rule is a screen full of
        // freight documentaries.
        assertFalse(isSport(listOf("Transport")))
    }

    @Test
    fun `any one category is enough`() {
        // "Sports" and "Football" together is ordinary, and a programme
        // tagged with both a genre and sport is still sport.
        assertTrue(isSport(listOf("Entertainment", "Football")))
    }

    @Test
    fun `a guide that says nothing is not guessed at`() {
        // Most of a provider's own xmltv.php omits categories, and for those
        // channels this answers false rather than pretending.
        assertFalse(isSport(emptyList()))
        assertFalse(isSport(listOf("")))
        assertFalse(isSport(listOf("Entertainment", "Drama", "Shopping")))
    }

    @Test
    fun `case and punctuation do not matter`() {
        assertTrue(isSport(listOf("SPORTS")))
        assertTrue(isSport(listOf("sport")))
        assertTrue(isSport(listOf("Sport/Leisure")))
    }
}
