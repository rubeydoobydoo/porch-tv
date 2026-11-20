package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgrammeTextTest {
    @Test
    fun titlesAndSynopsesLoseProviderNoise() {
        val titles = listOf(
            "Edge Of Alaska (?)" to "Edge Of Alaska",
            "LIVE: SportsCenter - Tuesday 3 November 2026" to "SportsCenter",
            "Live: Late News" to "Late News",
            "  Harbour Days " to "Harbour Days",
        )
        for ((raw, expected) in titles) assertEquals(raw, expected, ProgrammeText.title(raw))
        assertEquals(
            "Tony Kornheiser and Mike Wilbon run down...",
            ProgrammeText.synopsis("Tuesday 3 November 2026. Tony Kornheiser and Mike Wilbon run down..."),
        )
        assertEquals("Nothing to strip.", ProgrammeText.synopsis("Nothing to strip."))
        assertNull(ProgrammeText.synopsis("Tuesday 3 November 2026. "))
        assertNull(ProgrammeText.synopsis(null))
        assertNull(ProgrammeText.synopsis("   "))
    }
}
