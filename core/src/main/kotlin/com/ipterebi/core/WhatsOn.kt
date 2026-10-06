package com.ipterebi.core

/**
 * Finding a programme, and every channel carrying it.
 *
 * A line usually allows one stream, so when a feed buffers or dies the only
 * move left is another channel showing the same thing. On a line of 21,077
 * channels filed into categories like `EN - 2020 & OLD`, finding that by
 * browsing is hopeless. The guide already on the device knows the answer.
 *
 * Every rule here was settled by measuring one real line's `xmltv.php` —
 * 100,192 programmes across 1,366 channels — rather than guessed, and the
 * measurements are worth keeping because each one killed a simpler design:
 *
 * **The description matters as much as the title.** Croatia v England was on
 * seven feeds, and five of them titled it only "Nations League"; the teams
 * were in the description. Searching titles alone found two of the seven.
 *
 * **Grouping by identical title is wrong**, which was the first design. The
 * same fixture arrived titled three ways — "UEFA Nations League: Croatia v
 * England", "Nations League", "Kick Off - Croatia v England" — so exact
 * titles split one event into three. Worse in the other direction: `Live:
 * College Football` was on 127 channels, a generic slot name covering
 * *different games* on different affiliates, so exact titles also glue
 * unrelated matches into one.
 *
 * So an event is a time and a *compatible* title; see `Event.takes` below.
 *
 * **Noise is ranked down, not filtered out.** Searching "England" also finds
 * darts, two cricket ODIs, "7 News Today in New England" and "Out of
 * England". No word-boundary rule helps — England is a whole word in "New
 * England" — and a football database is not something this app is going to
 * carry. What separates them is how many channels carry it: the match is on
 * seven, the news programme on one. [showings] orders by that, so the thing
 * being looked for comes first and the viewer picks from a short list rather
 * than the app pretending to know.
 */

/** A programme, and every channel carrying it. */
data class Showing(
    /** The most specific title any of the channels gave it. */
    val title: String,
    val start: Long,
    val stop: Long,
    /**
     * The `epg_channel_id`s carrying it, folded to lower case and
     * de-duplicated. Folded because a real line sends them inconsistently —
     * `SkySport3.nz` and `skysport3.nz`, `SkySportsCricket.uk` and
     * `skysportscricket.uk` — and the same channel listed twice is a worse
     * answer than one listed once.
     */
    val channels: List<String>,
    /**
     * Every channel carrying it says it has been on before.
     *
     * All of them, not any: a fixture carried live by one channel and as a
     * replay by another is still being played somewhere, and that is the one
     * worth switching to. Most guides say nothing either way, so this is
     * false far more often than a schedule is actually live.
     */
    val repeat: Boolean = false,
) {
    /** How many channels carry it, which is the whole point of the screen. */
    val feeds: Int get() = channels.size
}

/**
 * How far apart two starts may be and still be one event.
 *
 * The seven feeds of the same match all began at exactly 15:15, while
 * talksport's pre-match show began at 14:30 and ran across it. A quarter of
 * an hour keeps a backup feed that starts a few minutes adrift and rejects
 * the preview programme, which is a different thing however much it overlaps.
 */
const val SAME_EVENT_SLACK_SECONDS: Long = 15 * 60

/**
 * The programmes mentioning every word of [term], in a title or description.
 *
 * Each word of the term has to be the start of some word of the programme's
 * text, so typing "engl" finds England before the name is finished while
 * "eng" does not match "Boxing". Case and accents are folded by
 * [normaliseForSearch], so "Koln" finds "Köln".
 *
 * A blank term finds nothing, deliberately — unlike a channel-name search,
 * where everything is a reasonable answer. Here everything is a hundred
 * thousand programmes.
 */
fun List<XmltvProgramme>.mentioning(term: String): List<XmltvProgramme> {
    val words = searchWords(term)
    if (words.isEmpty()) return emptyList()
    return filter { words.matchedBy(searchText(it)) }
}

