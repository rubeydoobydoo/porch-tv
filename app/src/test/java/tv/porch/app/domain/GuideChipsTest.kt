package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GuideChipsTest {
    private fun channel(id: Long, name: String, group: String, favourite: Boolean = false, number: Int = id.toInt()) = Channel(
        id = id,
        sourceId = 1,
        name = name,
        number = number,
        group = group,
        favourite = favourite,
        streamUrl = "http://example.com/live/$id.ts",
        xtreamStreamId = null,
        archiveDays = 0,
        matchedXmltvId = "key-$id",
        lastPlayedAt = null,
    )

    private val lineup = listOf(
        channel(1, "Example One", "US| Entertainment"),
        channel(2, "Example Two", "AU| Entertainment"),
        channel(3, "Example Three", "UK| Entertainment"),
        channel(4, "Example Sports", "NZ| Sport"),
        channel(5, "Example News", "AU| News"),
        channel(6, "Example Four", "DE| Entertainment"),
    )

    @Test
    fun oneRowWithFavouritesCountriesKindsThenAllInThatOrder() {
        assertEquals(listOf(Country.AU, Country.US, Country.UK, Country.NZ, Country.CA, Country.IE), ChannelCountry.order("AU"))
        val au = GuideChips.build(lineup, "AU").map { it.label }
        assertEquals(listOf("Favourites", "Australia", "US", "UK", "NZ", "Sport", "News", "All"), au)
        val uk = GuideChips.build(lineup, "GB").map { it.label }
        assertEquals(listOf("Favourites", "UK", "US", "NZ", "Australia", "Sport", "News", "All"), uk)
        // A kind nobody has, and a country nobody has, leave no chip.
        val few = GuideChips.build(lineup.filter { it.id == 2L }, "AU").map { it.label }
        assertEquals(listOf("Favourites", "Australia", "All"), few)
    }

    @Test
    fun allListsFavouritesFirstThenTheirCountryThenNumber() {
        val withFavourite = lineup.map { if (it.id == 6L) it.copy(favourite = true) else it }
        val all = GuideChips.order(withFavourite, GuideChips.ALL, "AU", emptySet()).map { it.id }
        // Favourite (6), then AU (2, 5), US (1), UK (3), NZ (4).
        assertEquals(listOf(6L, 2L, 5L, 1L, 3L, 4L), all)
        // Another chip keeps favourites first, then number.
        val aus = GuideCoverage.view(withFavourite, GuideChips.countryKey(Country.AU))
        assertEquals(listOf(2L, 5L), GuideChips.order(aus, GuideChips.countryKey(Country.AU), "AU", emptySet()).map { it.id })
    }
}
