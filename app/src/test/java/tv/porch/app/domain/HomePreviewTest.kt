package tv.porch.app.domain

import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class HomePreviewTest {
    private val nine = ZonedDateTime.of(2026, 11, 2, 21, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
    private val minute = 60_000L
    private val channel = Channel(
        id = 1L,
        sourceId = 1L,
        name = "Harbour Sport",
        number = 1,
        group = "Sport",
        favourite = false,
        streamUrl = "http://example.com/live/1.ts",
        xtreamStreamId = null,
        archiveDays = 0,
        matchedXmltvId = null,
        lastPlayedAt = null,
    )

    private fun status(startMs: Long, endMs: Long) =
        HomePreview.status(LiveRows.Row("Harbour City v Northgate", startMs, endMs, listOf(channel)), nine, ZoneOffset.UTC)

    @Test
    fun theStatusLineSaysLiveLaterOrNoGuide() {
        assertEquals(HomePreview.Status(live = true, text = "ends 9:45 pm"), status(nine - 15 * minute, nine + 45 * minute))
        assertEquals(HomePreview.Status(live = false, text = "Starts 9:30 pm"), status(nine + 30 * minute, nine + 60 * minute))
        assertEquals(HomePreview.Status(live = false, text = "No guide for this channel"), status(0L, 0L))
    }
}
