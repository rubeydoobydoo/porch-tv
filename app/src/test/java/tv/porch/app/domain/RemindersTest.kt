package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemindersTest {
    private val minute = 60_000L
    private val now = 1_800_000_000_000L

    private fun reminder(startsIn: Long, channelId: Long = 1, title: String = "Harbour Derby") =
        Reminder(sourceId = 1, identity = "xt:$channelId", title = title, startMs = now + startsIn * minute, stopMs = now + (startsIn + 90) * minute)

    @Test
    fun dueRunsFromFiveMinutesBeforeStartToFifteenAfterAndEarliestFirst() {
        val tooEarly = reminder(5).copy(startMs = now + 5 * minute + 1)
        val atLead = reminder(5, channelId = 2)
        val late = reminder(-15, channelId = 3)
        val tooLate = reminder(-15, channelId = 4).copy(startMs = now - 15 * minute - 1)
        val soon = reminder(1, channelId = 5)
        assertEquals(listOf(late, soon, atLead), Reminders.due(listOf(atLead, tooEarly, soon, tooLate, late), now))
    }

    @Test
    fun pruneDropsOnlyThoseMoreThanFifteenMinutesPastStartAndTheSameProgrammeIsOneReminder() {
        val old = reminder(-16, channelId = 1)
        val running = reminder(-15, channelId = 2)
        val later = reminder(120, channelId = 3)
        assertEquals(listOf(running, later), Reminders.prune(listOf(old, running, later), now))
        val once = Reminders.add(listOf(later), later.copy(title = "Harbour Derby (live)"))
        assertEquals(1, once.size)
        assertEquals(2, Reminders.add(once, reminder(120, channelId = 9)).size)
    }

    @Test
    fun aReminderNeedsMoreThanFiveMinutesToGo() {
        assertTrue(Reminders.canRemind(now + 5 * minute + 1, now))
        assertFalse(Reminders.canRemind(now + 5 * minute, now))
        assertFalse(Reminders.canRemind(now - minute, now))
    }

    @Test
    fun aProgrammeThatHasntStartedLeadsWithTheReminderPillUnlessItIsWithinFiveMinutes() {
        fun labels(startsIn: Long, reminderOn: Boolean?) =
            ProgrammeCard.actions(now + startsIn * minute, now + (startsIn + 60) * minute, now, 0, false, "Harbour News", reminderOn).map { it.label }
        assertEquals(listOf("Remind me", "Watch Harbour News now"), labels(30, reminderOn = false))
        assertEquals(listOf("Reminder on", "Watch Harbour News now"), labels(30, reminderOn = true))
        assertTrue(ProgrammeCard.actions(now + 30 * minute, now + 90 * minute, now, 0, false, "Harbour News", true).first().chosen)
        assertEquals(listOf("Watch Harbour News now"), labels(4, reminderOn = false))
        assertEquals(listOf("Watch now"), labels(-10, reminderOn = false))
    }

    @Test
    fun aReminderIsFoundByChannelIdentityNotByIdSoARefreshKeepsIt() {
        val saved = listOf(reminder(60, channelId = 7))
        // After a refresh the channel has a new id but the same source and identity.
        assertTrue(Reminders.has(saved, sourceId = 1, identity = "xt:7", startMs = saved[0].startMs))
        assertFalse(Reminders.has(saved, sourceId = 2, identity = "xt:7", startMs = saved[0].startMs))
        assertFalse(Reminders.has(saved, sourceId = 1, identity = "xt:8", startMs = saved[0].startMs))
    }
}
