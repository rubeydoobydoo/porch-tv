package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryRulesTest {
    @Test
    fun retryBackoffStopsOnARefusedLogin() {
        assertEquals(1_000L, RetryPolicy.delayMs(1))
        assertEquals(2_000L, RetryPolicy.delayMs(2))
        assertEquals(16_000L, RetryPolicy.delayMs(5))
        assertEquals(30_000L, RetryPolicy.delayMs(6))
        assertTrue(RetryPolicy.isAuthFailure(401))
        assertTrue(RetryPolicy.isAuthFailure(403))
        assertFalse(RetryPolicy.isAuthFailure(404))
        assertFalse(RetryPolicy.isAuthFailure(null))
    }

    @Test
    fun shelvesCapFavouritesAndKeepSoonInTimeOrder() {
        val channels = (1..8).map { number ->
            channel(number.toLong(), "Sport", number, favourite = number == 8)
        } + channel(9, "News", 1, favourite = true)
        val now = 1_000_000L
        val onNow = channels.map { programme(it, now - 1_000, now + 60_000) }
        val soon = listOf(
            programme(channels[7], now + 20 * 60_000, now + 40 * 60_000),
            programme(channels[0], now + 5 * 60_000, now + 15 * 60_000),
        )
        val home = Shelves.home(channels, onNow, soon, emptyMap())
        assertEquals(5, home.onNowShown.size)
        assertEquals(4, home.onNowExtra)
        assertTrue(home.onNowShown.first().channel.favourite)
        assertEquals(listOf(1L, 8L), home.soon.map { it.channel.id })
        assertEquals(2, home.soonShown.size)
        assertEquals(2, home.categories.size)
        assertEquals("Sport", home.categories.first().group)
    }

    @Test
    fun hdAndSdSharingAGuideIdGetTheirOwnStartingSoonCards() {
        val hd = channel(1, "News", 1, favourite = false)
        val sd = channel(2, "News", 2, favourite = false).copy(matchedXmltvId = hd.matchedXmltvId)
        val now = 1_000_000L
        val soon = listOf(programme(hd, now + 10 * 60_000, now + 40 * 60_000))
        val home = Shelves.home(listOf(hd, sd), emptyList(), soon, emptyMap())
        assertEquals(2, home.soonShown.size)
        assertEquals(2, home.soonShown.map { it.rowKey }.distinct().size)
    }

    @Test
    fun languageTagsAndBadges() {
        assertEquals("EN", LanguageTags.tagOf("EN: Sport"))
        assertEquals("AR", LanguageTags.tagOf("AR | News"))
        assertEquals("EN", LanguageTags.tagOf("English Movies"))
        assertEquals(null, LanguageTags.tagOf("Sport"))
        assertEquals("Albania", LanguageTags.nameOf("AL"))
        assertEquals("Africa", LanguageTags.nameOf("AFR"))
        assertEquals("ZZQ", LanguageTags.nameOf("ZZQ"))
        assertTrue(LanguageTags.allows("Sport", "EN"))
        assertFalse(LanguageTags.allows("AR | News", "EN"))
        assertEquals("M+", ChannelBadge.letters("Movies Plus"))
        assertEquals("24", ChannelBadge.letters("News 24"))
        assertEquals("N1", ChannelBadge.letters("News 1"))
        assertEquals("KZ", ChannelBadge.letters("Kids Zone"))
    }

    @Test
    fun catchupWindow() {
        val now = 10_000_000L
        val day = 24L * 60L * 60L * 1000L
        assertFalse(catchupAllowed(0, now - day, now))
        assertFalse(catchupAllowed(3, now + day, now))
        assertTrue(catchupAllowed(3, now - day, now))
        assertFalse(catchupAllowed(1, now - 2 * day, now))
        assertEquals(90, catchupMinutes(0, 90 * 60_000L))
        assertEquals(12 * 60, catchupMinutes(0, 20 * 60 * 60_000L))
    }

    private fun channel(id: Long, group: String, number: Int, favourite: Boolean) = Channel(
        id = id,
        sourceId = 1,
        name = "Channel $number",
        number = number,
        group = group,
        favourite = favourite,
        streamUrl = "http://example.com/live/$id.ts",
        xtreamStreamId = null,
        archiveDays = 0,
        matchedXmltvId = "key-$id",
        lastPlayedAt = null,
    )

    @Test
    fun catchupAsksForTheCorrectedStart() {
        val start = 50_000_000L
        assertEquals(start + 3_600_000L, catchupStart(start, 60))
        assertEquals(start - 1_800_000L, catchupStart(start, -30))
        assertEquals(start, catchupStart(start, 0))
    }

    private fun programme(channel: Channel, start: Long, stop: Long) = Programme(
        id = channel.id,
        sourceId = channel.sourceId,
        channelKey = channel.matchedXmltvId!!,
        title = channel.name,
        description = null,
        startUtc = start,
        stopUtc = stop,
        category = null,
    )
}
