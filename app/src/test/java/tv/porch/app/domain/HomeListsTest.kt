package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeListsTest {
    private val now = 1_000_000_000L
    private val minute = 60_000L

    private fun channel(
        id: Long,
        name: String = "Harbour Channel $id",
        favourite: Boolean = false,
        matched: Boolean = true,
        lastPlayedAt: Long? = null,
    ) = Channel(
        id = id,
        sourceId = 1L,
        name = name,
        number = id.toInt(),
        group = "General",
        favourite = favourite,
        streamUrl = "http://example.com/live/$id.ts",
        xtreamStreamId = null,
        archiveDays = 0,
        matchedXmltvId = if (matched) "key-$id" else null,
        lastPlayedAt = lastPlayedAt,
    )

    private fun programme(channel: Channel, title: String, startMin: Long, endMin: Long) = Programme(
        id = channel.id * 1000 + startMin + 500,
        sourceId = 1L,
        channelKey = "key-${channel.id}",
        title = title,
        description = null,
        startUtc = now + startMin * minute,
        stopUtc = now + endMin * minute,
        category = null,
    )

    @Test
    fun favouriteOnNowKeepsChannelsWithNoGuideDataAndFiller() {
        val news = channel(1, "Harbour News", favourite = true)
        val quiet = channel(2, "Harbour Quiet", favourite = true, matched = false)
        val filler = channel(3, "Harbour Shop", favourite = true)
        val other = channel(4, "Harbour Other")
        val onNow = listOf(
            programme(news, "Harbour Bulletin", -10, 20),
            programme(filler, "Paid Programming", -10, 20),
            programme(other, "Harbour Cooking", -10, 20),
        )
        val rows = HomeLists.favouritesOnNow(listOf(news, quiet, filler, other), onNow, emptyMap()).all
        assertEquals(listOf("Harbour Bulletin", "Harbour Quiet", "Harbour Shop"), rows.map { it.title })
        assertEquals(listOf(1L, 2L, 3L), rows.map { it.channels.first().id })
    }

    @Test
    fun recentIsNewestFirstAndShowsFourOfThem() {
        val played = (1L..6L).map { channel(it, lastPlayedAt = now + it * minute) }
        val never = channel(7)
        val section = HomeLists.recent(played + never, emptyList(), emptyMap())
        assertEquals(listOf(6L, 5L, 4L, 3L), section.shown.map { it.channels.first().id })
        assertEquals(2, section.more)
        // Nothing known about what is on: the row says the channel's name.
        assertEquals("Harbour Channel 6", section.shown.first().title)
    }

    @Test
    fun startingSoonDropsFillerAndPutsFavouritesFirst() {
        val plain = channel(1)
        val fav = channel(2, favourite = true)
        val filler = channel(3)
        val programmes = listOf(
            programme(plain, "Harbour Weather", 5, 35),
            programme(fav, "Harbour Late Show", 20, 50),
            programme(filler, "To Be Announced", 8, 38),
        )
        val rows = HomeLists.startingSoon(listOf(plain, fav, filler), programmes, emptyMap(), now).all
        assertEquals(listOf("Harbour Late Show", "Harbour Weather"), rows.map { it.title })
        assertTrue(rows.none { it.title == "To Be Announced" })
    }

    @Test
    fun favouriteTilesPutFavouritesWithAProgrammeOnNowFirst() {
        val quiet = channel(1, "Harbour Quiet", favourite = true, matched = false)
        val news = channel(2, "Harbour News", favourite = true)
        val sport = channel(3, "Harbour Sport", favourite = true)
        val onNow = listOf(programme(news, "Harbour Bulletin", -10, 20), programme(sport, "Harbour Match", -5, 40))
        val tiles = HomeLists.favouriteTiles(HomeLists.favouritesOnNow(listOf(quiet, news, sport), onNow, emptyMap()))
        assertEquals(listOf(2L, 3L, 1L), tiles.map { it.channels.first().id })
    }

    @Test
    fun yourChannelsIsFavouritesThenRecentWithNoRepeatsAndCountsOnlyWhatSeeAllCanShow() {
        val quiet = channel(1, "Harbour Quiet", favourite = true, matched = false)
        val news = channel(2, "Harbour News", favourite = true, lastPlayedAt = now + minute)
        val played = channel(3, "Harbour Played", lastPlayedAt = now + 2 * minute)
        val older = channel(4, "Harbour Older", lastPlayedAt = now)
        val channels = listOf(quiet, news, played, older)
        val onNow = listOf(programme(news, "Harbour Bulletin", -10, 20))
        val yours = HomeLists.build(channels, onNow, emptyList(), emptyMap(), now).yours
        assertEquals(listOf(2L, 1L, 3L, 4L), yours.shown.map { it.channels.first().id })
        assertEquals(0, yours.more)

        // Five favourites on now and one with no guide, then a recent one. See all lists favourites with a programme on, so it can add only the fifth.
        val live = (11L..15L).map { channel(it, favourite = true) }
        val many = live + quiet + played
        val more = HomeLists.build(many, live.map { programme(it, "Harbour Show ${it.id}", -10, 20) }, emptyList(), emptyMap(), now).yours
        assertEquals(listOf(11L, 12L, 13L, 14L), more.shown.map { it.channels.first().id })
        assertEquals(1, more.more)
    }
}
