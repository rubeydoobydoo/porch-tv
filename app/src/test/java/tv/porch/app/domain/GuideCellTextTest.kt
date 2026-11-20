package tv.porch.app.domain

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideCellTextTest {
    private val zone = ZoneId.of("UTC")

    // 2026-01-05 20:00 UTC and 21:00 UTC, in epoch millis.
    private val start = 1_767_643_200_000L
    private val stop = start + 3_600_000L

    @Test
    fun statusLinesForLivePastAndFuture() {
        val live = start + 42 * 60_000L
        assertEquals("On now  ·  ends 9:00 pm  ·  OK plays", GuideCellText.status(start, stop, live, 0, catchup = false, zone = zone))
        assertEquals("Starts 8:00 pm", GuideCellText.status(start, stop, start - 3_600_000L, 0, catchup = false, zone = zone))
        // A guide that runs an hour early (offset +60) puts the same programme an hour later.
        assertEquals("Starts 9:00 pm", GuideCellText.status(start, stop, start - 3_600_000L, 60, catchup = false, zone = zone))
        val after = stop + 10 * 60_000L
        assertEquals(
            "Ended 9:00 pm  ·  Catch-up available  ·  OK plays it from the start",
            GuideCellText.status(start, stop, after, 0, catchup = true, zone = zone),
        )
        assertEquals("Ended 9:00 pm  ·  No catch-up on this channel", GuideCellText.status(start, stop, after, 0, catchup = false, zone = zone))
    }

    @Test
    fun heroSaysNothingWrongWhileProgrammesLoad() {
        // Matched channel, no programme yet, still fetching: the channel's own name and a blank status.
        assertEquals("Example One", GuideHeroText.title(null, matched = true, programmesLoaded = false, channelListTitle = "Example One"))
        assertEquals("", GuideHeroText.status(matched = true, programmesLoaded = false))
        // Once loaded, the same channel really has nothing on.
        assertEquals("Nothing scheduled here", GuideHeroText.title(null, matched = true, programmesLoaded = true, channelListTitle = "Example One"))
        assertEquals("OK plays it live", GuideHeroText.status(matched = true, programmesLoaded = true))
        // A programme always wins; an unmatched channel is on all day.
        assertEquals("News", GuideHeroText.title("News", matched = true, programmesLoaded = false, channelListTitle = "Example One"))
        assertEquals("Example One", GuideHeroText.title(null, matched = false, programmesLoaded = true, channelListTitle = "Example One"))
        assertEquals(AllDayText.HERO_STATUS, GuideHeroText.status(matched = false, programmesLoaded = false))
    }

    @Test
    fun cellsStayBlankWhileProgrammesLoad() {
        // Still fetching: a plain block, never a channel name or "nothing on" in a cell.
        assertEquals("", GuideCellText.allDayBar("Example Loop", programmesLoaded = false))
        assertEquals(AllDayText.bar("Example Loop"), GuideCellText.allDayBar("Example Loop", programmesLoaded = true))
    }

    @Test
    fun aRowWithNothingInTheWindowStillHasABarThatCanShowFocus() {
        assertTrue(GuideCellText.showsBar(matched = true, programmesLoaded = true, cellsInWindow = 0))
        assertEquals("Nothing scheduled here", GuideCellText.barText("Example One", matched = true, programmesLoaded = true))
        assertFalse(GuideCellText.showsBar(matched = true, programmesLoaded = true, cellsInWindow = 2))
    }
}
