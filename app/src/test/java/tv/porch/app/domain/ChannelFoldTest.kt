package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ChannelFoldTest {
    private fun channel(
        id: Long,
        name: String,
        group: String = "Example",
        guide: String? = null,
        favourite: Boolean = false,
        played: Long? = null,
        number: Int = id.toInt() + 100,
    ) = Channel(id, 1L, name, number, group, favourite, "http://example.com/$id", null, 0, guide, played)

    private fun named(names: List<String>, group: String = "Example") = names.mapIndexed { i, name -> channel(i + 1L, name, group) }

    private fun shownCount(list: List<Channel>) = ChannelFold.fold(list, ChannelFold.families(list)).size

    @Test
    fun copiesOfOneChannelFoldIntoOne() {
        val copies = listOf(
            "UK| ACME NEWS", "UK| ACME NEWS SD", "UK| ACME NEWS HD", "UK| ACME NEWS FHD", "UK| ACME NEWS UHD", "UK| ACME NEWS 4K",
            "UK| ACME NEWS HEVC", "UK| ACME NEWS H265", "UK| ACME NEWS H264", "UK| ACME NEWS HDR", "UK| ACME NEWS RAW",
            "UK| ACME NEWS 50fps", "UK| ACME NEWS 60FPS", "UK| ACME NEWS ᶠᴴᴰ", "UK| ACME NEWS VIP",
            "UK| ACME NEWS Backup", "UK| ACME NEWS backup 2", "UK| ACME NEWS HD(BK)", "UK| ACME NEWS ALT", "UK| ACME NEWS (2)", "UK| ACME NEWS #2",
            "UK| Acme News", "GB| Acme News HD", "Acme News",
        )
        // The last has no label of its own: its group gives it.
        assertEquals(1, shownCount(named(copies, group = "UK| Example")))
        // One matched and one not, or both matched to one guide id, are still one channel.
        // A number written out or raised is the same number.
        assertEquals(1, shownCount(named(listOf("UK| ACME 2", "UK| ACME TWO", "UK| ACME HD \u00B2"))))
        val matched = listOf(channel(1, "UK| ACME NEWS", guide = "acme.uk"), channel(2, "UK| ACME NEWS HD", guide = "acme.uk"), channel(3, "UK| ACME NEWS SD"))
        assertEquals(1, shownCount(matched))
    }

    @Test
    fun aWrongFoldIsWorseThanAMissedOne() {
        val apart = listOf(
            listOf("UK| ACME ONE", "UK| ACME ONE +1"),
            listOf("UK| ACME ONE", "UK| ACME ONE+1"),
            listOf("UK| ACME ONE +1", "UK| ACME ONE +2"),
            listOf("US| ACME FILM EAST", "US| ACME FILM WEST"),
            listOf("US| ACME FILM", "US| ACME FILM PACIFIC"),
            listOf("UK| ACME NORTH", "UK| ACME SOUTH"),
            listOf("UK| ACME LONDON", "UK| ACME LEEDS"),
            listOf("UK| ACME 1", "UK| ACME 2"),
            listOf("UK| ACME", "UK| ACME 2"),
            listOf("UK| ACME 2", "UK| ACME (2) 3"),
            listOf("UK| ACME", "US| ACME"),
            listOf("UK| ACME", "EN| ACME"),
            listOf("[UK] ACME", "[US] ACME"),
            listOf("24/7 ACME SHOW", "24/7 ACME SHOW HD"),
            listOf("UK| 24/7 ACME SHOW", "UK| 24/7 ACME SHOW HD"),
            listOf("UK| TV", "UK| TV HD"),
            listOf("UK| E4", "UK| E4 HD"),
            listOf("UK| PPV 5", "UK| PPV 5 HD"),
            listOf("UK| ACME EVENT 5", "UK| ACME EVENT 5 HD"),
            listOf("UK| ACME+", "UK| ACME"),
            listOf("UK| ACME ALT 1", "UK| ACME ALT 2"),
            listOf("UK| ACME TWO NORTH", "UK| ACME 2 NORTH"),
            // A raised 2 or 3 that opens a raised "25fps" or "3840p" is quality, not the channel's number.
            listOf("UK| ACME 2 HD", "UK| ACME \u1D34\u1D30 \u00B2\u2075\u1DA0\u1D56\u02E2"),
            listOf("UK| ACME 3 HD", "UK| ACME \u2074\u1D37 \u00B3\u2078\u2074\u2070\u1D3E"),
            // A numbered event stays an event when its number is the "#" kind of copy marker.
            listOf("UK| PPV #1", "UK| PPV #2"),
            // A leading bracket is part of what the channel is: +1, East, or another country's label.
            listOf("(+1) ACME NEWS", "ACME NEWS"),
            listOf("(EAST) ACME NEWS", "ACME NEWS"),
            listOf("UK| [DE] ACME", "UK| ACME"),
        )
        for (names in apart) assertEquals(names.toString(), names.size, shownCount(named(names)))
        // A 24/7 group, and a numbered channel in an events group, are never folded.
        assertEquals(2, shownCount(named(listOf("ACME SHOW", "ACME SHOW HD"), group = "24/7 Comedy")))
        assertEquals(2, shownCount(named(listOf("UK| ACME 7", "UK| ACME 7 HD"), group = "UK| PPV Events")))
        // With no label of its own, a channel's group tells countries apart.
        val groups = listOf(channel(1, "ACME SPORT", group = "Germany Sports"), channel(2, "ACME SPORT HD", group = "Italy Sports"))
        assertEquals(2, shownCount(groups))
        assertEquals(1, shownCount(listOf(channel(1, "ACME SPORT", group = "Sports"), channel(2, "ACME SPORT HD", group = "Sports HD"))))
        // Different guide ids stay apart, and a copy with none can't say which it belongs to.
        val twoGuides = listOf(channel(1, "UK| ACME", guide = "a.uk"), channel(2, "UK| ACME HD", guide = "b.uk"), channel(3, "UK| ACME SD"))
        assertEquals(3, shownCount(twoGuides))
    }

    @Test
    fun theRepresentativeIsTheBestCopyAndTheRestFollowInThatOrder() {
        val best = listOf(
            channel(1, "UK| ACME SD", favourite = true, number = 900),
            channel(2, "UK| ACME SD", played = 2_000L, number = 901),
            channel(3, "UK| ACME SD", played = 1_000L, number = 902),
            channel(4, "UK| ACME SD", guide = "a.uk", number = 903),
            channel(5, "UK| ACME FHD", number = 904),
            channel(6, "UK| ACME ᴴᴰ", number = 905),
            channel(7, "UK| ACME UHD", number = 906),
            channel(8, "UK| ACME 4K", number = 907),
            channel(9, "UK| ACME", number = 908),
            channel(10, "UK| ACME", number = 909),
            channel(11, "UK| ACME SD", number = 910),
        )
        val list = best.reversed()
        val families = ChannelFold.families(list)
        assertEquals(best.map { it.id }, families.copiesOf(1L).map { it.id })
        assertEquals(listOf(1L), ChannelFold.fold(list, families).map { it.id })
        // Playing from a list keeps the copy the viewer chose, wherever it ranks.
        assertEquals(listOf(7L), ChannelFold.fold(list, families, keep = 7L).map { it.id })
        // A list that holds only some copies gets the best of those.
        assertEquals(listOf(5L), ChannelFold.fold(best.subList(4, 11), families).map { it.id })
        // A list keyed on the guide folds only copies that have guide data: the best copy overall may have none, and would take the matched one with it.
        assertEquals(listOf(1L), ChannelFold.fold(list.filter { it.id in setOf(1L, 4L) }, families).map { it.id })
        assertEquals(listOf(4L), ChannelFold.foldGuided(list.filter { it.id in setOf(1L, 4L) }, families).map { it.id })
        assertEquals(listOf("FHD", "HD", "UHD", "UHD", null, null, "SD"), best.subList(4, 11).map { ChannelFold.qualityOf(it.name).label })
    }

    @Test
    fun aFailingStreamMovesThroughEachCopyOnceThenGivesUp() {
        val list = listOf(channel(1, "UK| ACME HD"), channel(2, "UK| ACME FHD"), channel(3, "UK| ACME SD"))
        val copies = ChannelFold.families(list).copiesOf(1L)
        val tried = HashSet<Long>()
        val walked = ArrayList<Long>()
        var at: Channel? = copies.first()
        while (at != null) {
            walked += at.id
            tried += at.id
            at = ChannelFold.nextCopy(copies, tried, catchup = false)
        }
        assertEquals(listOf(2L, 1L, 3L), walked)
        // Starting from a lower copy still tries the better ones, and never the one it's on.
        assertEquals(2L, ChannelFold.nextCopy(copies, setOf(3L), catchup = false)?.id)
        // Catch-up plays one recording, so it never moves to another copy.
        assertNull(ChannelFold.nextCopy(copies, setOf(2L), catchup = true))
        // A channel with no copies has nowhere to go.
        assertNull(ChannelFold.nextCopy(emptyList(), setOf(9L), catchup = false))
    }

    @Test
    fun switchedOffTheListComesBackUnchanged() {
        val list = named(listOf("UK| ACME HD", "UK| ACME FHD", "UK| ACME SD"))
        val off = ChannelFold.families(list, on = false)
        assertSame(list, ChannelFold.fold(list, off))
        assertEquals(emptyList<Channel>(), off.copiesOf(1L))
        val counts = ChannelFold.counts(list)
        assertEquals(3, counts.total)
        assertEquals(1, counts.shown)
    }
}
