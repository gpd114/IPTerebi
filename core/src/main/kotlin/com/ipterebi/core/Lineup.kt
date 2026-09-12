package com.ipterebi.core

/**
 * A line's channels arranged the way a television remote uses them: grouped by
 * category, numbered, and stepped through with channel up and down.
 *
 * Built from one request for every channel on the line. The phone app loads a
 * category at a time to avoid exactly that request, but a set-top box needs
 * the whole list to do its job — number keys reach any channel, and zapping
 * crosses nothing to fetch — and a television on the mains can afford it once.
 */
class Lineup(channels: List<LiveStream>, categories: List<XtreamCategory>) {

    /** Every channel, in the panel's order: the "All channels" group. */
    val all: List<LiveStream> = channels

    /**
     * The panel's categories that hold anything, in the panel's order, then
     * [OTHER_GROUP_ID] for channels whose category the panel never named —
     * which happens, and those channels would otherwise be unreachable.
     */
    val groups: List<ChannelGroup>

    private val byId: Map<Int, LiveStream> = channels.associateBy { it.streamId }
    private val groupsById: Map<String, ChannelGroup>

    /**
     * The provider's own numbers where they can be trusted to mean one channel
     * each: every channel has one and no two share one. Otherwise positions in
     * [all] from 1, the way a playlist numbers them. Mixing the two would give
     * a line where number 12 is two channels, or none.
     */
    private val numbers: Map<Int, Int>
    private val byNumber: Map<Int, LiveStream>

    init {
        val inCategory = channels.groupBy { it.categoryId }
        val named = categories.filter { !inCategory[it.id].isNullOrEmpty() }
            .map { ChannelGroup(it.id, it.name.ifBlank { "Unnamed group" }, inCategory.getValue(it.id)) }
        val namedIds = named.mapTo(HashSet()) { it.id }
        val unnamed = channels.filter { it.categoryId !in namedIds }
        groups = if (unnamed.isEmpty()) named else named + ChannelGroup(OTHER_GROUP_ID, "Other channels", unnamed)
        groupsById = groups.associateBy { it.id }

        val providers = channels.map { it.number }
        val trusted = providers.all { it > 0 } && providers.toSet().size == providers.size
        numbers = channels.withIndex().associate { (i, c) -> c.streamId to if (trusted) c.number else i + 1 }
        byNumber = channels.associateBy { numbers.getValue(it.streamId) }
    }

    fun find(streamId: Int): LiveStream? = byId[streamId]

    /** The group with this id, or null when the line has no such group — any more. */
    fun group(id: String): ChannelGroup? = groupsById[id]

    /** What to show beside a channel and type to reach it. 0 for a channel not on this line. */
    fun numberOf(streamId: Int): Int = numbers[streamId] ?: 0

    fun byNumber(number: Int): LiveStream? = byNumber[number]

    companion object {
        /** The group for channels in no category the panel listed. Not a panel id: it has a space. */
        const val OTHER_GROUP_ID = " other"
    }
}

/** One category's channels, in the panel's order. */
data class ChannelGroup(val id: String, val name: String, val channels: List<LiveStream>)

/**
 * The channel [step] places along from [fromId] — +1 for channel up — wrapping
 * at either end as a remote does. From a channel not in the list (it was
 * chosen from a different one), up starts at the top and down at the bottom.
 * Null only for an empty list.
 */
fun List<LiveStream>.zap(fromId: Int, step: Int): LiveStream? {
    if (isEmpty()) return null
    val here = indexOfFirst { it.streamId == fromId }
    if (here < 0) return if (step > 0) first() else last()
    return this[(here + step).mod(size)]
}

/**
 * Digits typed on a remote to reach a channel by number. Up to four — no
 * line runs to ten thousand channels, and a fifth press starts again rather
 * than silently dropping the first digit.
 */
fun typedChannelNumber(sofar: String, digit: Char): String =
    if (sofar.length >= MAX_CHANNEL_DIGITS) "$digit" else sofar + digit

const val MAX_CHANNEL_DIGITS = 4
