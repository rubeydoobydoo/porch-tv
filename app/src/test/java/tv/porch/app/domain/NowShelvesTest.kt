package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NowShelvesTest {
    private fun channel(id: Long, number: Int, favourite: Boolean = false, sourceId: Long = 1L) = Channel(
        id = id,
        sourceId = sourceId,
        name = "Channel $id",
        number = number,
        group = "General",
        favourite = favourite,
        streamUrl = "http://example.com/live/$id.ts",
        xtreamStreamId = null,
        archiveDays = 0,
        matchedXmltvId = "key-$id",
        lastPlayedAt = null,
    )

    private fun programme(channelId: Long, start: Long = 0L, sourceId: Long = 1L) = Programme(
        id = channelId * 1000 + start,
        sourceId = sourceId,
        channelKey = "key-$channelId",
        title = "Show $channelId at $start",
        description = null,
        startUtc = start,
        stopUtc = start + 1_800_000L,
        category = null,
    )

    private val channels = listOf(
        channel(1, number = 5),
        channel(2, number = 3, favourite = true),
        channel(3, number = 1),
        channel(4, number = 9, favourite = true),
        channel(5, number = 2),
    )
    private val kinds = mapOf(1L to Kind.News, 2L to Kind.News, 3L to Kind.Sport, 4L to Kind.News, 5L to Kind.News)
    private val live = listOf(1L, 2L, 3L, 5L).map { programme(it) }
    private val hour = listOf(1L, 2L, 4L).map { programme(it) }
    private val tonight = listOf(3L, 4L, 5L).map { programme(it) }

    private fun build(chip: NowChip) = NowShelves.build(channels, live, hour, tonight, chip, kindOf = { kinds[it.id] })

    private fun NowShelves.Lists.ids() = Triple(live.map { it.channel.id }, hour.map { it.channel.id }, tonight.map { it.channel.id })

    @Test
    fun chipFiltersAndOrderApplyToEveryShelf() {
        // All: Live is favourites first, then by number. The later shelves have equal starts here, so the same order.
        assertEquals(Triple(listOf(2L, 3L, 5L, 1L), listOf(2L, 4L, 1L), listOf(4L, 3L, 5L)), build(NowChip.All).ids())
        // Favourites: only favourite channels, in all three shelves.
        assertEquals(Triple(listOf(2L), listOf(2L, 4L), listOf(4L)), build(NowChip.Favourites).ids())
        // A kind: only that kind, still favourites first.
        assertEquals(Triple(listOf(2L, 5L, 1L), listOf(2L, 4L, 1L), listOf(4L, 5L)), build(NowChip.OfKind(Kind.News)).ids())
        assertEquals(Triple(listOf(3L), emptyList<Long>(), listOf(3L)), build(NowChip.OfKind(Kind.Sport)).ids())
    }

    @Test
    fun laterShelvesShowEachChannelsNextProgramme() {
        val twoInHour = listOf(programme(1, start = 2_400_000L), programme(1, start = 600_000L))
        val lists = NowShelves.build(channels, emptyList(), twoInHour, emptyList(), NowChip.All, kindOf = { kinds[it.id] })
        assertEquals(listOf(600_000L), lists.hour.map { it.programme.startUtc })
        // Later shelves run in time order: channel 3 starts first, so it comes before favourite channel 2.
        val inTime = NowShelves.build(channels, emptyList(), listOf(programme(2, start = 1_200_000L), programme(3, start = 600_000L)), emptyList(), NowChip.All, kindOf = { kinds[it.id] })
        assertEquals(listOf(3L, 2L), inTime.hour.map { it.channel.id })
        // The start is the guide-offset one: channel 6 is on a source 30 minutes behind, so it moves after channel 2.
        val other = channel(6, number = 4, sourceId = 2L)
        val shifted = NowShelves.build(
            channels + other, emptyList(), listOf(programme(2, start = 1_200_000L), programme(6, start = 600_000L, sourceId = 2L)), emptyList(),
            NowChip.All, offsets = mapOf(2L to 30), kindOf = { kinds[it.id] },
        )
        assertEquals(listOf(2L, 6L), shifted.hour.map { it.channel.id })
        // Tonight refreshes hourly, so an airing that started before now is dropped; the channel's later one stays.
        val tonightRows = listOf(programme(3, start = 600_000L), programme(5, start = 3_000_000L), programme(5, start = 900_000L))
        val upcoming = NowShelves.build(channels, emptyList(), emptyList(), tonightRows, NowChip.All, now = 1_000_000L, kindOf = { kinds[it.id] })
        assertEquals(listOf(5L), upcoming.tonight.map { it.channel.id })
        assertEquals(listOf(3_000_000L), upcoming.tonight.map { it.programme.startUtc })
    }
}
