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
    if (live.isNotEmpty()) {
        return TeamMatch(live.maxWith(compareBy({ it.feeds }, { -it.start })), onNow = true)
    }
    val next = showings.filter { it.start > nowSeconds }.minByOrNull { it.start } ?: return null
    // Everything starting at that same moment, so the widest-carried of them
    // is chosen rather than whichever happened to be first in the list.
    val together = showings.filter { it.start == next.start }
    return TeamMatch(together.maxByOrNull { it.feeds } ?: next, onNow = false)
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
