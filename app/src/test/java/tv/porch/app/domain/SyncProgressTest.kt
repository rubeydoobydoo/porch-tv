package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.porch.app.domain.SyncProgress.Stage

class SyncProgressTest {
    @Test
    fun progressLinesReadAsSentences() {
        val table = listOf(
            SyncProgress(Stage.Waiting) to "Waiting for another source",
            SyncProgress(Stage.Channels) to "Fetching channels",
            SyncProgress(Stage.Guide, programmes = 12_400) to "Guide filling in: 12,400 programmes so far",
            SyncProgress(Stage.Guide, programmes = 115_000, expected = 280_000) to
                "Guide filling in: 115,000 of 280,000 programmes",
            SyncProgress(Stage.Matching) to "Matching channels to the guide",
            SyncProgress(Stage.Done, channels = 49_244) to "Up to date",
            SyncProgress(Stage.Done) to "Up to date",
        )
        table.forEach { (progress, line) -> assertEquals(line, progress.line()) }
    }
}
