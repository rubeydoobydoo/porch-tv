package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchRepeatsTest {
    @Test
    fun onlyNamesSeenTwiceAreRepeated() {
        val repeated = SearchRepeats.repeated(listOf("Sample One" to 1L, "sample one " to 2L, "Sample Two" to 1L))
        assertEquals(setOf("sample one"), repeated)
    }

    @Test
    fun oneChannelShownTwiceIsNotRepeated() {
        val repeated = SearchRepeats.repeated(listOf("Sample One" to 1L, "Sample One" to 1L))
        assertEquals(emptySet<String>(), repeated)
    }

    @Test
    fun repeatedRowsGetTheSourceName() {
        val repeated = setOf("sample one")
        assertEquals("Ch 4  ·  Box A", SearchRepeats.meta("Ch 4", "Sample One", repeated, "Box A"))
        assertEquals("Box B", SearchRepeats.meta("", "Sample One", repeated, "Box B"))
    }

    @Test
    fun aSingleRowKeepsItsMeta() {
        assertEquals("Ch 7", SearchRepeats.meta("Ch 7", "Sample Two", setOf("sample one"), "Box A"))
    }
}
