package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FavouritePicksTest {
    private fun channel(id: Long, name: String, guide: Boolean = true, logo: Boolean = true) = Channel(
        id = id, sourceId = 1, name = name, number = id.toInt(), group = "", favourite = false, streamUrl = "http://example.com/$id",
        xtreamStreamId = null, archiveDays = 0, matchedXmltvId = if (guide) "x$id" else null, lastPlayedAt = null,
        logoUrl = if (logo) "http://example.com/$id.png" else null,
    )

    private fun kind(c: Channel): Kind? = if (c.name.startsWith("S")) Kind.Sport else if (c.name.startsWith("N")) Kind.News else null

    @Test
    fun onlyChannelsThatWillLookRightAreOfferedAFewOfEachKind() {
        val sports = (1L..9L).map { channel(it, "S$it") }
        val noGuide = channel(20, "S no guide", guide = false)
        val noLogo = channel(21, "S no logo", logo = false)
        val other = channel(22, "Other")
        val sections = FavouritePicks.sections(sports + noGuide + noLogo + other + channel(23, "N one"), ::kind)
        assertEquals(listOf(Kind.Sport, Kind.News), sections.map { it.kind })
        assertEquals(FavouritePicks.PER_KIND, sections[0].channels.size)
        assertEquals(listOf(23L), sections[1].channels.map { it.id })
    }

    @Test
    fun theMostCarriedChannelsComeFirstEachOfferedOnce() {
        val copies = listOf(channel(1, "S Big"), channel(2, "S Big"), channel(3, "S Big"))
        val sections = FavouritePicks.sections(copies + channel(4, "S Alpha") + channel(5, "S 24/7 Loop"), ::kind)
        assertEquals(listOf("S Big", "S Alpha"), sections[0].channels.map { it.name })
    }
}
