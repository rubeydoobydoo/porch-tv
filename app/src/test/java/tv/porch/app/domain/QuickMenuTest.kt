package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickMenuTest {
    private fun channel(favourite: Boolean = false, group: String = "News") = Channel(
        id = 1, sourceId = 1, name = "Harbour News", number = 7, group = group, favourite = favourite,
        streamUrl = "http://example.com/a.ts", xtreamStreamId = null, archiveDays = 0, matchedXmltvId = null, lastPlayedAt = null,
    )

    @Test
    fun browseMenuOffersFavouriteThenHideGroup() {
        assertEquals(
            listOf(QuickAction.AddFavourite, QuickAction.HideGroup),
            QuickMenu.rows(channel(), inPlayer = false, paused = false),
        )
    }

    @Test
    fun aFavouriteOffersRemove() {
        assertEquals(QuickAction.RemoveFavourite, QuickMenu.rows(channel(favourite = true), false, false).first())
    }

    @Test
    fun noGroupMeansNoHideRow() {
        assertFalse(QuickAction.HideGroup in QuickMenu.rows(channel(group = ""), false, false))
    }

    @Test
    fun playerPutsPauseAndTracksAboveTheBrowseRows() {
        assertEquals(
            listOf(QuickAction.Pause, QuickAction.Tracks, QuickAction.AddFavourite, QuickAction.HideGroup),
            QuickMenu.rows(channel(), inPlayer = true, paused = false),
        )
    }

    @Test
    fun pausedPlayerOffersPlay() {
        assertEquals(QuickAction.Play, QuickMenu.rows(channel(), inPlayer = true, paused = true).first())
    }

    @Test
    fun labelsAndMessagesReadRight() {
        assertEquals("Add to Favourites", QuickMenu.label(QuickAction.AddFavourite))
        assertEquals("Remove from Favourites", QuickMenu.label(QuickAction.RemoveFavourite))
        assertEquals("Audio & subtitles", QuickMenu.label(QuickAction.Tracks))
        assertEquals("Added to Favourites", QuickMenu.message(QuickAction.AddFavourite))
        assertEquals("Removed from Favourites", QuickMenu.message(QuickAction.RemoveFavourite))
        assertTrue(QuickMenu.message(QuickAction.HideGroup)!!.contains("Settings > Channels"))
        assertNull(QuickMenu.message(QuickAction.Pause))
        assertNull(QuickMenu.message(QuickAction.Tracks))
    }

    @Test
    fun hideGroupNamesTheGroupAndCountsItsChannels() {
        assertEquals("Hide this group", QuickMenu.label(QuickAction.HideGroup))
        assertEquals("Hide Sports", QuickMenu.label(QuickAction.HideGroup, groupName = "Sports"))
        assertEquals("Hide Sports (12 channels)", QuickMenu.label(QuickAction.HideGroup, groupName = "Sports", groupCount = 12))
        assertEquals("Hide Example Group N… (120 channels)", QuickMenu.label(QuickAction.HideGroup, groupName = "Example Group Name Is Long", groupCount = 120))
        assertEquals("Hide Sports (1 channel)", QuickMenu.label(QuickAction.HideGroup, groupName = "Sports", groupCount = 1))
    }

    @Test
    fun favouriteMessageStaysUpTwoSecondsAndHideLonger() {
        assertEquals(2_000L, QuickMenu.messageMillis(QuickAction.AddFavourite))
        assertTrue(QuickMenu.messageMillis(QuickAction.HideGroup) > 2_000L)
    }
}
