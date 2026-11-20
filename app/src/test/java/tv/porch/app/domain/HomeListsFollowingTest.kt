package tv.porch.app.domain

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeListsFollowingTest {
    private val now = 1_000_000_000L
    private val minute = 60_000L
    private val zone = ZoneId.of("UTC")

    private fun channel(id: Long) = Channel(
        id = id, sourceId = 1L, name = "Harbour Channel $id", number = id.toInt(), group = "General", favourite = false,
        streamUrl = "http://example.com/live/$id.ts", xtreamStreamId = null, archiveDays = 0, matchedXmltvId = "key-$id", lastPlayedAt = null,
    )

    private fun programme(channel: Channel, title: String, startMin: Long, endMin: Long) = Programme(
        id = channel.id * 1000 + startMin, sourceId = 1L, channelKey = "key-${channel.id}", title = title, description = null,
        startUtc = now + startMin * minute, stopUtc = now + endMin * minute, category = null,
    )

    @Test
    fun followingTakesTheThirdListWithOnNowFirstAndDayWordsOnLaterRows() {
        val one = channel(1)
        val two = channel(2)
        val later = programme(two, "Matildas v England", 24 * 60, 26 * 60)
        val live = programme(one, "Matildas Warm-up", -10, 50)
        val lists = HomeLists.build(listOf(one, two), emptyList(), emptyList(), emptyMap(), now, following = listOf(two to later, one to live, two to programme(two, "Matildas Warm-up", 120, 180)))
        assertTrue(lists.thirdIsFollowing)
        // The same title again later today is not a second row.
        assertEquals(2, lists.third.all.size)
        assertEquals(listOf("Matildas Warm-up", "Matildas v England"), lists.third.all.map { it.title })
        val trailing = lists.third.all.map { HomeLists.followingTrailing(it, now, zone) }
        assertTrue(trailing[0].startsWith("ends "))
        assertTrue(trailing[1].startsWith("Tomorrow "))
    }

    @Test
    fun withNothingFollowedOnTheThirdListIsStartingSoon() {
        val one = channel(1)
        val soon = programme(one, "Harbour Bulletin", 10, 40)
        val filler = programme(one, "Paid Programming", -10, 40)
        val plain = HomeLists.build(listOf(one), emptyList(), listOf(soon), emptyMap(), now)
        val withFiller = HomeLists.build(listOf(one), emptyList(), listOf(soon), emptyMap(), now, following = listOf(one to filler))
        assertEquals(false, withFiller.thirdIsFollowing)
        assertEquals(listOf("Harbour Bulletin"), withFiller.third.all.map { it.title })
        assertEquals(plain.third, withFiller.third)
    }
}
