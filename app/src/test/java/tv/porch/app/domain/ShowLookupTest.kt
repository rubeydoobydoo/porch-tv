package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShowLookupTest {
    @Test
    fun theSearchTextIsTheShowNameWithoutSeasonCopyBracketsOrChannelWords() {
        val cases = mapOf(
            "American Dad 3" to "American Dad",
            "24" to "24",
            "Ben 10" to "Ben 10",
            "Harbour Lights (UK) HD" to "Harbour Lights",
            "Harbour Lights [Kids]" to "Harbour Lights",
            "Harbour Lights Season 12" to "Harbour Lights",
            "Harbour Lights S2" to "Harbour Lights",
            "Harbour Lights 24/7" to "Harbour Lights",
            "Harbour Lights Channel" to "Harbour Lights",
            "Harbour Lights   Marathon" to "Harbour Lights",
            "4K Harbour Lights FHD" to "Harbour Lights",
            "A N T Farm" to "ANT Farm",
            "I Love Lucy" to "I Love Lucy",
            "A Different World" to "A Different World",
            "  " to "",
        )
        for ((name, expected) in cases) assertEquals(name, expected, ShowLookup.searchText(name))
    }

    @Test
    fun aPosterIsTheMediumImageOnHttpsAndNothingElseCounts() {
        val withImage = """{"name":"Harbour Lights","image":{"medium":"https://static.example.com/p/1.jpg","original":"https://static.example.com/p/1o.jpg"},"summary":"<p>x</p>"}"""
        val noImage = """{"name":"Harbour Lights","image":null,"summary":null}"""
        val missingImage = """{"name":"Harbour Lights"}"""
        val plainHttp = """{"name":"Harbour Lights","image":{"medium":"http://static.example.com/p/1.jpg"}}"""
        val otherShow = """{"name":"Quarry Road","image":{"medium":"https://static.example.com/p/2.jpg"}}"""
        val accents = """{"name":"Pokémon","image":{"medium":"https://static.example.com/p/3.jpg"}}"""

        assertEquals("https://static.example.com/p/1.jpg", ShowLookup.posterUrl(withImage, "Harbour Lights"))
        assertNull(ShowLookup.posterUrl(noImage, "Harbour Lights"))
        assertNull(ShowLookup.posterUrl(missingImage, "Harbour Lights"))
        assertNull(ShowLookup.posterUrl(plainHttp, "Harbour Lights"))
        // A fuzzy match for some other show is worse than no poster.
        assertNull(ShowLookup.posterUrl(otherShow, "Harbour Lights"))
        assertEquals("https://static.example.com/p/3.jpg", ShowLookup.posterUrl(accents, "Pokemon"))
    }

    @Test
    fun aLogoSharedByFiveOrMoreChannelsIsGenericAndNotUsed() {
        var id = 0L
        fun show(logo: String?) = Channel(
            id = ++id, sourceId = 1, name = "Show $id", number = id.toInt(), group = "24/7 COMEDY VIP",
            favourite = false, streamUrl = "http://example.com/$id.ts", xtreamStreamId = null, archiveDays = 0,
            matchedXmltvId = null, lastPlayedAt = null, logoUrl = logo,
        )
        val generic = "https://img.example.com/247.png"
        val few = "https://img.example.com/four.png"
        val channels = List(5) { show(generic) } + List(4) { show(few) } + show("https://img.example.com/own.png") + show(null)

        val shared = LoopChannels.sharedLogos(channels)

        assertEquals(setOf(generic), shared)
        assertNull(LoopChannels.ownLogo(channels[0], shared))
        assertEquals(few, LoopChannels.ownLogo(channels[5], shared))
        assertEquals("https://img.example.com/own.png", LoopChannels.ownLogo(channels[9], shared))
        assertNull(LoopChannels.ownLogo(channels[10], shared))
    }
}
