package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleModeTest {
    @Test
    fun simpleModeShowsOnlyHomeAndTheGuide() {
        assertEquals(listOf("Home", "Full guide"), SimpleMode.tabs(Tabs.labels, simple = true))
        assertEquals(Tabs.labels, SimpleMode.tabs(Tabs.labels, simple = false))
        for (tab in listOf("Movies", "Series", "Sport", "Search", "Settings", "What's on now", "24/7")) {
            assertFalse(tab, SimpleMode.allows(tab, simple = true))
            assertTrue(tab, SimpleMode.allows(tab, simple = false))
        }
        assertTrue(SimpleMode.allows("Full guide", simple = true))
    }

    @Test
    fun holdOkCannotHideAGroupInSimpleMode() {
        val rows = listOf(QuickAction.About, QuickAction.AddFavourite, QuickAction.OtherCopies, QuickAction.HideGroup)
        assertEquals(listOf(QuickAction.About, QuickAction.AddFavourite, QuickAction.OtherCopies), SimpleMode.menuRows(rows, simple = true))
        assertEquals(rows, SimpleMode.menuRows(rows, simple = false))
    }

    @Test
    fun backFromThePlayerGoesToTheFavouritesAndBackThereAsksToLeave() {
        // Opened from the guide: simple mode skips the guide and lands on the favourites list, by the remote's Back
        // (the app's Back step) or the player's own Back button.
        val isPlayer = { route: String -> route == "player" }
        assertEquals(listOf("home"), SimpleMode.back(listOf("home", "guide", "player"), "home", simple = true, isPlayer))
        assertEquals(listOf("home"), SimpleMode.afterPlayer(listOf("home", "guide", "player"), "home", simple = true))
        assertEquals(listOf("home", "guide"), SimpleMode.back(listOf("home", "guide", "player"), "home", simple = false, isPlayer))
        assertEquals(listOf("home", "guide"), SimpleMode.afterPlayer(listOf("home", "guide", "player"), "home", simple = false))
        // Back from the guide itself is the usual step.
        assertEquals(listOf("home"), SimpleMode.back(listOf("home", "guide"), "home", simple = true, isPlayer))
        assertEquals(SimpleMode.HomeBack.AskLeave, SimpleMode.homeBack(simple = true))
        assertEquals(SimpleMode.HomeBack.BackTwice, SimpleMode.homeBack(simple = false))
    }
}
