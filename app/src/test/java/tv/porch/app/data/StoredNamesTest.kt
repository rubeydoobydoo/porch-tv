package tv.porch.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.porch.app.domain.ChannelNames
import tv.porch.app.parse.ParsedChannel

class StoredNamesTest {
    private fun parsed(name: String) = ParsedChannel(
        identity = "m3u:http://example.com/live/1.ts", name = name, number = 1, tvgId = null, tvgName = null,
        group = "Sport", logo = null, streamUrl = "http://example.com/live/1.ts", xtreamStreamId = null, archiveDays = 0,
    )

    private fun row(name: String, title: String? = null, tag: String? = null, code: String? = null, divider: Boolean? = null) = ChannelRow(
        id = 1, sourceId = 1, name = name, number = 1, groupName = "Sport", groupOverride = null, favourite = false,
        streamUrl = "http://example.com/live/1.ts", xtreamStreamId = null, archiveDays = 0, matchedXmltvId = null, lastPlayedAt = null,
        shownTitle = title, shownTag = tag, shownCode = code, divider = divider,
    )

    @Test
    fun importStoresWhatTheCleanerGives() {
        val name = "UK: Example Sports HD"
        val entity = parsed(name).toEntity(1, null, null)
        val shown = ChannelNames.display(name)
        assertEquals(shown.title, entity.shownTitle)
        assertEquals(shown.tag, entity.shownTag)
        assertEquals(shown.code, entity.shownCode)
        assertEquals(false, entity.divider)
    }

    @Test
    fun importStoresThatADividerIsOne() {
        assertEquals(true, parsed("=== Example ===").toEntity(1, null, null).divider)
    }

    @Test
    fun storedValuesAreUsedWithoutCleaningAgain() {
        val channel = row("UK: Example Sports HD", title = "Stored Title", tag = "Stored Tag", code = "SC", divider = false).toModel()
        assertEquals("Stored Title", channel.title)
        assertEquals("Stored Tag", channel.tag)
        assertEquals("SC", channel.code)
    }

    @Test
    fun anOldRowWithoutStoredNamesIsStillCleaned() {
        val name = "UK: Example Sports HD"
        val channel = row(name).toModel()
        assertEquals(ChannelNames.display(name).title, channel.title)
        assertTrue(channel.title.isNotBlank())
    }

    @Test
    fun storedDividerDecidesWhetherARowIsShown() {
        val rows = listOf(
            row("Example One", divider = true),
            row("Example Two", divider = false),
        )
        assertEquals(listOf("Example Two"), rows.visible(emptySet(), emptyMap()).map { it.name })
    }

    @Test
    fun anOldRowStillFallsBackToTheCleanerForDividers() {
        val rows = listOf(row("=== Example ==="), row("Example Two"))
        assertEquals(listOf("Example Two"), rows.visible(emptySet(), emptyMap()).map { it.name })
    }
}
