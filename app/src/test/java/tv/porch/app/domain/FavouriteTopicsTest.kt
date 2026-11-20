package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavouriteTopicsTest {
    private fun channel(id: Long, name: String, group: String = "", guide: Boolean = true, logo: Boolean = true, tag: String? = null) = Channel(
        id = id, sourceId = 1, name = name, number = id.toInt(), group = group, favourite = false, streamUrl = "http://example.com/$id",
        xtreamStreamId = null, archiveDays = 0, matchedXmltvId = if (guide) "x$id" else null, lastPlayedAt = null,
        logoUrl = if (logo) "http://example.com/$id.png" else null, tag = tag,
    )

    private fun topics(channels: List<Channel>) = FavouriteTopics.sections(channels) { null }.associate { it.label to it.channels }

    @Test
    fun tenGolfChannelsOfferOnlyAFew() {
        val golf = (1L..10L).map { channel(it, "Example Golf $it") }
        val offered = topics(golf)["Golf"]!!
        assertEquals(FavouriteTopics.PER_TOPIC, offered.size)
    }

    @Test
    fun theAustralianCopyComesFirst() {
        val channels = listOf(
            channel(1, "Example NRL", group = "UK Sport"),
            channel(2, "Example NRL", group = "UK Sport"),
            channel(3, "Example NRL", group = "AU | Sport"),
            channel(4, "Other NRL Show", group = "UK Sport"),
            channel(5, "Other NRL Show", group = "UK Sport"),
            channel(6, "Other NRL Show", group = "UK Sport"),
            channel(7, "Other NRL Show", group = "UK Sport"),
        )
        // One copy of each name, and the Australian name and copy win even though the other is carried more often.
        assertEquals(listOf(3L, 4L), topics(channels)["NRL"]!!.map { it.id })
    }

    @Test
    fun wrestlingNflAndTheLeaguesGetTheirOwnLists() {
        val channels = listOf(
            channel(1, "Example WWE Network"),
            channel(2, "Example AEW"),
            channel(3, "Example NFL RedZone"),
            channel(4, "Example Premier League"),
            channel(5, "Example A-League"),
            channel(6, "Example Champions League"),
            channel(7, "Example Cricket"),
        )
        val found = topics(channels)
        assertEquals(listOf(2L, 1L), found["Wrestling"]!!.map { it.id })
        assertEquals(listOf(3L), found["NFL"]!!.map { it.id })
        assertEquals(listOf(4L), found["Premier League"]!!.map { it.id })
        assertEquals(listOf(5L), found["A-League"]!!.map { it.id })
        assertEquals(listOf(6L), found["Champions League"]!!.map { it.id })
        assertTrue("cricket is not on the list", found.values.flatten().none { it.id == 7L })
    }

    @Test
    fun freeToAirIsOnlyTheAustralianChannels() {
        val channels = listOf(channel(1, "Nine", group = "AU | Free to air"), channel(2, "Nine", group = "Example Group"))
        assertEquals(listOf(1L), topics(channels)["Free-to-air"]!!.map { it.id })
    }

    @Test
    fun onlyChannelsWithAGuideAndALogoAndNoLoopsAreOffered() {
        val channels = listOf(
            channel(1, "Example UFC", guide = false),
            channel(2, "Example UFC 2", logo = false),
            channel(3, "24/7 UFC Classics"),
            channel(4, "Example UFC 4"),
        )
        assertEquals(listOf(4L), topics(channels)["UFC"]!!.map { it.id })
    }

    @Test
    fun australianFoxChannelsWithNoGuideStillCount() {
        val channels = listOf(
            channel(1, "Fox Sports 504", group = "AU | Australia", guide = false),
            channel(2, "Example Fox Sports 502 Leauge", group = "AU | Australia", guide = false),
            channel(3, "Example AFL Replay", group = "UK Sport", guide = false),
        )
        val found = topics(channels)
        assertEquals(listOf(1L), found["AFL"]!!.map { it.id })
        assertEquals(listOf(2L), found["NRL"]!!.map { it.id })
    }

    @Test
    fun aTopicWithNoGuidedChannelOffersTheOthers() {
        val channels = listOf(channel(1, "Example Champions League 1", guide = false), channel(2, "Example Champions League 2", guide = false, logo = false))
        assertEquals(listOf(1L), topics(channels)["Champions League"]!!.map { it.id })
    }

    @Test
    fun eventSlotsComeAfterTheRealChannel() {
        val channels = listOf(
            channel(1, "AFL TV 00 | Example vs Example", group = "AU| AFL PPV", guide = false),
            channel(2, "Fox Sports 504", group = "AU| Australia", guide = false),
        )
        assertEquals(listOf(2L, 1L), topics(channels)["AFL"]!!.map { it.id })
    }

    @Test
    fun moviesTvShowsDocsAndMusicGetTheirOwnLists() {
        val kinds = mapOf(1L to Kind.Movies, 3L to Kind.Docs, 4L to Kind.Music)
        val channels = listOf(channel(1, "Example Cinema"), channel(2, "E!"), channel(3, "Example Discovery"), channel(4, "Example Hits"))
        val found = FavouriteTopics.sections(channels) { kinds[it.id] }.associate { it.label to it.channels.map { c -> c.id } }
        assertEquals(listOf(1L), found["Movies"])
        assertEquals(listOf(2L), found["TV shows"])
        assertEquals(listOf(3L), found["Docs"])
        assertEquals(listOf(4L), found["Music"])
    }

    @Test
    fun tvShowsSplitIntoDramaComedyAndLifestyle() {
        val channels = listOf(
            channel(1, "Example Crime"),
            channel(2, "Example Showcase"),
            channel(3, "Example Comedy"),
            channel(4, "Example Food"),
            channel(5, "Example Home"),
            channel(6, "Example Travel"),
            channel(7, "HGTV"),
            channel(8, "E!"),
            channel(9, "Example Entertainment"),
        )
        val found = topics(channels).mapValues { (_, list) -> list.map { it.id }.toSet() }
        assertEquals(setOf(1L, 2L), found["Drama and crime"])
        assertEquals(setOf(3L), found["Comedy"])
        assertEquals(setOf(4L, 5L, 6L, 7L), found["Lifestyle"])
        assertEquals(setOf(8L, 9L), found["TV shows"])
    }

    @Test
    fun theNewGroupsOfferFiveAustralianFirstLikeTheOthers() {
        val overseas = (1L..6L).map { channel(it, "Example Lifestyle $it", group = "UK Entertainment") }
        val local = channel(7, "Example Lifestyle Food", group = "AU | Entertainment", guide = false)
        val offered = topics(overseas + local)["Lifestyle"]!!.map { it.id }
        assertEquals(FavouriteTopics.PER_TOPIC, offered.size)
        assertEquals(7L, offered.first())
    }

    @Test
    fun theNewGroupsComeInOrderAfterMovies() {
        val channels = listOf(channel(1, "E!"), channel(2, "Example Food"), channel(3, "Example Comedy"), channel(4, "Example Drama"), channel(5, "Example Cinema"))
        val labels = FavouriteTopics.sections(channels) { if (it.id == 5L) Kind.Movies else null }.map { it.label }
        assertEquals(listOf("Movies", "Drama and crime", "Comedy", "Lifestyle", "TV shows"), labels)
    }
}
