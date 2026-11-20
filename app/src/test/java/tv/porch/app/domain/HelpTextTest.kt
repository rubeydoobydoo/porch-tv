package tv.porch.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpTextTest {
    private val lines = HelpText.columns.flatMap { it.second }

    @Test
    fun everyLineFitsOneLine() {
        lines.forEach { assertTrue("too long: $it", it.length <= HelpText.MAX_LINE) }
    }

    @Test
    fun holdOkIsTheQuickMenu() {
        assertTrue(lines.any { it.contains("Hold OK") && it.contains("quick menu") })
        assertFalse(lines.any { it.contains("Hold OK pauses") || it.contains("to favourite it") })
    }
}
