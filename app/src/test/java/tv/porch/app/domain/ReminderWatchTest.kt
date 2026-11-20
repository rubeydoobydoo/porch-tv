package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderWatchTest {
    @Test
    fun watchTunesAnOpenPlayerAndOpensOneOtherwise() {
        assertEquals(ReminderWatch.Action.Tune, ReminderWatch.action(playerOnTop = true))
        assertEquals(ReminderWatch.Action.Open, ReminderWatch.action(playerOnTop = false))
    }
}
