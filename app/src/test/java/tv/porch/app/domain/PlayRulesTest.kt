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

    @Test
    fun endOfListIsSaidWhileANewChannelIsStarting() {
        assertTrue(PlayRules.canSayEndOfList(banner = null, tryingNote = null, stopped = false))
        assertTrue(PlayRules.canSayEndOfList(banner = "Starting…", tryingNote = null, stopped = false))
        assertTrue(PlayRules.canSayEndOfList(banner = "Starting catch-up…", tryingNote = null, stopped = false))
        assertTrue(PlayRules.canSayEndOfList(banner = "Trying another copy…", tryingNote = "Trying another copy…", stopped = false))
    }

    @Test
    fun endOfListNeverCoversADropOrAnotherMessage() {
        assertFalse(PlayRules.canSayEndOfList(banner = null, tryingNote = null, stopped = true))
        assertFalse(PlayRules.canSayEndOfList(banner = "The stream dropped. Press Retry to try again.", tryingNote = null, stopped = false))
        assertFalse(PlayRules.canSayEndOfList(banner = "This stream can't rewind.", tryingNote = null, stopped = false))
    }

    @Test
    fun endOfListIsAlsoSaidWhileTryingAnotherStreamFormat() {
        assertTrue(PlayRules.canSayEndOfList(banner = PlayRules.TRYING_FORMAT, tryingNote = null, stopped = false))
    }

    @Test
    fun theStartWordsComeBackWhenTheEndOfListFades() {
        assertEquals("Starting…", PlayRules.afterEndOfList(before = "Starting…", tryingNote = null))
        assertEquals(PlayRules.TRYING_FORMAT, PlayRules.afterEndOfList(before = PlayRules.TRYING_FORMAT, tryingNote = null))
        assertEquals(null, PlayRules.afterEndOfList(before = null, tryingNote = null))
        assertEquals(null, PlayRules.afterEndOfList(before = "This stream can't rewind.", tryingNote = null))
    }
}
