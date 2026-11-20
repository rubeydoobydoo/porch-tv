package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ChoiceLabelsTest {
    @Test
    fun aChosenItemCarriesATick() {
        assertEquals("✓ Sport", ChoiceLabels.chosen("Sport", selected = true))
        assertEquals("Sport", ChoiceLabels.chosen("Sport", selected = false))
    }

    @Test
    fun aGroupSaysWhatItIsNowNotWhatOkWouldDo() {
        assertEquals("Hidden", ChoiceLabels.group(hidden = true))
        assertEquals("Showing", ChoiceLabels.group(hidden = false))
    }
}
