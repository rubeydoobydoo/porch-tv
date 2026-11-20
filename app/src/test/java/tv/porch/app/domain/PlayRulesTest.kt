package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayRulesTest {
    @Test
    fun theChannelAlreadyPlayedLastIsNotWrittenAgain() {
        // Every write makes the whole 49,000-row channel list reload, so a resume or a reconnect must not write.
        assertFalse(PlayRules.needsMark(channelId = 7, mostRecentId = 7))
    }

    @Test
    fun aDifferentChannelOrAFirstPlayIsWritten() {
        assertTrue(PlayRules.needsMark(channelId = 7, mostRecentId = 3))
        assertTrue(PlayRules.needsMark(channelId = 7, mostRecentId = null))
    }

    @Test
    fun aChannelCountsAsPlayedOnlyAfterTenSeconds() {
        // Zapping past a channel should not reload the list while video plays.
        assertEquals(10_000L, PlayRules.MARK_AFTER_MS)
    }

    @Test
    fun comingBackRejoinsLiveButCatchUpCarriesOn() {
        assertEquals(0L, PlayRules.resumeAtMs(catchup = false, positionMs = 90_000L))
        assertEquals(90_000L, PlayRules.resumeAtMs(catchup = true, positionMs = 90_000L))
    }

    @Test
    fun aChannelSaysItIsStartingUntilThePictureArrives() {
        assertEquals("Starting…", PlayRules.startWords(catchup = false, tryingNote = null))
        assertEquals("Starting catch-up…", PlayRules.startWords(catchup = true, tryingNote = null))
        assertEquals("Trying another copy…", PlayRules.startWords(catchup = false, tryingNote = "Trying another copy…"))
    }
}
