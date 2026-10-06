package com.ipterebi.core

/**
 * Matching a second guide's channels to the line's.
 *
 * The provider's own `xmltv.php` is the limit on everything built around the
 * guide, and on a real line it is a thin one: it reached **40 hours** ahead,
 * it named the teams on exactly one channel for a fixture carried by several,
 * and Fox, Sony, Nova and Ziggo all listed the same match as "Live: UEFA
 * Nations League Soccer" with no sides in the title. A second XMLTV source —
 * there are good free ones — fills in teams, descriptions and a week of
 * schedule where the provider gives a day and a half.
 *
 * The hard part is not fetching it. It is deciding which of *their* channels
 * is which of *ours*, and that is what this file is: pure rules over two
 * lists of names, which is the half that can be proven without a device.
 *
 * Two routes, in order of how much they can be trusted:
 *
 * 1. **The id**, where both sides use the same convention. Plenty of
 *    providers copy their ids from the same public sources, so `BBCOne.uk`
 *    on one really is `BBCOne.uk` on the other. Free when it works.
 * 2. **The name**, reduced to what identifies the channel. A provider writes
 *    `UK: BBC ONE LONDON 4K ◉` where a public guide writes `BBC One London`,
 *    and almost everything in the difference is decoration: a country prefix,
 *    a quality, a mark this app itself added. [guideKey] strips those.
 *
 * What it will not do is guess. A name that reduces to something two of the
 * line's channels share is left alone rather than matched to one of them at
 * random, because a guide attached to the wrong channel is worse than no
 * guide: it is wrong with confidence, on a screen built to be trusted.
 */

/**
 * A channel's name reduced to what identifies it.
 *
 * Everything a provider decorates with comes off: the country or language
 * prefix before a colon, the quality, the marks. What is left is the name a
 * public guide would use.
 *
 * Deliberately not clever. It does not expand abbreviations or know that
 * "Sports" and "Sport" are the same word, because those are judgements about
 * a particular provider's habits and this has seen one line.
 */
fun guideKey(name: String): String {
    // "UK: BBC ONE LONDON 4K" -> "BBC ONE LONDON 4K". Only the first colon,
    // and only when what precedes it is short: "Match of the Day: Final" is a
    // programme-shaped name whose first half is the part that matters.
    val withoutPrefix = name.substringAfter(':', name).let {
        if (it !== name && name.substringBefore(':').length <= PREFIX_MAX) it else name
    }
    // An ampersand is the word "and", and the two sides spell it
    // differently: this provider writes "ENGLISH: AND FLIX" where a public
    // guide writes "&flix". Folding punctuation away first would leave
    // "flix" against "and flix", and the two would never meet.
    return normaliseForSearch(withoutPrefix.replace("&", " and "))
        .split(' ')
        .filter { it.isNotBlank() && it !in DECORATION }
        .joinToString(" ")
}

/** How long a prefix before a colon may be and still read as a country or language. */
private const val PREFIX_MAX = 12

/**
 * Words that say how a channel is delivered rather than which channel it is.
 *
 * Folded already, so lower case and no punctuation. These come off both
 * sides, so a provider's `BBC ONE HD` and a public guide's `BBC One` meet in
 * the middle.
 */
private val DECORATION = setOf(
    "4k", "uhd", "fhd", "hd", "sd", "hq", "lq",
    "hevc", "h265", "h264", "raw", "fps", "60fps", "50fps",
    "backup", "alt", "vip", "plus", // plus only as a trailing word; see the test
)

/**
 * Which of the line's `epg_channel_id`s each of [theirs] belongs to.
 *
 * [ours] is the line's channels. The answer maps *their* channel id to every
 * one of our guide ids it matches, because several of our streams share a
 * guide id and several guide ids can carry the same channel.
 *
 * Only unambiguous matches are returned. Where a reduced name belongs to more
 * than one of our guide ids, it is dropped: see the note at the top about
 * guessing.
 *
 * And each of ours is claimed once. A public guide carries the same channel
 * twice -- an Irish source lists "Sky Sports Premier League" beside "Sky
 * Sports Premier League HD" -- and mapping both onto our one channel wrote
 * its evening twice.
 */
fun matchGuideChannels(
    ours: List<LiveStream>,
    theirs: List<XmltvChannel>,
): Map<String, String> {
    val byId = HashMap<String, String>()
    val byKey = HashMap<String, MutableSet<String>>()
    for (channel in ours) {
        val epg = channel.epgChannelId
        if (epg.isBlank()) continue
        byId[epg.lowercase()] = epg
        val key = guideKey(channel.name)
        if (key.isNotBlank()) byKey.getOrPut(key) { mutableSetOf() }.add(epg)
    }

    // One of theirs per one of ours, which is a different question from the
    // one above and was found on a real line. A public guide carries the same
    // channel twice -- the Irish source lists "Sky Sports Premier League" and
    // "Sky Sports Premier League HD" as two channels -- and both reduce to the
    // same name, so both were matched to our one channel and both schedules
    // were written. The box showed every programme twice.
    //
    // Deduplicated rather than refused, and the difference matters. Two of
    // *ours* sharing a name is a question nobody can answer: we do not know
    // which channel the guide belongs to, so it is dropped. Two of *theirs*
    // is not ambiguous at all -- it is one channel listed twice, carrying the
    // same programmes -- so the second claimant is simply ignored.
    val matched = HashMap<String, String>()
    val claimed = HashSet<String>()

    // Ids first, as a pass of its own. An id match is the stronger of the
    // two, so it must not lose our channel to something that merely had the
    // right name and came earlier in the document.
    for (one in theirs) {
        val sameId = byId[one.id.lowercase()] ?: continue
        if (claimed.add(sameId)) matched[one.id] = sameId
    }
    for (one in theirs) {
        if (one.id in matched) continue
        val key = guideKey(one.name)
        if (key.isBlank()) continue
        // Exactly one, or none. Two of ours reducing to the same name is the
        // case this refuses to decide.
        val ours1 = byKey[key] ?: continue
        if (ours1.size != 1) continue
        val mine = ours1.first()
        if (claimed.add(mine)) matched[one.id] = mine
    }
    return matched
}

