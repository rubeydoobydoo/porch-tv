package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LoopEmptyTextTest {
    @Test
    fun switchedOffGroupsPointToSettings() {
        assertEquals("No 24/7 channels are showing. Switch them on in Settings > Channels.", LoopEmptyText.say(providerHasLoop = true, hiddenLoop = false))
    }

    @Test
    fun noLoopChannelsSaysSo() {
        assertEquals("Your provider has no 24/7 channels.", LoopEmptyText.say(providerHasLoop = false, hiddenLoop = false))
    }

    @Test
    fun onlyHiddenGroupsSaySoAndWhereToUnhide() {
        assertEquals("Your 24/7 groups are hidden. Unhide them in Settings > Channels.", LoopEmptyText.say(providerHasLoop = false, hiddenLoop = true))
    }
}
