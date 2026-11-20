package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class HeaderClockFitTest {
    @Test
    fun fullClockShowsWhenItFitsBesideTheTitle() {
        assertEquals(HeaderClockFit.Shown.Full, HeaderClockFit.pick(space = 230, title = 94, gap = 14, full = 106, timeOnly = 62))
    }

    @Test
    fun clockDropsTheDayBeforeItHides() {
        assertEquals(HeaderClockFit.Shown.TimeOnly, HeaderClockFit.pick(space = 200, title = 94, gap = 14, full = 125, timeOnly = 62))
        assertEquals(HeaderClockFit.Shown.None, HeaderClockFit.pick(space = 160, title = 94, gap = 14, full = 125, timeOnly = 62))
    }

    @Test
    fun timeOnlyIsTheClockWithoutItsDay() {
        assertEquals("10:34 pm", HeaderClockFit.dropDay("Wednesday 10:34 pm"))
        assertEquals("10:34 pm", HeaderClockFit.dropDay("10:34 pm"))
    }
}
