package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TabsTest {
    @Test
    fun tabSwapsRootAndBackGoesHome() {
        assertEquals(listOf("Home"), Tabs.stackFor("Home", "Home"))
        val onGuide = Tabs.stackFor("Home", "Guide")
        assertEquals(listOf("Home", "Guide"), onGuide)
        assertEquals(listOf("Home", "Search"), Tabs.stackFor("Home", "Search"))
        assertEquals(listOf("Home"), onGuide.dropLast(1))
    }
}
