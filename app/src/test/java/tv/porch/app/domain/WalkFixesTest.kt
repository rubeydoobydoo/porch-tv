package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WalkFixesTest {
    private fun channel(id: Long, number: Int, favourite: Boolean, group: String = "News") = Channel(
        id = id, sourceId = 1, name = "Example $id", number = number, group = group, favourite = favourite,
        streamUrl = "http://example.com/$id.ts", xtreamStreamId = null, archiveDays = 0, matchedXmltvId = null, lastPlayedAt = null,
    )

    private val all = listOf(
        channel(1, 10, favourite = false),
        channel(2, 30, favourite = true),
        channel(3, 20, favourite = true),
        channel(4, 5, favourite = false),
    )

    @Test
    fun simpleColdStartZapsTheFavouritesInListOrder() {
        // Same order as the simple favourites list: by channel number. Last played (id 3) is the start.
        val start = SimpleMode.coldStart(all, ChannelFamilies.None, lastId = 3)
        assertEquals(listOf(3L, 2L), start.first)
        assertEquals(0, start.second)
    }

    @Test
    fun simpleColdStartOnANonFavouriteStillStartsThereThenZapsFavourites() {
        val start = SimpleMode.coldStart(all, ChannelFamilies.None, lastId = 4)
        assertEquals(listOf(4L, 3L, 2L), start.first)
        assertEquals(0, start.second)
    }

    @Test
    fun holdOkNeverOffersRemoveFromFavouritesInSimpleMode() {
        val rows = listOf(QuickAction.About, QuickAction.RemoveFavourite, QuickAction.HideGroup)
        assertEquals(listOf(QuickAction.About), SimpleMode.menuRows(rows, simple = true))
        assertEquals(rows, SimpleMode.menuRows(rows, simple = false))
    }

    @Test
    fun whatElseIsOnListsOnlyFavouritesInSimpleMode() {
        assertEquals(listOf(2L, 3L), SimpleMode.offered(all, simple = true).map { it.id })
        assertEquals(all, SimpleMode.offered(all, simple = false))
    }

    @Test
    fun aStreamThatCannotSeekIsNotSeeked() {
        assertNull(Films.skipTarget(positionMs = 60_000L, stepMs = 10_000L, durationMs = 0L, seekable = false))
        assertEquals(70_000L, Films.skipTarget(60_000L, 10_000L, 5_400_000L, seekable = true))
        assertEquals(0L, Films.skipTarget(5_000L, -10_000L, 5_400_000L, seekable = true))
        assertEquals(5_400_000L, Films.skipTarget(5_395_000L, 10_000L, 5_400_000L, seekable = true))
    }

    @Test
    fun reconnectingSaysWhichTry() {
        assertEquals("Reconnecting… try 3 of 6", RetryPolicy.reconnectingText(3, 6))
    }
}
