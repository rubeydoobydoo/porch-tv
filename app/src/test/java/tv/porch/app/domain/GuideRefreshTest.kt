package tv.porch.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideRefreshTest {
    private val hour = 60L * 60L * 1000L
    private val now = 1_000L * hour

    @Test
    fun aGuideOlderThanSixHoursIsDueAndANewerOneIsNot() {
        assertTrue(GuideRefresh.due(lastSyncAt = now - 7 * hour, now = now, watching = false))
        assertTrue(GuideRefresh.due(lastSyncAt = null, now = now, watching = false))
        assertFalse(GuideRefresh.due(lastSyncAt = now - 5 * hour, now = now, watching = false))
        // A TV whose clock went backwards must not wait for it to catch up.
        assertTrue(GuideRefresh.due(lastSyncAt = now + hour, now = now, watching = false))
    }

    @Test
    fun anOldGuideWaitsWhileSomethingIsBeingWatched() {
        assertFalse(GuideRefresh.due(lastSyncAt = now - 7 * hour, now = now, watching = true))
        assertFalse(GuideRefresh.due(lastSyncAt = null, now = now, watching = true))
    }
}