/**
 * A window of guide, ready to be searched over and over.
 *
 * The whole searchable window is 12,584 programmes and 1.8 MB of text on a
 * real line — small enough to hold, far too much to normalise again on every
 * keystroke. That lesson is already written down for channel names, where
 * re-normalising tens of thousands of them per keypress was the slow part, so
 * this is the same shape as [NameIndex]: the text is folded once when the
 * index is built and each search is then a scan of ready strings.
 *
 * The folded text is kept as one string per programme rather than a list of
 * words, because a list of a hundred thousand short strings costs more than
 * the text it came from and a prefix-of-a-word test needs no splitting.
 */
class WhatsOnIndex(programmes: List<XmltvProgramme>) {

    private val entries: List<Pair<XmltvProgramme, String>> =
        programmes.map { it to searchText(it) }

    val size: Int get() = entries.size

    /** The events mentioning [term], most widely carried first. */
    fun search(term: String): List<Showing> {
        val words = searchWords(term)
        if (words.isEmpty()) return emptyList()
        return entries
            .mapNotNull { (programme, text) -> programme.takeIf { words.matchedBy(text) } }
            .showings()
    }
}

/** A programme's title and description, folded once for searching. */
private fun searchText(programme: XmltvProgramme): String =
    normaliseForSearch(programme.title + " " + programme.description)

/** The words a search term asks for, folded the same way. */
private fun searchWords(term: String): List<String> =
    normaliseForSearch(term).split(' ').filter { it.isNotEmpty() }

/**
 * Whether [text] has a word starting with each of these.
 *
 * A word matches at the start of the folded text or after a space, which is
 * what makes "engl" find England while "eng" does not match "Boxing" — and
 * it does it without splitting the text, because this runs over every
 * programme in the window on every keystroke.
 */
private fun List<String>.matchedBy(text: String): Boolean =
    all { word -> text.startsWith(word) || text.contains(" $word") }

/**
 * Gathers programmes into events, most widely carried first.
 *
 * Within one event the channels keep the order they arrived in, so a
 * provider's own ordering survives; between events the count decides, which
 * is what sinks a one-channel coincidence below the match on seven. Equal
 * counts go by start time, so what is on soonest is nearer the top.
 */
fun List<XmltvProgramme>.showings(): List<Showing> {
    // Folded once per programme, never inside the comparison.
    //
    // The first version normalised both titles on every comparison and
    // compared each programme against every event so far. On the fake
    // panel's twenty channels that is instant; on a real line it never
    // finished. A window around one programme held **3,163 rows across 160
    // start times**, which is a quarter of a million comparisons and half a
    // million NFD normalisations, and the box simply sat there — no error, no
    // card, nothing in the log after "looking for". This is the same lesson
    // [NameIndex] already carries for channel names, learnt again one layer
    // down.
    val folded = sortedBy { it.start }.map { Folded(it, normaliseForSearch(it.title)) }

    val events = mutableListOf<Event>()
    // Everything before this has a start too far back to match again, because
    // the programmes are in start order. Without it the scan is quadratic.
    var live = 0
    for (one in folded) {
        while (live < events.size && one.programme.start - events[live].start > SAME_EVENT_SLACK_SECONDS) {
            live++
        }
        var joined: Event? = null
        for (index in live until events.size) {
            if (events[index].takes(one)) {
                joined = events[index]
                break
            }
        }
        if (joined != null) joined.members.add(one.programme)
        else events.add(Event(one))
    }

    return events
        .map { event ->
            Showing(
                // The longest title is the most specific one. Not the
                // commonest: five channels said "Nations League" and two gave
                // the fixture, so a majority would pick the vague one.
                title = event.members.maxByOrNull { it.title.length }?.title.orEmpty(),
                start = event.start,
                stop = event.members.maxOf { it.stop },
                channels = event.members.map { it.channel.lowercase() }.distinct(),
                repeat = event.members.all { it.repeat },
            )
        }
        .sortedWith(compareByDescending<Showing> { it.feeds }.thenBy { it.start })
}

