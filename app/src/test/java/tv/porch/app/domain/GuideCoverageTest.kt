package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GuideCoverageTest {
    private fun channel(id: Long, matched: String?, sourceId: Long = 1) = Channel(
        id = id,
        sourceId = sourceId,
        name = "Channel $id",
        number = id.toInt(),
        group = "News",
        favourite = false,
        streamUrl = "http://example.com/live/$id.ts",
        xtreamStreamId = null,
        archiveDays = 0,
        matchedXmltvId = matched,
        lastPlayedAt = null,
    )

    @Test
    fun unmatchedChannelsHideUnlessTheSwitchIsOn() {
        val channels = listOf(channel(1, "key-1"), channel(2, null), channel(3, "key-3"))

        assertEquals(listOf(1L, 3L), GuideCoverage.filter(channels, showWithoutGuide = false).map { it.id })
        assertEquals(listOf(1L, 2L, 3L), GuideCoverage.filter(channels, showWithoutGuide = true).map { it.id })
    }

    @Test
    fun aSourceWithNoGuideAtAllIsNeverHidden() {
        val channels = listOf(channel(1, null, sourceId = 1), channel(2, null, sourceId = 9))

        assertEquals(listOf(2L), GuideCoverage.filter(channels, showWithoutGuide = false, sourcesWithoutGuideUrl = setOf(9L)).map { it.id })
    }
}
