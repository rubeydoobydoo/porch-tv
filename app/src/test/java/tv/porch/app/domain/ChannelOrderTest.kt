package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelOrderTest {
    private fun channel(number: Int, favourite: Boolean = false) = Channel(
        id = number.toLong(),
        sourceId = 1,
        name = "Channel $number",
        number = number,
        group = "General",
        favourite = favourite,
        streamUrl = "http://example.com/live/$number.ts",
        xtreamStreamId = null,
        archiveDays = 0,
        matchedXmltvId = "key-$number",
        lastPlayedAt = null,
    )

    @Test
    fun favouritesThenOnAirThenNumber() {
        val channels = listOf(5, 3, 9, 1, 7, 2).map { channel(it, favourite = it == 9 || it == 2) }

        val ordered = ChannelOrder.order(channels, onNowIds = setOf(3L, 7L))

        assertEquals(listOf(2, 9, 3, 7, 1, 5), ordered.map { it.number })
    }
}
