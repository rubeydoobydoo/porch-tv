package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SportMatchTest {
    private val now = 1_020_000_000L
    private val minute = 60_000L

    private fun channel(id: Long, number: Int, name: String, favourite: Boolean = false) = Channel(
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

    private val sport = channel(1, 1, "Harbour Sport 1")
    private val movies = channel(2, 2, "Harbour Movies")

    private fun airing(channel: Channel, title: String, startMin: Long = -30) = Airing(
        channel,
        Programme(
            id = channel.id * 100 + startMin,
            sourceId = 1L,
            channelKey = "key-${channel.id}",
            title = title,
            description = null,
            startUtc = now + startMin * minute,
            stopUtc = now + (startMin + 90) * minute,
            category = null,
        ),
    )

    @Test
    fun aVersusTitleOnASportChannelIsAMatch() {
        assertTrue(SportMatch.isMatch("Harbour City v Northgate", sport))
        assertTrue(SportMatch.isMatch("Harbour City VS Northgate", sport))
        assertTrue(SportMatch.isMatch("Harbour City @ Northgate", sport))
        assertFalse(SportMatch.isMatch("Harbour City versus Northgate", sport))
        assertFalse(SportMatch.isMatch("v Northgate", sport))
        // Team - Team reads as a match too, but only as two short names.
        assertTrue(SportMatch.isMatch("Harbour City - Northgate", sport))
        assertFalse(SportMatch.isMatch("Harbour Weekly - The Long Story Of A Great Season In Sport", sport))
    }

    @Test
    fun theSameTitleOnAMovieChannelIsNotAMatch() {
        assertFalse(SportMatch.isMatch("Harbour City v Northgate", movies))
        assertFalse(SportMatch.isMatch("UFC 300", movies))
    }

    @Test
    fun aLeagueCountsAtTheStartOfTheTitleOnly() {
        assertTrue(SportMatch.isMatch("UFC 300: Main Card", sport))
        assertTrue(SportMatch.isMatch("premier league Harbour Hour", sport))
        assertFalse(SportMatch.isMatch("Harbour Weekly Show about the UFC", sport))
        assertFalse(SportMatch.isMatch("UFCs of the world", sport))
    }

    @Test
    fun excludedWordsRejectAnOtherwiseGoodTitle() {
        for (title in listOf(
            "Harbour City v Northgate Replay",
            "Harbour City v Northgate - highlights",
            "NFL Best Of the Week",
            "NBA Preview",
            "Boxing Talk",
            "UFC Classics",
        )) assertFalse(title, SportMatch.isMatch(title, sport))
    }

    @Test
    fun matchesSortFirstInTheSportListAndStillFold() {
        val other = channel(3, 3, "Harbour Sport 2")
        val screen = SportMatch.screen(
            live = listOf(
                airing(sport, "Harbour Golf Magazine Hour"),
                airing(other, "Harbour City v Northgate"),
                airing(channel(4, 4, "Harbour Sport 3", favourite = true), "Harbour City v Northgate"),
            ),
            upcoming = listOf(
                airing(sport, "Harbour Studio", startMin = 60),
                airing(sport, "Northgate v Eastbank", startMin = 120),
            ),
            offsets = emptyMap(),
        )
        assertEquals(listOf("Harbour City v Northgate", "Harbour Golf Magazine Hour"), screen.live.rows.map { it.title })
        assertEquals(listOf(4L, 3L), screen.live.rows[0].channels.map { it.id })
        assertEquals(listOf(true, false), screen.live.rows.map { it.match })
        assertEquals(listOf("Northgate v Eastbank"), screen.hour.map { it.title })
    }
}
