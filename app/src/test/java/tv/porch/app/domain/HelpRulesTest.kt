package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class HelpRulesTest {
    @Test
    fun shownOnceWhenChannelsArrive() {
        assertEquals(false, shouldShowHelp(seen = false, channelCount = 0))
        assertEquals(true, shouldShowHelp(seen = false, channelCount = 1))
        assertEquals(false, shouldShowHelp(seen = true, channelCount = 1))
        assertEquals(false, shouldShowHelp(seen = true, channelCount = 0))
    }
}
