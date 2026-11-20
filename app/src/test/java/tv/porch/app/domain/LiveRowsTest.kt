package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveRowsTest {
    private val now = 1_000_000_000L
    private val minute = 60_000L

    private fun channel(id: Long, number: Int, name: String = "Harbour Channel $id", favourite: Boolean = false) = Channel(
        id = id,
        sourceId = 1L,
        name = name,
        number = number,
        group = "General",
        favourite = favourite,
        streamUrl = "http://example.com/live/$id.ts",
        xtreamStreamId = null,
        archiveDays = 0,
        matchedXmltvId = "key-$id",
        lastPlayedAt = null,
    )

    private fun airing(channel: Channel, title: String, startMin: Long = -30, endMin: Long = 30) = Airing(
        channel,
        Programme(
            id = channel.id * 100 + startMin,
            sourceId = 1L,
            channelKey = "key-${channel.id}",
            title = title,
            description = null,
            startUtc = now + startMin * minute,
            stopUtc = now + endMin * minute,
            category = null,
        ),
    )

    private fun live(vararg airings: Airing) = LiveRows.live(airings.toList(), emptyMap())

    @Test
    fun sameTitleAndStartIsOneRowListingItsChannels() {
        val rows = live(
            airing(channel(1, 10), "Harbour News"),
            airing(channel(2, 11), "  harbour   NEWS "),
            airing(channel(3, 12), "Harbour News", startMin = -45),
            // The guide can repeat a programme; the channel is still listed once.
            airing(channel(1, 10), "Harbour News"),
        ).rows
        assertEquals(2, rows.size)
        assertEquals(listOf(1L, 2L), rows[0].channels.map { it.id })
        assertEquals(listOf(3L), rows[1].channels.map { it.id })
    }

    @Test
    fun fillerIsLeftOutAndCounted() {
        assertEquals(true, LiveRows.isFiller("Teleshopping", channel(9, 9)))
        assertEquals(true, LiveRows.isFiller("Local Programming", channel(9, 9)))

        val real = airing(channel(5, 5), "Harbour Cricket")
        val result = live(
            airing(channel(1, 1), "Paid Programming"),
            airing(channel(2, 2), "TBA"),
            airing(channel(3, 3), "Harbour News"),
            airing(channel(4, 4), "No Information"),
            real,
        )
        assertEquals(listOf("Harbour News", "Harbour Cricket"), result.rows.map { it.title })
        assertEquals(3, result.hidden)
    }

    @Test
    fun aTitleSameAsItsChannelNameIsFiller() {
        val result = live(
            airing(channel(1, 1, name = "Harbour Sport"), "harbour  sport"),
            airing(channel(2, 2, name = "Harbour Sport 2"), "Harbour Sport"),
        )
        assertEquals(listOf("Harbour Sport"), result.rows.map { it.title })
        assertEquals(1, result.hidden)
    }

    @Test
    fun commonestEndTimeWinsAndTiesGoToTheEarliest() {
        val result = live(
            airing(channel(1, 1), "A One", endMin = 60),
            airing(channel(2, 2), "B Two", endMin = 30),
            airing(channel(3, 3), "C Three", endMin = 60),
            airing(channel(4, 4), "D Four", endMin = 30),
            airing(channel(5, 5), "E Five", endMin = 90),
        )
        assertEquals(now + 30 * minute, result.commonEnd)
        assertNull(live().commonEnd)
        // A few minutes either way reads as the same end time, so the row stays quiet.
        val common = now + 30 * minute
        assertEquals(false, LiveRows.endWorthShowing(now + 31 * minute, common))
        assertEquals(true, LiveRows.endWorthShowing(now + 45 * minute, common))
        assertEquals(true, LiveRows.endWorthShowing(now + 45 * minute, null))
    }

    @Test
    fun aFavouriteLeadsItsFoldedChannelsThenLowestNumber() {
        val rows = live(
            airing(channel(1, 5), "Harbour News"),
            airing(channel(2, 9, favourite = true), "Harbour News"),
            airing(channel(3, 2), "Harbour News"),
        ).rows
        assertEquals(1, rows.size)
        assertEquals(listOf(2L, 3L, 1L), rows[0].channels.map { it.id })
    }

    @Test
    fun aGuidesLiveLabelAndDateAroundATitleAreLeftOut() {
        assertEquals("SportsCenter", LiveRows.shownTitle("LIVE: SportsCenter - Tuesday 3 November 2026"))
        assertEquals("Harbour News - Late Edition", LiveRows.shownTitle("Harbour News - Late Edition"))
    }
}
