package com.ipterebi.core

/**
 * Channels the viewer never wants to see again.
 *
 * A real line answered 21,077 channels, filed into dozens of categories called
 * things like `EN - 2020 & OLD`. Most of them are in languages nobody in the
 * house speaks, or are the same channel for the fourth time. Favourites says
 * what to keep close; this says what to stop showing at all, which is the other
 * half of making a provider's list usable and the half that shrinks it.
 *
 * **Stored as whole records, not as ids.** The first version kept ids alone, on
 * the grounds that a hidden channel is never drawn so there is nothing to
 * remember but which one it is. That is wrong, and wrong in a way that would
 * have shipped: hiding has to be undoable, the screen that undoes it has to
 * draw a name, and the only place that name lives is the list the channel has
 * just been taken out of. So it carries its name with it, the same bargain
 * favourites make.
 *
 * Matching is still on [LiveStream.streamId] alone, so a provider renaming a
 * channel overnight does not un-hide it.
 *
 * Per line, like everything keyed on a panel's ids. Id 4271 on one provider is
 * not id 4271 on another, so a hidden list carried across would blank out
 * something innocent.
 */

/** The ids to filter by, which is all the filtering needs. */
fun List<LiveStream>.hiddenIds(): Set<Int> = mapTo(mutableSetOf()) { it.streamId }

/**
 * [channels] with the hidden ones gone.
 *
 * Applied everywhere channels are listed rather than only on the list screen:
 * a channel still turning up in search, in the guide, or under the zap keys is
 * one that has not been hidden, whatever the list says. Returns the same list
 * when nothing is hidden, which is the common case and worth not copying a
 * line's worth of channels for.
 */
fun List<LiveStream>.withoutHidden(hidden: Set<Int>): List<LiveStream> =
    if (hidden.isEmpty()) this else filterNot { hidden.contains(it.streamId) }

/**
 * Hides [channel] if it is shown, shows it if it is hidden.
 *
 * Newest first, unlike favourites: this list is only ever read by the screen
 * that undoes it, and the thing most likely to want undoing is the one just
 * hidden by accident.
 */
fun List<LiveStream>.withHiddenToggled(channel: LiveStream): List<LiveStream> =
    if (any { it.streamId == channel.streamId }) {
        filterNot { it.streamId == channel.streamId }
    } else {
        listOf(channel) + this
    }

/**
 * The hidden channels this line still has, and the ones it no longer does.
 *
 * Worth clearing out when the whole line has been seen: a provider that drops
 * or renumbers channels would otherwise leave a list that grows for ever and,
 * worse, might one day hide something new that was given an old id. Only safe
 * to call with every channel on the line, never with one category.
 */
fun List<LiveStream>.stillOnLine(allChannels: List<LiveStream>): List<LiveStream> {
    if (isEmpty()) return this
    val live = allChannels.mapTo(mutableSetOf()) { it.streamId }
    return filter { live.contains(it.streamId) }
}
