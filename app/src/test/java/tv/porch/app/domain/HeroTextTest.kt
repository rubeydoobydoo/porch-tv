package tv.porch.app.domain

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeroTextTest {
    private val utc = ZoneId.of("UTC")

    private fun at(time: String) = Instant.parse("2026-10-26T${time}:00Z").toEpochMilli()

    private fun programme(title: String, from: String, to: String) = Programme(
        id = 1L,
        sourceId = 1L,
        channelKey = "harbour.example",
        title = title,
        description = null,
        startUtc = at(from),
        stopUtc = at(to),
        category = null,
    )

    @Test
    fun linesForProgrammeAndForBareChannel() {
        val now = programme("The Evening Report", "20:00", "21:00")
        val next = programme("Late Edition", "21:00", "22:00")
        val withGuide = HeroText.of("Harbour News", now, next, at("20:42"), zone = utc)
        assertEquals("The Evening Report", withGuide.title)
        assertEquals("8:00 – 9:00 pm · 18 min left", withGuide.subtitle)
        assertEquals("Next 9:00 pm Late Edition", withGuide.nextLine)
        assertEquals("Watch Harbour News", withGuide.okLine)

        val bare = HeroText.of("Harbour News", null, null, at("20:42"), zone = utc)
        assertEquals("Harbour News", bare.title)
        assertEquals("Live now. Programme details will appear when the guide is in.", bare.subtitle)
        assertNull(bare.nextLine)
        assertEquals("Watch Harbour News", bare.okLine)
    }
}
