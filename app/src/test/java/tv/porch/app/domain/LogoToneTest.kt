package tv.porch.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogoToneTest {
    private val clear = 0x00000000
    private val white = 0xFFFFFFFF.toInt()
    private val navy = 0xFF14306A.toInt()

    @Test
    fun aMostlyWhiteLogoNeedsTheDarkPlate() {
        assertTrue(LogoTone.needsDarkPlate(intArrayOf(clear, clear, white, white, white)))
        assertFalse(LogoTone.needsDarkPlate(intArrayOf(clear, navy, navy, white)))
        assertFalse(LogoTone.needsDarkPlate(intArrayOf(clear, clear)))
    }
}
