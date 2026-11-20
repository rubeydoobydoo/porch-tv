package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherRowTest {
    private val now = 1_000_000_000L
    private val minute = 60_000L

    private fun channel(id: Long, name: String = "Harbour Channel $id", logo: String? = "https://example.com/logo/$id.png") = Channel(
        id = id,
        sourceId = 1L,
        name = name,
        number = id.toInt(),
        group = "General",
        favourite = false,
        streamUrl = "http://example.com/live/$id.ts",
        xtreamStreamId = null,
        archiveDays = 0,
        matchedXmltvId = "key-$id",
        lastPlayedAt = null,
        logoUrl = logo,
    )

    private fun row(channel: Channel, title: String = "Evening Bulletin ${channel.id}", startMin: Long = -10, endMin: Long = 20) =
        LiveRows.Row(title, now + startMin * minute, now + endMin * minute, listOf(channel), programmeId = channel.id * 10)

    private fun pick(yours: List<LiveRows.Row>, sport: List<LiveRows.Row> = emptyList(), pictures: Map<Long, String> = emptyMap(), secrets: Map<Long, List<String>> = emptyMap(), families: ChannelFamilies = ChannelFamilies.None) =
        LauncherRow.pick(yours, sport, pictures, now, families, secrets)

    @Test
    fun picksWhatIsLiveWithAPictureYoursFirstThenSport() {
        val news = channel(1)
        val shop = channel(2)
        val bare = channel(3, logo = null)
        val later = channel(4)
        val leaky = channel(5, logo = "https://example.com/u/hunter2/logo.png")
        val match = channel(6)
        val encoded = channel(8, logo = "https://example.com/u/hun%74er2/logo.png")
        val broken = channel(9, logo = "https://example.com/u/100%/logo.png")
        val cards = pick(
            yours = listOf(
                row(news, "LIVE: Harbour Bulletin"),
                row(shop, "Paid Programming"),
                row(bare),
                row(later, startMin = 5, endMin = 30),
                row(leaky),
                row(encoded),
                LiveRows.Row("Harbour Channel 7", 0L, 0L, listOf(channel(7))),
            ),
            sport = listOf(row(match, "Harbour v Rovers"), row(broken), row(news, "Repeat of Harbour Bulletin")),
            pictures = mapOf(bare.id * 10 to "https://example.com/poster/3.jpg", match.id * 10 to "ftp://example.com/poster/6.jpg"),
            secrets = mapOf(1L to listOf("hunter2", "")),
        )
        // The bare channel is saved by its programme picture; the leaky logo has no safe stand-in; the ftp picture falls back to the logo.
        assertEquals(listOf(1L, 3L, 6L, 9L), cards.map { it.channelId })
        assertEquals("Harbour Bulletin", cards[0].title)
        assertEquals("https://example.com/logo/1.png", cards[0].image)
        assertEquals("https://example.com/poster/3.jpg", cards[1].image)
        assertEquals("https://example.com/logo/6.png", cards[2].image)
        assertEquals(now + 20 * minute, cards[0].endMs)
    }

    @Test
    fun showsAChannelFamilyOnceAndNeverMoreThanTen() {
        val hd = channel(101, "UK| ACME NEWS HD").copy(matchedXmltvId = "acme.uk")
        val sd = channel(102, "UK| ACME NEWS SD").copy(matchedXmltvId = "acme.uk")
        val families = ChannelFold.families(listOf(hd, sd))
        val cards = pick(
            yours = (1L..14L).map { row(channel(it)) } + row(channel(30)),
            families = families,
        )
        assertEquals((1L..10L).toList(), cards.map { it.channelId })
        val folded = pick(yours = listOf(row(hd), row(sd), row(channel(3))), families = families)
        assertEquals(listOf(101L, 3L), folded.map { it.channelId })
    }

    @Test
    fun readsOnlyOurOwnChannelLink() {
        assertEquals(123L, LauncherRow.channelIdFrom("porch://channel/123"))
        assertEquals("porch://channel/9", LauncherRow.linkFor(9L))
        assertNull(LauncherRow.channelIdFrom("porch://channel/abc"))
        assertNull(LauncherRow.channelIdFrom("porch://channel/12/extra"))
        assertNull(LauncherRow.channelIdFrom("https://example.com/channel/123"))
        assertNull(LauncherRow.channelIdFrom(null))
    }
}
