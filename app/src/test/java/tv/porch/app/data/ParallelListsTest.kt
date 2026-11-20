package tv.porch.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.porch.app.domain.Kinds

class ParallelListsTest {
    private val names = listOf("UK: Example Sports HD", "=== Example Divider ===", "Example News 24", "FR| Chaine Exemple", "Example Kids (HD)", "Plain Example")
    private val groups = listOf("Sport", "News", "Kids", "FR: Chaines")

    private fun entities(count: Int) = List(count) { i ->
        ChannelEntity(
            id = i + 1L,
            sourceId = 1L + i % 2,
            identity = "id-$i",
            name = names[i % names.size] + " $i",
            number = i,
            tvgId = null,
            tvgName = null,
            groupName = groups[i % groups.size],
            groupOverride = null,
            logoUrl = null,
            streamUrl = "http://example.com/live/$i.ts",
            xtreamStreamId = null,
            archiveDays = 0,
            favourite = i % 7 == 0,
            matchedXmltvId = null,
            lastPlayedAt = null,
        )
    }

    @Test
    fun parallelListIsTheSameListAsTheSequentialOne() {
        val rows = entities(9_000)
        val hidden = setOf(1L to "Kids")
        val languages = mapOf(2L to "EN")

        val together = rows.visible(hidden, languages)
        val split = runBlocking { rows.visibleInParallel(hidden, languages, parts = 3) }

        assertTrue(together.size in 1 until rows.size)
        assertEquals(together, split)
    }

    @Test
    fun parallelKindsAreTheSameKindsAsTheSequentialOnes() {
        val channels = entities(9_000).map { it.toModel() }

        val split = runBlocking { Kinds.indexInParallel(channels, parts = 3) }

        assertTrue(split.isNotEmpty())
        assertEquals(Kinds.index(channels), split)
    }
}
