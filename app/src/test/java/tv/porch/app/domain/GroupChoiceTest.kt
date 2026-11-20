package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.porch.app.data.ChannelEntity
import tv.porch.app.data.toModel

class GroupChoiceTest {
    private fun entity(id: Long, sourceId: Long, group: String, override: String? = null, favourite: Boolean = false, played: Boolean = false) = ChannelEntity(
        id = id,
        sourceId = sourceId,
        identity = "id-$id",
        name = "Channel $id",
        number = id.toInt(),
        tvgId = null,
        tvgName = null,
        groupName = group,
        groupOverride = override,
        logoUrl = null,
        streamUrl = "http://example.com/live/$id.ts",
        xtreamStreamId = null,
        archiveDays = 0,
        favourite = favourite,
        matchedXmltvId = "key-$id",
        lastPlayedAt = if (played) 1L else null,
    )

    @Test
    fun emptyChoiceShowsAllAndChoiceFiltersByEffectiveGroup() {
        val channels = listOf(
            entity(1, 1, "News"),
            entity(2, 1, "Sport"),
            entity(3, 2, "News"),
            entity(4, 2, "Sport", override = "News"),
        ).map { it.toModel() }

        assertEquals(listOf(1L, 2L, 3L, 4L), GroupChoice.filter(channels, emptySet()).map { it.id })
        // Channel 4 was moved into News on source 2, so it counts as News there.
        assertEquals(listOf(1L, 3L, 4L), GroupChoice.filter(channels, setOf(GroupKey(1, "News"), GroupKey(2, "News"))).map { it.id })
        // ...and it is not Sport any more.
        assertEquals(listOf(1L), GroupChoice.filter(channels, setOf(GroupKey(1, "News"), GroupKey(2, "Sport"))).map { it.id })
        // The choice is per source: picks only on source 1 leave every source 2 channel showing.
        assertEquals(listOf(1L, 3L, 4L), GroupChoice.filter(channels, setOf(GroupKey(1, "News"))).map { it.id })
    }

    @Test
    fun aFavouriteInAnUnchosenGroupStillShows() {
        val channels = listOf(
            entity(1, 1, "News"),
            entity(2, 1, "Sport", favourite = true),
            entity(3, 1, "Sport"),
            entity(4, 1, "Sport", played = true),
        ).map { it.toModel() }

        // 2 is a favourite and 4 was played; 3 is neither.
        assertEquals(listOf(1L, 2L, 4L), GroupChoice.filter(channels, setOf(GroupKey(1, "News"))).map { it.id })
    }

    @Test
    fun aPickForAGroupThatIsGoneIsIgnored() {
        val channels = listOf(entity(1, 1, "News"), entity(2, 1, "Sport")).map { it.toModel() }
        val chosen = setOf(GroupKey(1, "News"), GroupKey(1, "Renamed away"))

        assertEquals(setOf(GroupKey(1, "News")), GroupChoice.live(chosen, channels))
        // Only the stale pick left: nothing counts as picked, so everything shows.
        val live = GroupChoice.live(setOf(GroupKey(1, "Renamed away")), channels)
        assertEquals(emptySet<GroupKey>(), live)
        assertEquals(listOf(1L, 2L), GroupChoice.filter(channels, live).map { it.id })
    }
}
