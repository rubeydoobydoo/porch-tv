package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelsChoiceTest {
    private val r = GroupChoice.RESTRICTED
    private val counts = mapOf("AU: Harbour Sport" to 10, "AU: Harbour News" to 5, "FR: Harbour Sport" to 7, "Harbour Local" to 3)
    private val all = { counts.keys }

    @Test
    fun showingWhenUnrestrictedRestrictedOrEveryPickIsStale() {
        // No rows: everything shows except what is hidden.
        assertTrue(ChannelsChoice.showing("FR: Harbour Sport", emptySet(), emptySet()))
        assertFalse(ChannelsChoice.showing("FR: Harbour Sport", emptySet(), setOf("FR: Harbour Sport")))
        // Restricted: only a picked group that is not hidden shows.
        val chosen = setOf(r, "AU: Harbour Sport", "AU: Harbour News")
        assertTrue(ChannelsChoice.showing("AU: Harbour Sport", chosen, emptySet()))
        assertFalse(ChannelsChoice.showing("FR: Harbour Sport", chosen, emptySet()))
        assertFalse(ChannelsChoice.showing("AU: Harbour News", chosen, setOf("AU: Harbour News")))
        // Restricted with only the reserved row, or only picks for groups that are gone: nothing shows.
        assertFalse(ChannelsChoice.showing("AU: Harbour Sport", setOf(r), emptySet()))
        assertFalse(ChannelsChoice.showing("AU: Harbour Sport", setOf(r, "Gone group"), emptySet()))

        // An older TV's picks with no reserved row: if every pick has gone, it shows everything, as Home does.
        val present = setOf("AU: Harbour Sport", "FR: Harbour Sport")
        assertEquals(emptySet<String>(), ChannelsChoice.effective(setOf("Gone group"), present))
        assertEquals(setOf(r, "Gone group"), ChannelsChoice.effective(setOf(r, "Gone group"), present))
        // A filter that rejects every old pick keeps what the filter let through, not nothing.
        val converted = ChannelsChoice.convertFilter(present, "AU", setOf("FR: Harbour Sport"))
        assertEquals(setOf("AU: Harbour Sport", r), converted.addPicks)
        // The count is the channels in showing groups, and a divider is not a channel.
        val rows = listOf(
            GroupedName("AU: Harbour Sport", "Harbour One"),
            GroupedName("AU: Harbour Sport", "Harbour Two"),
            GroupedName("AU: Harbour Sport", "#### SPORT ####"),
            GroupedName("AU: Harbour News", "Harbour Three", divider = false),
            GroupedName("FR: Harbour Sport", "Harbour Four"),
            GroupedName("Dividers only", "=== X ==="),
        )
        val grouped = ChannelsChoice.groupCounts(rows)
        assertEquals(mapOf("AU: Harbour Sport" to 2, "AU: Harbour News" to 1, "FR: Harbour Sport" to 1), grouped)
        assertEquals(4, ChannelsChoice.keeping(grouped, emptySet(), emptySet()))
        assertEquals(2, ChannelsChoice.keeping(grouped, setOf(r, "AU: Harbour Sport"), emptySet()))
        assertEquals(0, ChannelsChoice.keeping(grouped, setOf(r), emptySet()))
    }

    @Test
    fun switchingOneGroupIncludingTheLastOne() {
        // Unrestricted: hiding makes the source restricted, keeping everything that shows now except the target.
        val first = ChannelsChoice.change(listOf("FR: Harbour Sport"), false, emptySet(), setOf("Harbour Local"), all)
        assertEquals(setOf(r, "AU: Harbour Sport", "AU: Harbour News"), first.addPicks)
        assertEquals(emptySet<String>(), first.removePicks)
        // Unrestricted: showing a hidden group only removes its hidden row.
        assertEquals(
            ChannelsChange(removeHidden = setOf("FR: Harbour Sport")),
            ChannelsChoice.change(listOf("FR: Harbour Sport"), true, emptySet(), setOf("FR: Harbour Sport"), all),
        )
        // Restricted: hiding removes the pick, and showing adds it and clears any hidden row.
        val chosen = setOf(r, "AU: Harbour Sport", "AU: Harbour News")
        assertEquals(ChannelsChange(removePicks = setOf("AU: Harbour News")), ChannelsChoice.change(listOf("AU: Harbour News"), false, chosen, emptySet(), all))
        assertEquals(
            ChannelsChange(removeHidden = setOf("FR: Harbour Sport"), addPicks = setOf("FR: Harbour Sport")),
            ChannelsChoice.change(listOf("FR: Harbour Sport"), true, chosen, setOf("FR: Harbour Sport"), all),
        )
        // The last group: its pick goes, the reserved row stays, so nothing floods back. No hidden rows are written.
        val lastChange = ChannelsChoice.change(listOf("AU: Harbour Sport"), false, setOf(r, "AU: Harbour Sport"), emptySet(), all)
        assertEquals(ChannelsChange(removePicks = setOf("AU: Harbour Sport")), lastChange)
        // An older install with picks but no reserved row gets the row on its first change.
        val older = ChannelsChoice.change(listOf("AU: Harbour Sport"), false, setOf("AU: Harbour Sport"), emptySet(), all)
        assertEquals(ChannelsChange(removePicks = setOf("AU: Harbour Sport"), addPicks = setOf(r)), older)
    }

    @Test
    fun switchingAWholeLanguageDecidesFromTheSavedRows() {
        val au = listOf("AU: Harbour Sport", "AU: Harbour News")
        // Unrestricted, both showing: all go hidden, by picking everything else.
        val hide = ChannelsChoice.change(au, null, emptySet(), emptySet(), all)
        assertEquals(setOf(r, "FR: Harbour Sport", "Harbour Local"), hide.addPicks)
        // One of the two showing: still all go hidden.
        assertEquals(
            ChannelsChange(removePicks = setOf("AU: Harbour Sport")),
            ChannelsChoice.change(au, null, setOf(r, "AU: Harbour Sport", "FR: Harbour Sport"), emptySet(), all),
        )
        // None showing: all go showing, each picked when restricted.
        assertEquals(
            ChannelsChange(addPicks = au.toSet()),
            ChannelsChoice.change(au, null, setOf(r, "FR: Harbour Sport"), emptySet(), all),
        )
        assertEquals(ChannelsChange(removeHidden = au.toSet()), ChannelsChoice.change(au, null, emptySet(), au.toSet(), all))
        // A second press on the same saved rows reaches the same wanted state (no flip-flop on a stale screen).
        assertEquals(
            ChannelsChoice.change(au, false, setOf(r, "FR: Harbour Sport", "AU: Harbour Sport"), emptySet(), all),
            ChannelsChoice.change(au, null, setOf(r, "FR: Harbour Sport", "AU: Harbour Sport"), emptySet(), all),
        )
    }

    @Test
    fun languagesSayCountryNamesAndSitTvCountryFirst() {
        val rows = mapOf(
            "XX: Odd" to 1, "FR: Cinema" to 2, "Sport" to 3, "DE: Kino" to 4, "NZ: Sport" to 5,
            "UK: News" to 6, "US: News" to 7, "AU: News" to 8, "EN: Movies" to 9,
        )
        val au = ChannelsChoice.languages(rows, "AU")
        assertEquals(listOf("AU", "US", "UK", "NZ", "DE", "EN", "FR", "XX", "Other"), au.map { it.label })
        assertEquals(
            listOf("Australia", "United States", "United Kingdom", "New Zealand", "Germany", "EN", "France", "XX", "Other"),
            au.map { it.name },
        )
        // A TV in Britain reports GB; the guide counts that as UK.
        assertEquals(listOf("UK", "US", "NZ"), ChannelsChoice.languages(rows, "GB").map { it.label }.take(3))
    }
}
