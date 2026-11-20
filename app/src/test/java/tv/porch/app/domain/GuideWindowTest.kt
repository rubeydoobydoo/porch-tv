package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GuideWindowTest {
    private val hour = 3_600_000L
    private val windowStart = 10 * hour
    private val windowMs = 2 * hour

    @Test
    fun cellGeometryClampsToTheWindow() {
        // Inside: the second half hour of a two hour window, 600 wide.
        val inside = GuideWindow.span(windowStart + hour / 2, windowStart + hour, windowStart, windowMs, 600f)
        assertEquals(150f, inside.x, 0.01f)
        assertEquals(150f, inside.width, 0.01f)
        // Starts before and ends after: fills the whole window.
        val whole = GuideWindow.span(windowStart - hour, windowStart + 3 * hour, windowStart, windowMs, 600f)
        assertEquals(0f, whole.x, 0.01f)
        assertEquals(600f, whole.width, 0.01f)
        // Runs off the left edge: clipped to the edge.
        val left = GuideWindow.span(windowStart - hour, windowStart + hour, windowStart, windowMs, 600f)
        assertEquals(0f, left.x, 0.01f)
        assertEquals(300f, left.width, 0.01f)
        // Wholly outside: nothing to draw.
        assertEquals(0f, GuideWindow.span(windowStart - 2 * hour, windowStart, windowStart, windowMs, 600f).width, 0.01f)
        assertEquals(0f, GuideWindow.span(windowStart + windowMs, windowStart + 3 * hour, windowStart, windowMs, 600f).width, 0.01f)
    }
}
