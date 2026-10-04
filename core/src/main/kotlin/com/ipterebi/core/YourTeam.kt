package com.ipterebi.core

/**
 * Your team, and the channels showing them.
 *
 * The thing this was all asked for: *"find out which channels my team is
 * playing and have them listed, so I can easily switch between them if the
 * connection goes bad."* A list, standing there, not a question to be asked
 * again each time. The name is typed once; after that it is simply on the
 * screen.
 *
 * Why that matters rather than a search box: the moment the list is wanted
 * is the moment a feed has just died, mid-match, with a remote in hand. That
 * is the worst possible time to be typing a team name letter by letter on a
 * D-pad keyboard. Whatever work there is should have been done in advance,
 * and all of it can be — the name does not change from week to week.
 *
 * [Showing] and its grouping do the finding; this only decides *which* match
 * to put on the screen, which is the one judgement involved.
 */

/** A team's match, and when it is. */
data class TeamMatch(
    val showing: Showing,
    /** True while it is on; false when it is still to come. */
    val onNow: Boolean,
    /**
     * Whether this reads as the team playing, rather than as a programme
     * that merely says their name. The row labels the two differently.
     */
    val isFixture: Boolean,
)

/**
 * The match worth showing for a team, out of everything found for them.
 *
 * On now wins, because that is the one a dying feed is interrupting. Failing
 * that, the soonest still to come, which is what makes the row worth keeping
 * on screen between matches rather than going blank for six days.
 *
 * Among several on at once — a team's match and a highlights programme
 * naming them, say — the one on the most channels wins. That is the same
 * ranking the search uses and for the same reason: a real fixture is carried
 * widely and a programme merely mentioning the team is not.
 *
 * Null when the guide holds nothing for them, which is the ordinary case for
 * most of the week and must read as "nothing this week" rather than as a
 * fault.
 */
fun teamMatch(showings: List<Showing>, nowSeconds: Long, team: String): TeamMatch? {
    val live = showings.filter { nowSeconds >= it.start && nowSeconds < it.stop }
    val upcoming = showings.filter { it.start > nowSeconds }

    // A fixture outranks a mention whether it is on or not, and that ordering
    // was the other way round at first. "France" found France24's sports
    // bulletin and a motocross championnat on now, both of which merely say
    // the word, and they beat France v Italy that same evening — which is
    // plainly the wrong answer to "where are my team on". A mention is only
    // worth showing when there is no fixture to show instead.
    live.soonestFixture(team)?.let { return TeamMatch(it, onNow = true, isFixture = true) }
    upcoming.soonestFixture(team)?.let { return TeamMatch(it, onNow = false, isFixture = true) }

    // Nothing that reads as a fixture, so fall back to what there is: on now
    // first, because a feed dying during something is interrupting that.
    if (live.isNotEmpty()) return TeamMatch(live.best(team), onNow = true, isFixture = false)
    if (upcoming.isEmpty()) return null
    return TeamMatch(upcoming.atSoonestStart().best(team), onNow = false, isFixture = false)
}

/** The soonest of these that reads as a fixture, or null if none does. */
private fun List<Showing>.soonestFixture(team: String): Showing? =
    filter { it.looksLikeFixtureFor(team) }.takeIf { it.isNotEmpty() }
        ?.atSoonestStart()?.best(team)

/** Everything here that starts at the earliest moment any of them does. */
private fun List<Showing>.atSoonestStart(): List<Showing> {
    val soonest = minOf { it.start }
    return filter { it.start == soonest }
}

/**
 * The most likely of these to be the match.
 *
 * A fixture first, then the widest-carried, then the latest to have started
 * — which among things on now is the one most recently joined.
 */
private fun List<Showing>.best(team: String): Showing =
    maxWith(compareBy({ it.looksLikeFixtureFor(team) }, { it.feeds }, { -it.start }))

/**
 * Whether this reads as [team] playing somebody, rather than as a programme
 * that happens to say their name.
 *
 * A guide writes a fixture with the sides either side of a separator, and
 * every provider picks its own: "Croatia v England", "Belgium vs Türkiye",
 * "Nogomet: UEFA Liga nacija (M): Greece - Germany". The last of those is why
 * this is not simply a search for "v": Germany played Greece and the row
 * offered Bundesliga highlights on an Indian film channel instead, because a
 * dash was not a separator as far as this was concerned.
 *
 * **The team has to be one of the sides**, not merely somewhere in the title,
 * and that is what makes a wider set of separators safe. Titles are full of
 * dashes — "UEFA Nations League 2026/27 - Match Day 3" — and slashes, and an
 * earlier version of this rule refused to recognise them for exactly that
 * reason. Asking that the name sit *beside* the separator costs nothing and
 * settles it: "Slavia Prague / Lens" is not a France fixture however often
 * its description says France.
 *
 * The separator must have spaces around it, so "U-Boat Wargamers" is one
 * word and not a fixture, and the sides are read from the raw title because
 * [normaliseForSearch] turns punctuation into spaces and would erase every
 * separator but the lettered ones.
 */
fun Showing.looksLikeFixtureFor(team: String): Boolean {
    val wanted = normaliseForSearch(team)
    if (wanted.isBlank()) return false
    val sides = title.split(FIXTURE_SEPARATOR).filter { it.isNotBlank() }
    if (sides.size < 2) return false
    return sides.any { normaliseForSearch(it).hasWord(wanted) }
}

/** Whether these folded words appear in this folded text as whole words. */
private fun String.hasWord(words: String): Boolean =
    this == words || startsWith("$words ") || endsWith(" $words") || contains(" $words ")

/**
 * What a provider puts between two sides.
 *
 * Spaces either side throughout, which is what keeps hyphenated words out.
 */
private val FIXTURE_SEPARATOR = Regex("""\s+(?:vs?\.?|[-–—/])\s+""", RegexOption.IGNORE_CASE)

/**
 * What a team name may be.
 *
 * Trimmed, spaces collapsed, cut to something a row can draw. Blank means no
 * team, which is how the row is turned off: there is no separate switch to
 * get out of step with the name.
 *
 * Not otherwise cleaned up. "Man Utd" and "Manchester United" are both
 * reasonable things to type and only the provider's own wording decides
 * which finds anything, so second-guessing it here would be guessing on the
 * viewer's behalf about a line this app has never seen.
 */
fun cleanTeamName(raw: String): String =
    raw.trim().replace(Regex("\\s+"), " ").take(MAX_TEAM_NAME)

/** As long a name as a row can show without the channels losing their space. */
const val MAX_TEAM_NAME = 40
