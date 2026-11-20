package tv.porch.app.domain

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayTextTest {
    private val zone = ZoneId.of("UTC")

    // 2026-01-05 20:00 UTC and 21:00 UTC, in epoch millis.
    private val start = 1_767_643_200_000L
    private val stop = start + 3_600_000L

    @Test
    fun linesForLiveAndCatchup() {
        val now = start + 42 * 60_000L
        assertEquals("8:00 – 9:00 pm · 18 min left", OverlayText.of(start, stop, catchup = false, now = now, zone = zone))
        assertEquals("Catch-up · 8:00 – 9:00 pm", OverlayText.of(start, stop, catchup = true, now = now, zone = zone))
        assertEquals("", OverlayText.of(null, null, catchup = false, now = now, zone = zone))
    }

    @Test
    fun acrossNoonEachEndKeepsItsOwnAmOrPm() {
        val from = start - 30_600_000L // 11:30 am
        assertEquals(
            "11:30 am – 12:30 pm · 50 min left",
            OverlayText.of(from, from + 3_600_000L, catchup = false, now = from + 10 * 60_000L, zone = zone),
        )
    }
}
