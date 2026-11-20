package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GuideChipOrderTest {
    @Test
    fun groupsWithGuideDataComeBeforeGroupsWithout() {
        // Only the first few chips show, so loop-only groups must not push real ones off the row.
        val order = GuideCoverage.chipGroups(
            all = listOf("24/7 Alpha", "Sport", "24/7 Beta", "News", "Sport"),
            withGuide = setOf("Sport", "News"),
        )
        assertEquals(listOf("News", "Sport", "24/7 Alpha", "24/7 Beta"), order)
    }
}
