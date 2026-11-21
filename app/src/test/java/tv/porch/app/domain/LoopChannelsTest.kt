package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LoopChannelsTest {
    private var next = 1L

    private fun channel(
        name: String,
        group: String,
        sourceId: Long = 1,
        favourite: Boolean = false,
        played: Boolean = false,
    ): Channel {
        val id = next++
        return Channel(
            id = id,
            sourceId = sourceId,
            name = name,
            number = id.toInt(),
            group = group,
            favourite = favourite,
            streamUrl = "http://example.com/live/$id.ts",
            xtreamStreamId = null,
            archiveDays = 0,
            matchedXmltvId = null,
            lastPlayedAt = if (played) 1L else null,
        )
    }

    @Test
    fun aLoopChannelIsInA247GroupAndNotADividerWhateverTheGuideSays() {
        val comedy = channel("24/7 HARBOUR LIGHTS", "24/7 COMEDY VIP")
        val shows = listOf(
            comedy,
            channel("### 24/7 COMEDY VIP", "24/7 COMEDY VIP"),
            channel("Harbour News", "UK| NEWS"),
            channel("24/7 PICKED OUT", "24/7 DRAMA VIP", sourceId = 2),
            channel("24/7 LEFT OUT", "24/7 CRIME VIP", sourceId = 2),
        )
        val picks = setOf(GroupKey(2, "24/7 DRAMA VIP"))

        // The source with picks shows only its picked group; no guide data is no reason to hide a loop channel.
        assertEquals(listOf("24/7 HARBOUR LIGHTS", "24/7 PICKED OUT"), LoopChannels.listed(shows, picks).map { it.name })
    }

    @Test
    fun genreChipsDropTheLabelsAndLeadWithAllThenTheBiggest() {
        val channels = listOf(
            channel("24/7 A", "24/7 KIDS/FAMILY VIP"),
            channel("24/7 B", "24/7 COMEDY VIP"),
            channel("24/7 C", "24/7 COMEDY VIP"),
            channel("24/7 D", "24/7 ACTION & ADVENTURE VIP"),
            channel("24/7 E", "24/7 HARBOUR ᴿᴬᵂ 60ᶠᵖˢ"),
            channel("24/7 F", "24/7 HARBOUR RAW 60fps"),
            channel("24/7 G", "24/7 HARBOUR+ VIP"),
        )

        assertEquals(
            listOf("All", "Comedy", "Harbour", "Action & Adventure", "Harbour+", "Kids/Family"),
            LoopChannels.genres(channels),
        )
    }

    @Test
    fun aShowNameLosesItsLabelsAndKeepsSmallWordsLowerCase() {
        assertEquals("King of the Hill", LoopChannels.showName(channel("US| 24/7 KING OF THE HILL", "24/7 SHOWS VIP")))
        assertEquals("Harbour Lights", LoopChannels.showName(channel("24/7 Harbour Lights", "24/7 SHOWS VIP")))
        assertEquals("The Harbour Show", LoopChannels.showName(channel("24/7 THE HARBOUR SHOW", "24/7 SHOWS VIP")))

        val ordered = LoopChannels.ordered(
            listOf(
                channel("24/7 ZED", "24/7 SHOWS VIP"),
                channel("24/7 BEACON", "24/7 SHOWS VIP"),
                channel("24/7 MARINA", "24/7 SHOWS VIP", favourite = true),
                channel("24/7 ANCHOR", "24/7 SHOWS VIP"),
            ),
        )
        assertEquals(listOf("Marina", "Anchor", "Beacon", "Zed"), ordered.map { LoopChannels.showName(it) })
    }

    @Test
    fun aLoopLabelCountsAnywhereAfterProviderLabels() {
        for (group in listOf("EN| 24/7 CARTOONS", "|UK| 24/7 Kids", "24-7 KIDS", "24 / 7 Comedy", "24x7 Shows", "24/7 COMEDY VIP", "[EN] 24/7 Cartoons", "US| NEWS 24/7")) {
            assertEquals(group, true, LoopChannels.inLoopGroup(group))
        }
        for (group in listOf("1247 Music", "24/70 Music", "UK| NEWS", "")) {
            assertEquals(group, false, LoopChannels.inLoopGroup(group))
        }
    }

    @Test
    fun aGenreDropsTheProviderLabelAndTheLoopLabel() {
        assertEquals("Cartoons", LoopChannels.genreOf("EN| 24/7 CARTOONS"))
        assertEquals("Kids", LoopChannels.genreOf("|UK| 24-7 Kids"))
        assertEquals("Comedy", LoopChannels.genreOf("24 / 7 Comedy"))
        assertEquals("Comedy", LoopChannels.genreOf("24/7 COMEDY VIP"))
        assertEquals("News", LoopChannels.genreOf("US| NEWS 24/7"))
        assertEquals("Cartoons", LoopChannels.genreOf("[EN] 24/7 Cartoons"))
    }
}
