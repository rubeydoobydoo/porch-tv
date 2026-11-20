package tv.porch.app.domain

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgrammeCardTest {
    private val zone = ZoneId.of("Australia/Sydney")

    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    // Tuesday 3 Nov 2026, 1:00 pm.
    private val now = at(3, 13)

    private fun line(start: Long, stop: Long, offset: Int = 0) = ProgrammeCard.whenLine(start, stop, now, offset, zone)

    @Test
    fun theWhenLineIsPlainEnglishForEachState() {
        assertEquals("Live · ends 3:15 pm", line(at(3, 12), at(3, 15, 15)).text)
        assertTrue(line(at(3, 12), at(3, 15, 15)).live)
        assertEquals("Today 7:30 pm · in 6 hours", line(at(3, 19, 30), at(3, 20, 30)).text)
        assertEquals("Today 1:25 pm · in 25 minutes", line(at(3, 13, 25), at(3, 14)).text)
        assertEquals("Tomorrow 7:30 pm", line(at(4, 19, 30), at(4, 20, 30)).text)
        assertEquals("Thu 5 Nov 7:30 pm", line(at(5, 19, 30), at(5, 20, 30)).text)
        assertEquals("Ended 11:30 am", line(at(3, 10), at(3, 11, 30)).text)
        assertEquals("Ended Mon 2 Nov 9:00 pm", line(at(2, 20), at(2, 21)).text)
        // The offset moves the file's times: 1:30 pm in the file with -60 is 12:30 pm on the clock, so it is on now.
        assertEquals("Live · ends 1:30 pm", line(at(3, 13, 30), at(3, 14, 30), offset = -60).text)
    }

    @Test
    fun theActionsFollowTheStateAndCatchup() {
        fun labels(start: Long, stop: Long, catchup: Boolean) =
            ProgrammeCard.actions(start, stop, now, 0, catchup, "Harbour News").map { it.label }
        assertEquals(listOf("Watch now", "Watch from the start"), labels(at(3, 12), at(3, 14), catchup = true))
        assertEquals(listOf("Watch now"), labels(at(3, 12), at(3, 14), catchup = false))
        assertEquals(listOf("Watch Harbour News now"), labels(at(3, 19), at(3, 20), catchup = true))
        assertEquals(listOf("Watch"), labels(at(3, 9), at(3, 10), catchup = true))
        assertEquals(listOf("Watch Harbour News now"), labels(at(3, 9), at(3, 10), catchup = false))
    }

    @Test
    fun aboutSitsAboveTheFavouriteRowAndBelowThePlayersRows() {
        val channel = Channel(
            id = 1, sourceId = 1, name = "Harbour News", number = 7, group = "News", favourite = false,
            streamUrl = "http://example.com/a.ts", xtreamStreamId = null, archiveDays = 0, matchedXmltvId = null, lastPlayedAt = null,
        )
        assertEquals(
            listOf(QuickAction.About, QuickAction.AddFavourite, QuickAction.HideGroup),
            QuickMenu.rows(channel, inPlayer = false, paused = false, hasProgramme = true),
        )
        assertEquals(
            listOf(QuickAction.Pause, QuickAction.Tracks, QuickAction.About, QuickAction.AddFavourite, QuickAction.HideGroup),
            QuickMenu.rows(channel, inPlayer = true, paused = false, hasProgramme = true),
        )
    }
}
