package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelBadgeTest {
    @Test
    fun contrastFormulaMatchesWcag() {
        // WCAG 2 reference values for white text on these backgrounds.
        assertEquals(21.0, ChannelBadge.contrastWithWhite(0xFF000000), 0.01)
        assertEquals(1.0, ChannelBadge.contrastWithWhite(0xFFFFFFFF), 0.01)
        assertEquals(4.48, ChannelBadge.contrastWithWhite(0xFF777777), 0.02)
    }

    @Test
    fun everyBadgeColourReadsAgainstWhite() {
        val all = ChannelBadge.palette + ChannelBadge.keywordColours
        assertTrue(all.isNotEmpty())
        for (rgb in all) {
            val ratio = ChannelBadge.contrastWithWhite(rgb)
            assertTrue("0x${rgb.toString(16)} gives $ratio", ratio >= 3.0)
        }
    }
}
