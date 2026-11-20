package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRowsTest {
    private fun channel(id: Long, favourite: Boolean) = Channel(
        id = id,
        sourceId = 1L,
        name = "Channel $id",
        number = id.toInt(),
        group = "News",
        favourite = favourite,
        streamUrl = "http://example.com/$id",
        xtreamStreamId = null,
        archiveDays = 0,
        matchedXmltvId = null,
        lastPlayedAt = null,
    )

    @Test
    fun yourChannelsPutsFavouritesFirstAndCaps() {
        val channels = (1L..8L).map { channel(it, favourite = it == 6L || it == 3L) }
        val shelf = HomeRows.yourChannels(channels.reversed())
        assertEquals(listOf(3L, 6L, 1L, 2L, 4L), shelf.shown.map { it.id })
        assertEquals(listOf(3L, 6L, 1L, 2L, 4L, 5L, 7L, 8L), shelf.all.map { it.id })
        assertEquals(8 - SHELF_CAP, shelf.extra)
        assertEquals(8, shelf.total)
    }
}
