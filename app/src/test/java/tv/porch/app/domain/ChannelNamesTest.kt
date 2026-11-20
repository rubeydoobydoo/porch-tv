package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelNamesTest {
    private data class Row(val raw: String, val title: String, val tag: String?, val code: String?)

    @Test
    fun displayStripsTagsCodesAndDecoration() {
        val rows = listOf(
            Row("TR| RADYO FENOMEN", "Radyo Fenomen", "TR", null),
            Row("(FLSP 003) | wrestling: WWE Raw", "Wrestling: WWE Raw", null, "FLSP 003"),
            Row("US| ESPN HD", "ESPN", "US", null),
            Row("HD+", "HD+", null, null),
            Row("#### PRIME ᴿᴬᵂ ⁶⁰ᶠᵖˢ #####", "Prime", null, null),
            Row("TR| SLOW TÜRK", "Slow Türk", "TR", null),
            Row("BBC One HD", "BBC One", null, null),
            Row("EN: Sky Sports Main Event (1080p)", "Sky Sports Main Event", "EN", null),
            Row("BBC: World News", "BBC: World News", null, null),
            Row("ABC: Kids", "ABC: Kids", null, null),
            Row("AC/DC TV", "AC/DC TV", null, null),
            Row("UK: 5", "5", "UK", null),
            Row("US| ESPN", "ESPN", "US", null),
            Row("Prime| CBS Sports Golazo Network", "CBS Sports Golazo Network", "PRIME", null),
        )
        for (row in rows) {
            val shown = ChannelNames.display(row.raw)
            assertEquals(row.raw, DisplayName(row.title, row.tag, row.code), shown)
        }
    }

    @Test
    fun dividersAreSpotted() {
        val rows = listOf(
            "##### FLO NETWORK #####" to true,
            "####### EP" to true,
            "---" to true,
            "ESPN" to false,
            "(FLSP 003) | wrestling" to false,
            "TR| 24" to false,
            "US| E!" to false,
            "UK: 5" to false,
            "5 HD" to false,
            "UK: W" to false,
            "HD+" to false,
            "HD" to true,
        )
        for ((raw, expected) in rows) assertEquals(raw, expected, ChannelNames.isDivider(raw))
    }

    @Test(timeout = 2000)
    fun longDigitRunsInBracketsCleanQuickly() {
        val raw = "Harbor News (" + "1".repeat(40) + "x)"
        assertEquals(raw, ChannelNames.display(raw).title)
    }

    @Test
    fun groupKeepsTagAndTitleCases() {
        assertEquals(DisplayName("Flo Network", "US", null), ChannelNames.group("US| FLO NETWORK"))
        assertEquals(DisplayName("Sports", null, null), ChannelNames.group("Sports"))
    }

    @Test
    fun channelShownTitleFallsBackToName() {
        assertEquals("Channel 7", channel("Channel 7").shownTitle)
        assertEquals("Radyo Fenomen", channel("TR| RADYO FENOMEN", title = "Radyo Fenomen").shownTitle)
    }

    private fun channel(name: String, title: String = "") = Channel(
        id = 1,
        sourceId = 1,
        name = name,
        number = 7,
        group = "Radio",
        favourite = false,
        streamUrl = "http://example.com/live/1.ts",
        xtreamStreamId = null,
        archiveDays = 0,
        matchedXmltvId = null,
        lastPlayedAt = null,
        title = title,
    )
}
