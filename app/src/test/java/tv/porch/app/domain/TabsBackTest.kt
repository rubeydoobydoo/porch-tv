package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TabsBackTest {
    @Test
    fun backTwiceFromThePlayerStaysOnHome() {
        val once = Tabs.back(listOf("home", "player"), "home")
        assertEquals(listOf("home"), once)
        assertEquals(listOf("home"), Tabs.back(once, "home"))
    }
}
