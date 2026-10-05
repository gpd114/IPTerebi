package com.ipterebi.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reminders. Each of these is a way to be told the wrong thing, or at the
 * wrong moment, or twice.
 */
class RemindersTest {

    private val now = 1_000_000L

    private fun reminder(stream: Int, start: Long, title: String = "The match") = Reminder(
        id = reminderId(stream, start),
        streamId = stream,
        channelName = "Channel $stream",
        title = title,
        startSeconds = start,
        stopSeconds = start + 7200,
    )

    @Test
    fun `it arrives before the start, not at it`() {
        // The point is to get the right channel on before kick-off. A
        // notification that lands as the whistle goes has missed it.
        val match = reminder(1, now + 3600)
        assertEquals(now + 3600 - REMINDER_LEAD_SECONDS, match.dueSeconds)
        assertTrue(match.dueSeconds < match.startSeconds)
    }

    @Test
    fun `something already under way is not worth setting`() {
        // Its moment would be in the past, and Android delivers a past alarm
        // at once — so the viewer is told immediately about the thing they
        // are presumably already watching.
        assertFalse(reminder(1, now - 60).worthSetting(now))
        assertFalse(reminder(1, now).worthSetting(now))
    }

    @Test
    fun `one inside the lead time is also too late`() {
        // Starting in four minutes, with a five minute lead: the alarm would
        // already be due.
        assertFalse(reminder(1, now + 4 * 60).worthSetting(now))
        assertTrue(reminder(1, now + 6 * 60).worthSetting(now))
    }

    @Test
    fun `asking twice is one reminder`() {
        // From the guide and then from the team row, say. Two notifications
        // about one match is the thing to avoid.
        val match = reminder(1, now + 3600)
        val once = emptyList<Reminder>().withReminderToggled(match)
        assertEquals(1, once.size)
        assertTrue(once.holdsReminder(match.id))
    }

    @Test
    fun `the same gesture takes it away again`() {
        val match = reminder(1, now + 3600)
        val set = emptyList<Reminder>().withReminderToggled(match)
        assertTrue(set.withReminderToggled(match).isEmpty())
    }

    @Test
    fun `the same programme on another channel is another reminder`() {
        // A match on two feeds is two programmes as far as this is concerned,
        // because the reminder has to name a channel to put on.
        val set = emptyList<Reminder>()
            .withReminderToggled(reminder(1, now + 3600))
            .withReminderToggled(reminder(2, now + 3600))
        assertEquals(2, set.size)
    }

    @Test
    fun `the next to fire is the soonest still ahead`() {
        val set = listOf(
            reminder(1, now + 7200, "Later"),
            reminder(2, now + 3600, "Sooner"),
        )
        assertEquals("Sooner", assertNotNullReminder(set.nextReminder(now)).title)
    }

    @Test
    fun `one already due is not the next to fire`() {
        // It has had its moment; arming for it would fire at once.
        val set = listOf(reminder(1, now - 3600), reminder(2, now + 3600, "Ahead"))
        assertEquals("Ahead", assertNotNullReminder(set.nextReminder(now)).title)
    }

    @Test
    fun `nothing ahead is nothing to arm`() {
        assertNull(listOf(reminder(1, now - 3600)).nextReminder(now))
        assertNull(emptyList<Reminder>().nextReminder(now))
    }

    @Test
    fun `one is kept until the programme ends, not until it fires`() {
        // So a reminder that arrived while the television was off is still
        // there as a thing that happened, for as long as it is any use.
        val onNow = reminder(1, now - 600)
        assertEquals(1, listOf(onNow).stillToCome(now).size)
        assertTrue(listOf(reminder(1, now - 7300)).stillToCome(now).isEmpty())
    }

    @Test
    fun `an id is the channel and the start`() {
        assertEquals("104@1000", reminderId(104, 1000))
        assertEquals(reminderId(1, now), reminder(1, now).id)
    }

    private fun assertNotNullReminder(r: Reminder?): Reminder {
        assertTrue(r != null, "expected a reminder")
        return r
    }
}
