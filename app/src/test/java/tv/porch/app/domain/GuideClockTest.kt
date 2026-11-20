package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideClockTest {
    @Test
    fun positiveOffsetShowsTheGuideLater() {
        val start = 1_700_000_000_000L
        assertEquals(start + 60 * 60_000L, GuideClock.shift(start, 60))
        assertTrue(GuideClock.isOnNow(start, start + 30 * 60_000L, start + 70 * 60_000L, 60))
        assertFalse(GuideClock.isOnNow(start, start + 30 * 60_000L, start + 10 * 60_000L, 60))
    }

    @Test
    fun startingSoonUsesTheShiftedStart() {
        val start = 1_700_000_000_000L
        val now = start + 50 * 60_000L
        assertTrue(GuideClock.startsWithin(start, now, 60, 30 * 60_000L))
        assertFalse(GuideClock.startsWithin(start, now, 0, 30 * 60_000L))
    }
}
