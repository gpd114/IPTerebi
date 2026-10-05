package com.ipterebi.core

/**
 * Being told when something is about to start.
 *
 * The small half of recording. A recording wants the line, stops whatever is
 * playing and fills a stick; a reminder wants nothing at all and costs
 * nothing — it is a note to look up at the right moment, which for a match
 * you intend to watch live is what was actually wanted.
 *
 * So the two are deliberately separate. Booking a recording of a programme
 * you are going to sit and watch is the wrong tool, and on a box with twelve
 * minutes of space it is a worse one.
 */

/** A programme someone wants to be told about. */
@kotlinx.serialization.Serializable
data class Reminder(
    /** Channel and start, so the same programme twice is one reminder. */
    val id: String,
    val streamId: Int,
    val channelName: String,
    val title: String,
    /** When the programme starts, unix seconds. */
    val startSeconds: Long,
    /** When it ends, so one that has been and gone can be cleared out. */
    val stopSeconds: Long,
)

/**
 * A reminder's identity: the channel and the programme's start.
 *
 * The same shape as a recording's id and for the same reason — asking twice,
 * from the guide and then from the team row, is one reminder rather than two
 * notifications about the same match.
 */
fun reminderId(streamId: Int, startSeconds: Long): String = "$streamId@$startSeconds"

/**
 * How long before the start a reminder arrives.
 *
 * Five minutes. At the start it is already late: the thing it is for is
 * putting the right channel on *before* kick-off, and a notification that
 * lands as the whistle goes has missed the point. Long enough to pick up a
 * remote, short enough that nobody forgets why their television is talking
 * to them.
 */
const val REMINDER_LEAD_SECONDS: Long = 5 * 60

/** When a reminder should actually fire. */
val Reminder.dueSeconds: Long get() = startSeconds - REMINDER_LEAD_SECONDS

/**
 * Whether this is still worth setting at [nowSeconds].
 *
 * Something already under way is not: a reminder for it would fire in the
 * past, which Android delivers immediately, so the viewer is told at once
 * about a thing they are presumably already watching.
 */
fun Reminder.worthSetting(nowSeconds: Long): Boolean = dueSeconds > nowSeconds

/**
 * Adds [reminder], or takes it away if it is already set.
 *
 * The same gesture both ways, as favourites and hidden channels are. There
 * is no second control for cancelling, because a reminder is small enough
 * that one is clutter.
 */
fun List<Reminder>.withReminderToggled(reminder: Reminder): List<Reminder> =
    if (any { it.id == reminder.id }) {
        filterNot { it.id == reminder.id }
    } else {
        this + reminder
    }

/** Whether one is set for this programme. */
fun List<Reminder>.holdsReminder(id: String): Boolean = any { it.id == id }

/**
 * The next one to fire, or null when none is left.
 *
 * One alarm at a time, as recording does it: Android gives no prize for
 * holding a hundred, and a single alarm re-armed whenever the list changes
 * cannot drift out of step with it.
 */
fun List<Reminder>.nextReminder(nowSeconds: Long): Reminder? =
    filter { it.dueSeconds > nowSeconds }.minByOrNull { it.dueSeconds }

/**
 * The ones still worth keeping at [nowSeconds].
 *
 * A programme that has finished is dropped. Kept until it finishes rather
 * than until it fires, so a reminder that arrived while the television was
 * off is still visible as a thing that happened for the length of the
 * programme it was about.
 */
fun List<Reminder>.stillToCome(nowSeconds: Long): List<Reminder> =
    filter { it.stopSeconds > nowSeconds }
