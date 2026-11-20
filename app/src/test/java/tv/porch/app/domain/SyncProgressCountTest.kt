package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.porch.app.domain.SyncProgress.Stage

class SyncProgressCountTest {
    @Test
    fun onceTheChannelsAreInTheLineSaysHowMany() {
        assertEquals("Found 20,000 channels. Fetching the guide", SyncProgress(Stage.Guide, channels = 20_000).line())
        assertEquals("Matching 20,000 channels to the guide", SyncProgress(Stage.Matching, channels = 20_000).line())
        assertEquals("Fetching channels…", SyncProgress(Stage.Channels).homeLine())
        assertEquals("Found 20,000 channels. Fetching the guide…", SyncProgress(Stage.Guide, channels = 20_000).homeLine())
    }
}
