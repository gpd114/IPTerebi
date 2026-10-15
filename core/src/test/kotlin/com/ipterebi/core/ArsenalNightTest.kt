package com.ipterebi.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * One real evening, read off the owner's own box.
 *
 * Arsenal v Lille in the Champions League, 13 October 2026, and the team row
 * said there was nothing on. The guide had it: the database sieve answered
 * **102 programmes** for "Arsenal" in the four-day window, across the titles
 * below — which are verbatim, country spellings and all.
 *
 * So this pins the half that can be proven without a device: that the rule and
 * the grouping turn those rows into the fixture. If this ever fails, the fault
 * is here. While it passes, a row saying nothing is the Android half — the
 * channel list it needs to turn guide ids into something pressable.
 */
class ArsenalNightTest {

    // 13 October 2026, 21:02 UTC — the moment the box was asked.
    private val now = 1791921720L

    private fun at(hhmm: String, minutes: Long, channel: String, title: String, description: String = "") =
        XmltvProgramme(
            channel = channel,
            start = hhmm.toEpoch(),
            stop = hhmm.toEpoch() + minutes * 60,
            title = title,
            description = description,
        )

    /** "18:00" on that day, in unix seconds. */
    private fun String.toEpoch(): Long {
        val (h, m) = split(":").map { it.toLong() }
        return 1791849600L + h * 3600 + m * 60 // 2026-10-13 00:00:00 UTC
    }

    private val evening = listOf(
        at("18:00", 210, "TNTSport1.uk", "Live UEFA Champions League", "Arsenal v Lille from the Emirates"),
        at("18:00", 120, "skysport256.it", "Arsenal - Lille"),
        at("18:30", 210, "TNTSport3.uk", "Live: UEFA Champions League Goals Show Xtra", "Including Arsenal v Lille"),
        at("18:45", 195, "VirginMediaTwo.ie", "UEFA Champions League 2026/27 - Live", "Arsenal v Lille"),
        at("18:55", 125, "beinsports3.sg", "UEFA Champions League 2026/27: Arsenal vs Lille - MD2 (Live)"),
        at("19:00", 117, "CanalPlusLive1.fr", "Arsenal / Lille"),
        at("19:00", 120, "arenasportpremium3.ba", "Uživo: Arsenal - Lille"),
        at("19:00", 117, "canalplusfoot.fr", "Arsenal / Lille"),
        at("19:00", 120, "primasport4.ro", "LIVE: Meci Fotbal UEFA Champions League: Arsenal - Lille"),
        // On at the same moment and nothing to do with them, as the real
        // guide had it: a different tie, and a radio show naming other clubs.
        at("18:55", 125, "skysportarena.it", "Villarreal CF - Napoli"),
        at("18:00", 60, "talksport.uk", "Kick Off - Atletico Madrid v Man U"),
    )

    @Test
    fun `the fixture is found among the night's other football`() {
        val showings = evening.mentioning("Arsenal").showings()
        assertTrue(showings.isNotEmpty(), "nothing matched Arsenal at all")

        val match = teamMatch(showings, now, "Arsenal")
        assertTrue(match != null, "no match chosen from ${showings.size} showings")
        assertTrue(
            match.showing.title.contains("Arsenal", ignoreCase = true) ||
                match.showing.title.contains("Champions League", ignoreCase = true),
            "chose the wrong thing: ${match.showing.title}",
        )
    }

    @Test
    fun `it is reported as on now, not as something coming`() {
        val match = teamMatch(evening.mentioning("Arsenal").showings(), now, "Arsenal")
        assertTrue(match != null)
        assertTrue(now >= match.showing.start && now < match.showing.stop, "not treated as on now")
    }

    @Test
    fun `the unrelated tie on at the same moment is left out`() {
        val showings = evening.mentioning("Arsenal").showings()
        assertTrue(
            showings.none { it.title.contains("Villarreal") },
            "a different fixture was pulled in",
        )
    }

    /**
     * The teams are often only in the description, which is why the sieve
     * reads both. Five of the seven feeds carrying Croatia v England named it
     * only "Nations League"; this night is the same shape.
     */
    @Test
    fun `a feed naming the teams only in its description still counts`() {
        val showings = evening.mentioning("Arsenal").showings()
        // Folded to lower case, as Showing documents: a real line sends the
        // same id both ways.
        val channels = showings.flatMap { it.channels }.toSet()
        assertTrue("tntsport1.uk" in channels, "the UK feed was missed: $channels")
    }

    @Test
    fun `every feed carrying it is offered, not just the one that names it`() {
        val showings = evening.mentioning("Arsenal").showings()
        // Grouping alone makes five events of one fixture, because the
        // broadcasters start their build-up at 18:00, 18:30, 18:45, 18:55 and
        // 19:00 and title it however they like. That is why the row widens.
        assertTrue(showings.size > 1, "the night grouped into one event, so this no longer tests anything")

        val match = teamMatch(showings, now, "Arsenal")
        assertTrue(match != null)
        val feeds = match.showing.channels
        assertTrue(feeds.size >= 9, "only ${feeds.size} feeds offered: $feeds")

        // The two the owner can actually watch in England, and the ones the
        // row used to drop: TNT titles it generically and names the sides
        // only in its description.
        assertTrue("tntsport1.uk" in feeds, "the UK feed was not offered: $feeds")
        assertTrue("tntsport3.uk" in feeds, "the UK companion feed was not offered: $feeds")
    }

    @Test
    fun `the name is read as the viewer typed it`() {
        assertEquals("Arsenal", cleanTeamName("  Arsenal  "))
    }
}
