package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VodRefreshTest {
    @Test
    fun aWholeCategoryWithItemsPrunesAndCountsAsFetched() {
        val after = VodRefresh.afterCategory(rows = 12, wasList = true)
        assertTrue(after.prune)
        assertTrue(after.markFetched)
    }

    @Test
    fun anEmptyCategoryListIsFetchedButPrunesNothing() {
        // A valid [] reply: nothing to show, but opening the chip again must not download it again.
        val after = VodRefresh.afterCategory(rows = 0, wasList = true)
        assertFalse(after.prune)
        assertTrue(after.markFetched)
    }

    @Test
    fun aReplyThatIsNotAListChangesNothing() {
        // An error object or an empty body: try again next open, and keep every stored row.
        val after = VodRefresh.afterCategory(rows = 0, wasList = false)
        assertFalse(after.prune)
        assertFalse(after.markFetched)
    }

    @Test
    fun anEmptyCategoryReplyKeepsTheStoredCategories() {
        assertFalse(VodRefresh.replaceCategories(0))
        assertTrue(VodRefresh.replaceCategories(3))
    }

    @Test
    fun continueHidesAnItemNoLongerListedAndKeepsTheOrder() {
        val points = listOf(5L, 9L, 7L, 3L)
        val listed = mapOf(5L to "Five", 7L to "Seven", 3L to "Three")
        val tiles = VodRefresh.continueTiles(points, listed, { it }, max = 2)
        assertEquals(listOf(5L to "Five", 7L to "Seven"), tiles)
        // The progress itself is not touched: the dropped item's entry is still in the list passed in.
        assertEquals(4, points.size)
    }

    @Test
    fun aTitleIsListedUnderTheChipThatAskedAndItsOwnCategory() {
        // A panel answers "category 32" with a show that says it belongs to 31: it is in both chips.
        assertEquals(listOf("32", "31"), VodRefresh.listedUnder(asked = "32", own = "31"))
        assertEquals(listOf("32"), VodRefresh.listedUnder(asked = "32", own = "32"))
        assertEquals(listOf("32"), VodRefresh.listedUnder(asked = "32", own = " "))
    }

    @Test
    fun theFullListFilesATitleUnderItsOwnCategoryOnly() {
        assertEquals(listOf("31"), VodRefresh.listedUnder(asked = null, own = "31"))
        // No category at all still gets one listing, so the title is not left with none.
        assertEquals(listOf(""), VodRefresh.listedUnder(asked = null, own = ""))
    }
}
