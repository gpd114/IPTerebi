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
fun teamMatch(showings: List<Showing>, nowSeconds: Long): TeamMatch? {
    val live = showings.filter { nowSeconds >= it.start && nowSeconds < it.stop }
    val upcoming = showings.filter { it.start > nowSeconds }

    // A fixture outranks a mention whether it is on or not, and that ordering
    // was the other way round at first. "France" found France24's sports
    // bulletin and a motocross championnat on now, both of which merely say
    // the word, and they beat France v Italy that same evening — which is
    // plainly the wrong answer to "where are my team on". A mention is only
    // worth showing when there is no fixture to show instead.
    live.soonestFixture()?.let { return TeamMatch(it, onNow = true) }
    upcoming.soonestFixture()?.let { return TeamMatch(it, onNow = false) }

    // Nothing that reads as a fixture, so fall back to what there is: on now
    // first, because a feed dying during something is interrupting that.
    if (live.isNotEmpty()) return TeamMatch(live.best(), onNow = true)
    if (upcoming.isEmpty()) return null
    return TeamMatch(upcoming.atSoonestStart().best(), onNow = false)
}

/** The soonest of these that reads as a fixture, or null if none does. */
private fun List<Showing>.soonestFixture(): Showing? =
    filter { it.looksLikeFixture }.takeIf { it.isNotEmpty() }?.atSoonestStart()?.best()

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
private fun List<Showing>.best(): Showing =
    maxWith(compareBy({ it.looksLikeFixture }, { it.feeds }, { -it.start }))

/**
 * Whether this reads as one side against another.
 *
 * A guide writes a fixture with the teams either side of a "v" or "vs" —
 * "UEFA Nations League: Croatia v England", "Man United vs Liverpool" — and
 * writes a programme that merely mentions a country without one. It is a
 * weak signal and it is used as one: it only sorts above the feed count, so
 * a widely carried fixture still beats a lone one.
 *
 * The separator has to sit *between* words, which is what keeps "V for
 * Vendetta" from reading as a fixture. That film is not invented: it came up
 * while searching England on a real line.
 */
val Showing.looksLikeFixture: Boolean
    get() {
        val words = normaliseForSearch(title).split(' ')
        val at = words.indexOfFirst { it == "v" || it == "vs" }
        return at > 0 && at < words.lastIndex
    }

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
