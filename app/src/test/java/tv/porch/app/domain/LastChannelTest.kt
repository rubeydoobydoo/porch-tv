package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LastChannelTest {
    private fun channel(id: Long, name: String) = Channel(
        id = id, sourceId = 1, name = name, number = id.toInt(), group = "News", favourite = false,
        streamUrl = "http://example.com/$id.ts", xtreamStreamId = null, archiveDays = 0, matchedXmltvId = null, lastPlayedAt = null,
    )

    @Test
    fun previousIsTheChannelBeforeAndPlayingItFlipsBack() {
        val last = LastChannel()
        val harbour = channel(1, "Harbour News")
        val bay = channel(2, "Bay Sport")
        val hill = channel(3, "Hill Movies")
        assertNull(last.previous())
        last.played(harbour)
        assertNull(last.previous())
        last.played(bay)
        assertEquals("Harbour News", last.previous()?.name)
        last.played(bay)
        assertEquals("Harbour News", last.previous()?.name)
        last.played(harbour)
        assertEquals("Bay Sport", last.previous()?.name)
        last.played(hill)
        assertEquals("Harbour News", last.previous()?.name)
    }
}