/**
 * Whether a second guide's programme is worth keeping for [epgChannelId].
 *
 * Only where the provider's own guide says nothing for that channel in that
 * stretch of time. The provider is closer to what it is actually
 * broadcasting, so where the two disagree the provider wins; the second
 * source is there to fill the silence, which on a real line is most of it.
 *
 * [oursFor] is what we already hold for that channel, in start order.
 */
fun worthKeeping(
    theirs: XmltvProgramme,
    oursFor: List<XmltvProgramme>,
): Boolean = oursFor.none { it.start < theirs.stop && theirs.start < it.stop }

/**
 * How far the provider's own guide reaches for one channel.
 *
 * Two numbers per channel rather than every programme it holds: a real line
 * is a hundred thousand programmes and the question being asked of them is
 * only "does the provider already cover this moment".
 */
data class GuideSpan(val earliest: Long, val latest: Long) {
    /** A channel the provider said nothing about at all. */
    val empty: Boolean get() = earliest > latest
}

/** The span of nothing, which every programme falls outside. */
val NO_SPAN = GuideSpan(Long.MAX_VALUE, Long.MIN_VALUE)

/** [GuideSpan] widened to include [programme]. */
fun GuideSpan.plus(programme: XmltvProgramme): GuideSpan = GuideSpan(
    earliest = minOf(earliest, programme.start),
    latest = maxOf(latest, programme.stop),
)

/**
 * Whether a second source's programme is worth keeping against [ours].
 *
 * Outside what the provider gave, or on a channel it gave nothing for. Two
 * cases, and they are the two that matter:
 *
 * - **A channel with no guide at all.** The ordinary case: a line carried
 *   21,077 channels and the provider's `xmltv.php` covered 1,366 of them.
 * - **Beyond the provider's horizon.** It published 40 hours one day and 17
 *   the next, while keeping seven days of recordings — so the far end is
 *   where a second source earns its place.
 *
 * What it will *not* do is fill a hole inside the provider's own stretch.
 * The provider is closer to what it is actually broadcasting, a gap there is
 * usually a junction rather than a mistake, and two sources interleaved
 * across one evening is a guide nobody can read.
 */
fun worthKeepingBeyond(theirs: XmltvProgramme, ours: GuideSpan): Boolean =
    ours.empty || theirs.stop <= ours.earliest || theirs.start >= ours.latest

/**
 * How many extra sources may be set.
 *
 * Not a round number for its own sake. The owner's reason for wanting a
 * second guide at all is the Premier League: a UK sports source for the
 * matches shown here, and at least one foreign source for the three o'clock
 * Saturday games, which are not broadcast in the UK and so are listed only
 * where they are. That is two, and a third and fourth leave room for another
 * country without turning a guide refresh into a download of everything
 * anyone has ever published. Each one is tens of megabytes and they are
 * fetched together.
 */
const val MAX_GUIDE_SOURCES = 4

/**
 * The XMLTV sources in a typed block of text, tidied.
 *
 * One per line is what the field invites, but people paste, so commas and
 * spaces separate them too — neither can occur inside a URL, so splitting on
 * them cannot break one.
 *
 * Blank is how extra sources are turned off, as blank is how the team row is.
 * Anything that is not URL-shaped is dropped rather than kept to fail later:
 * a guide refresh happens in the background, hours after the typing, and an
 * error there has nowhere to be seen.
 */
fun cleanGuideSources(raw: String): List<String> =
    raw.split('\n', '\r', '\t', ' ', ',', ';')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { withScheme(it) }
        .filter { looksLikeUrl(it) }
        .distinct()
        .take(MAX_GUIDE_SOURCES)

/**
 * **A guide source defaults to https, where a panel defaults to http.**
 *
 * That looks like an inconsistency and is the opposite. Panels are
 * overwhelmingly plain HTTP on a high port, and assuming https there fails at
 * the handshake with an error naming TLS, which sends you looking in the
 * wrong place. A public guide is an ordinary website on an ordinary port,
 * served by people who have had a certificate for years, and assuming http
 * there earns a redirect at best.
 */
private fun withScheme(candidate: String): String =
    if (candidate.contains("://")) candidate else "https://$candidate"

private fun looksLikeUrl(candidate: String): Boolean {
    if (!candidate.startsWith("http://", ignoreCase = true) &&
        !candidate.startsWith("https://", ignoreCase = true)
    ) return false
    val authority = candidate.substringAfter("://")
        .substringBefore('/').substringBefore('?').substringBefore('#')
    // A dot or a port: a bare word is a typo, not a host. `localhost:8080`
    // passes on the port, which is what a source served off the same machine
    // as a test panel looks like.
    return '.' in authority || ':' in authority
}

/**
 * A source named short enough to log and to show.
 *
 * **It drops the query, and that is the point as much as the length.** Some
 * publishers put a subscriber token in it, and a token in a log is the same
 * mistake as a stream URL in a log — see `String.withoutCredentialValues`.
 * The host and the file are what identify a source to the person who typed
 * it; nothing else is anyone's business.
 */
fun guideSourceLabel(url: String): String {
    val afterScheme = url.substringAfter("://", url)
    val host = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
    val file = afterScheme.substringAfter('/', "")
        .substringBefore('?').substringBefore('#')
        .trimEnd('/').substringAfterLast('/')
    return if (file.isBlank()) host else "$host/$file"
}