/** A programme with its title folded, so the folding happens once. */
private class Folded(val programme: XmltvProgramme, val title: String) {
    /** Words worth matching on; see [Event.takes]. */
    val words: List<String> = title.split(' ').filter { it.length > 2 }
}

/** Programmes gathered as one event, keyed on the first one seen. */
private class Event(first: Folded) {
    val members = mutableListOf(first.programme)
    private val head = first
    val start: Long get() = head.programme.start

    /**
     * Whether [other] is this same event on another channel.
     *
     * Time alone would make everything starting at 15:15 one event. A title
     * alone splits a fixture titled three ways. So: the starts are close, the
     * windows overlap, and the shorter title's real words all appear in the
     * longer one — "Nations League" inside "UEFA Nations League: Croatia v
     * England". Words of one or two letters are skipped, because a title made
     * only of them would otherwise glue to anything; where a title has no
     * longer word, the two have to read the same.
     */
    fun takes(other: Folded): Boolean {
        val a = head.programme
        val b = other.programme
        if (kotlin.math.abs(a.start - b.start) > SAME_EVENT_SLACK_SECONDS) return false
        if (a.start >= b.stop || b.start >= a.stop) return false
        if (head.words.isEmpty() || other.words.isEmpty()) return head.title == other.title
        val (shorter, longer) =
            if (head.words.size <= other.words.size) head.words to other.words
            else other.words to head.words
        return shorter.all { longer.contains(it) }
    }
}

/**
 * The whole event [playing] belongs to, its own channel included.
 *
 * [candidates] is whatever the guide holds around the same time, the playing
 * channel's own row included or not.
 *
 * Its own channel is kept, and that is deliberate rather than laziness. A
 * provider carries ITV1 as the HD cut, the FHD cut and a backup, all three
 * under the one `epg_channel_id` — so when the feed being watched dies, the
 * best thing to switch to is very often another stream on the *same* guide
 * channel. Dropping the channel here would hide exactly those. What has to
 * be left out is the one stream that just failed, and only the caller knows
 * which stream that is; see [streams].
 */
fun showingOf(playing: XmltvProgramme, candidates: List<XmltvProgramme>): Showing? {
    val here = playing.channel.lowercase()
    return (listOf(playing) + candidates).showings().firstOrNull { it.channels.contains(here) }
}

/**
 * The other *guide channels* showing what [playing] is showing.
 *
 * [showingOf] without the channel asked about — the answer to "who else has
 * this", as opposed to "what can I press instead".
 */
fun alsoShowing(playing: XmltvProgramme, candidates: List<XmltvProgramme>): Showing? {
    val here = playing.channel.lowercase()
    val event = showingOf(playing, candidates) ?: return null
    val others = event.channels.filterNot { it == here }
    return if (others.isEmpty()) null else event.copy(channels = others)
}

/**
 * The streams a [Showing] can actually be watched on.
 *
 * Several streams share one `epg_channel_id` — the HD cut, the backup, the
 * 4K one — so a showing on seven guide channels is usually more than seven
 * things to press. That multiplication is the feature working, not a fault.
 *
 * [channels] must already have the hidden ones taken out; a channel the
 * viewer has hidden is not an answer to anything.
 */
fun Showing.streams(channels: List<LiveStream>): List<LiveStream> {
    val wanted = this.channels.toSet()
    val byChannel = channels
        .filter { it.epgChannelId.isNotBlank() && wanted.contains(it.epgChannelId.lowercase()) }
        .groupBy { it.epgChannelId.lowercase() }
    // In the showing's own channel order, so the list does not reshuffle
    // itself between one search and the next.
    return this.channels.flatMap { byChannel[it].orEmpty() }
}

