package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GuideAllChannelsTest {
    private fun channel(id: Long, matched: String?, group: String = "24/7 Example", sourceId: Long = 1, title: String = "") = Channel(
        id = id,
        sourceId = sourceId,
        name = "Channel $id",
        number = id.toInt(),
        group = group,
        favourite = false,
        streamUrl = "http://example.com/live/$id.ts",
        xtreamStreamId = null,
        archiveDays = 0,
        matchedXmltvId = matched,
        lastPlayedAt = null,
        title = title,
    )

    private val mixed = listOf(channel(1, "key-1"), channel(2, null), channel(3, null, sourceId = 2), channel(4, "key-4"))

    @Test
    fun aPickedGroupShowsEveryChannelWhateverTheSwitchSays() {
        assertEquals(listOf(1L, 2L, 3L, 4L), GuideCoverage.forFilter(mixed, "24/7 Example", showWithoutGuide = false).map { it.id })
    }

    @Test
    fun allAndFavouritesStillHideUnmatchedChannelsWhenTheSwitchIsOff() {
        assertEquals(listOf(1L, 4L), GuideCoverage.forFilter(mixed, "all", showWithoutGuide = false).map { it.id })
        assertEquals(listOf(1L, 4L), GuideCoverage.forFilter(mixed, "fav", showWithoutGuide = false).map { it.id })
    }

    @Test
    fun allAndFavouritesKeepUnmatchedChannelsWhenTheSwitchIsOnOrTheSourceHasNoGuide() {
        assertEquals(listOf(1L, 2L, 3L, 4L), GuideCoverage.forFilter(mixed, "all", showWithoutGuide = true).map { it.id })
        assertEquals(listOf(1L, 3L, 4L), GuideCoverage.forFilter(mixed, "all", showWithoutGuide = false, noGuideSources = setOf(2L)).map { it.id })
    }

    @Test
    fun onlyAllAndFavouritesCanBeEmptiedByTheGuideSwitch() {
        assertEquals(true, GuideCoverage.hidesUnmatched("all"))
        assertEquals(true, GuideCoverage.hidesUnmatched("fav"))
        assertEquals(false, GuideCoverage.hidesUnmatched("24/7 Example"))
    }

    @Test
    fun theAllChipShowsOnlyMyGroupsOnceSomeArePicked() {
        val list = listOf(channel(1, "key-1", group = "Sport"), channel(2, "key-2", group = "Unpicked"), channel(3, "key-3", group = "News"))
        val chosen = setOf(GroupKey(1, "Sport"), GroupKey(1, "News"))
        assertEquals(listOf(1L, 3L), GuideCoverage.listed(list, chosen, "all", showWithoutGuide = true).map { it.id })
    }

    @Test
    fun aChannelWithNoGuideGetsOneAllDayBarNamedAfterIt() {
        assertEquals("Example Loop · on all day", AllDayText.bar(channel(1, null, title = "Example Loop").shownTitle))
        assertEquals("Channel 2 · on all day", AllDayText.bar(channel(2, null).shownTitle))
    }

    @Test
    fun theHeroSaysItIsOnAllDay() {
        assertEquals("On all day · OK plays", AllDayText.HERO_STATUS)
    }
}
