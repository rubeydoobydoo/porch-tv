package tv.porch.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReturnTileTest {
    @Test
    fun comingBackLandsOnlyOnTheLastFocusedTileAndOnlyOnce() {
        val back = ReturnTile()
        back.focused(1)
        back.focused(2)
        assertFalse(back.isFor(2))
        back.leaving()
        assertFalse(back.isFor(1))
        assertTrue(back.isFor(2))
        back.landed()
        assertFalse(back.isFor(2))
    }
}
