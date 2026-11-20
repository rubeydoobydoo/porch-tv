package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterPickTest {
    private val groups = listOf(
        "AU: Harbour Sport", "NZ | Harbour News", "UK: Harbour Drama", "US: Harbour Kids", "EN: Harbour Films",
        "GB: Harbour Docs", "VOD: Harbour Movies", "Replay | Harbour Sport", "24/7 Harbour Comedy", "FORMULA 1 Harbour",
        "ASIA | Harbour Tamil", "EXYU| 24/7", "ASIA | 24/7 Harbour India", "FR: Harbour Sport", "DE: Harbour News",
        "Hindi Harbour Movies", "AU: Harbour Radio", "Radio Harbour", "VOD: Harbour Radio", "CA: Harbour Hockey",
    )

    @Test
    fun keepsHomeAndContentLabelsAndDropsOtherPlacesAndRadio() {
        assertEquals(
            setOf(
                "AU: Harbour Sport", "NZ | Harbour News", "UK: Harbour Drama", "US: Harbour Kids", "EN: Harbour Films",
                "GB: Harbour Docs", "VOD: Harbour Movies", "Replay | Harbour Sport", "24/7 Harbour Comedy", "FORMULA 1 Harbour",
            ),
            StarterPick.groups(groups, ""),
        )
        // The TV's own country is kept too, and a radio group goes whatever its label.
        assertTrue("CA: Harbour Hockey" in StarterPick.groups(groups, "ca"))
        assertFalse("FR: Harbour Sport" in StarterPick.groups(groups, "CA"))
        assertFalse("CA: Harbour Radio" in StarterPick.groups(listOf("CA: Harbour Radio"), "CA"))
    }

    @Test
    fun onlyAFirstSaveWithNoPicksPicks() {
        assertTrue(StarterPick.shouldPick(hadChannels = false, hasPicks = false))
        assertFalse(StarterPick.shouldPick(hadChannels = true, hasPicks = false))
        assertFalse(StarterPick.shouldPick(hadChannels = false, hasPicks = true))
        assertFalse(StarterPick.shouldPick(hadChannels = true, hasPicks = true))
    }
}
