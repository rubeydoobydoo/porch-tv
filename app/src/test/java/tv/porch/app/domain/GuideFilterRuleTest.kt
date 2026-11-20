package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GuideFilterRuleTest {
    private val groups = listOf("News", "Sport")

    @Test
    fun opensOnAllWhenThereAreChannelsButNoFavourites() {
        assertEquals("all", GuideFilterRule.resolve("fav", false, groups, channelCount = 5, hasFavourites = false))
    }

    @Test
    fun opensOnFavouritesWhenThereAreSome() {
        assertEquals("fav", GuideFilterRule.resolve("fav", false, groups, channelCount = 5, hasFavourites = true))
    }

    @Test
    fun staysOnFavouritesWhenTheViewerPickedItThemselves() {
        assertEquals("fav", GuideFilterRule.resolve("fav", true, groups, channelCount = 5, hasFavourites = false))
    }

    @Test
    fun staysOnFavouritesBeforeAnyChannelsHaveLoaded() {
        assertEquals("fav", GuideFilterRule.resolve("fav", false, emptyList(), channelCount = 0, hasFavourites = false))
    }

    @Test
    fun keepsAllAndAGroupThatStillExists() {
        assertEquals("all", GuideFilterRule.resolve("all", true, groups, channelCount = 5, hasFavourites = false))
        assertEquals("Sport", GuideFilterRule.resolve("Sport", true, groups, channelCount = 5, hasFavourites = false))
    }

    @Test
    fun aGroupThatVanishedFallsBackToFavouritesThenFirstGroupThenAll() {
        assertEquals("fav", GuideFilterRule.resolve("Movies", true, groups, channelCount = 5, hasFavourites = true))
        assertEquals("News", GuideFilterRule.resolve("Movies", true, groups, channelCount = 5, hasFavourites = false))
        assertEquals("all", GuideFilterRule.resolve("Movies", true, emptyList(), channelCount = 0, hasFavourites = false))
    }

    @Test
    fun aFilterThatMatchesNoChipFallsBackToAllNotAnEmptyFavouritesChip() {
        val chips = listOf("fav", "cc:AU", "kind:news", "all")
        assertEquals("all", GuideFilterRule.resolve("cc:NZ", true, chips, channelCount = 5, hasFavourites = false))
    }
}
