package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerOverlayRulesTest {
    @Test
    fun upFromTheTopOrDownFromTheBottomOfAListIsTheEnd() {
        assertNull(PlayRules.zapTarget(pending = 0, delta = -1, size = 1))
        assertNull(PlayRules.zapTarget(pending = 0, delta = 1, size = 1))
        assertNull(PlayRules.zapTarget(pending = 4, delta = 1, size = 5))
        assertEquals(3, PlayRules.zapTarget(pending = 4, delta = -1, size = 5))
        assertNull(PlayRules.zapTarget(pending = 0, delta = 1, size = 0))
    }

    @Test
    fun theEndOfTheListHasAMessage() {
        assertEquals("End of this list", PlayRules.END_OF_LIST)
    }

    @Test
    fun aProgrammeListedForAnotherChannelIsNotShown() {
        val old = listOf("Sports Desk")
        assertEquals(emptyList<String>(), PlayRules.programmesFor(loadedFor = 1L, channelId = 2L, items = old))
        assertEquals(old, PlayRules.programmesFor(loadedFor = 2L, channelId = 2L, items = old))
    }
}
