package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelBadgeTitleTest {
    private fun channel(name: String, title: String) = Channel(
        id = 1, sourceId = 1, name = name, number = 1, group = "Example", favourite = false,
        streamUrl = "http://example.com/1.ts", xtreamStreamId = null, archiveDays = 0, matchedXmltvId = null,
        lastPlayedAt = null, title = title,
    )

    @Test
    fun theBadgeComesFromTheReadableNameNotTheProviderPrefix() {
        // Every "US: ..." channel used to read "U" plus a letter.
        assertEquals("EN", channel("US: Example News", "Example News").badge)
        assertEquals("ES", channel("US: Example Sport", "Example Sport").badge)
    }

    @Test
    fun aChannelWithNoReadableNameFallsBackToItsRawName() {
        assertEquals("EN", channel("Example News", "").badge)
    }
}
