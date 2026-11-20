package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabsWrapTest {
    private val tabs = listOf("Home", "Full guide", "Search", "Settings")

    @Test
    fun aFreshPressWrapsRoundTheEnds() {
        // Home to Settings in one press is what makes Settings > Channels a short walk.
        assertEquals("Settings", Tabs.wrap(tabs, "Home", direction = -1, repeatCount = 0))
        assertEquals("Home", Tabs.wrap(tabs, "Settings", direction = 1, repeatCount = 0))
        // Inside the bar, and at an end going inwards, focus moves as normal.
        assertNull(Tabs.wrap(tabs, "Full guide", direction = -1, repeatCount = 0))
        assertNull(Tabs.wrap(tabs, "Home", direction = 1, repeatCount = 0))
    }

    @Test
    fun aHeldKeyStopsAtTheEnd() {
        assertNull(Tabs.wrap(tabs, "Home", direction = -1, repeatCount = 3))
        assertNull(Tabs.wrap(tabs, "Settings", direction = 1, repeatCount = 1))
    }
}
