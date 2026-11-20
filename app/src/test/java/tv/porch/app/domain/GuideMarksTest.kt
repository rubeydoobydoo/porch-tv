package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideMarksTest {
    private val minute = 60_000L
    private val mark = 1_000L * minute

    @Test
    fun aTimeLabelUnderTheRedNowChipIsHidden() {
        assertTrue(GuideWindow.markHiddenByNow(mark, now = mark + 4 * minute))
        assertTrue(GuideWindow.markHiddenByNow(mark, now = mark - 3 * minute))
    }

    @Test
    fun labelsClearOfTheChipStay() {
        assertFalse(GuideWindow.markHiddenByNow(mark, now = mark + 20 * minute))
        assertFalse(GuideWindow.markHiddenByNow(mark, now = mark - 10 * minute))
    }

    @Test
    fun theElapsedToneReachesTheNowLineOnlyInTheProgrammeOnAir() {
        val windowMs = 120 * minute
        val start = mark
        val stop = mark + 60 * minute
        // 30 minutes into a 2-hour window 1,200 wide: the line is at 300, so a cell starting at 4 is toned for 296.
        assertEquals(296f, GuideWindow.elapsedWidth(start, stop, now = mark + 30 * minute, mark, windowMs, 1_200f, cellX = 4f), 0.01f)
        assertEquals(0f, GuideWindow.elapsedWidth(start, stop, now = stop + minute, mark, windowMs, 1_200f, cellX = 4f), 0.01f)
        assertEquals(0f, GuideWindow.elapsedWidth(start, stop, now = start - minute, mark, windowMs, 1_200f, cellX = 4f), 0.01f)
    }

    @Test
    fun inAThreeHourGuideTheChipCoversMoreOfTheLabel() {
        assertTrue(GuideWindow.markHiddenByNow(mark, now = mark + 20 * minute, windowMs = 180 * minute))
    }
}
