package tv.porch.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackKeyTest {
    @Test
    fun holdingBackStepsBackOnce() {
        assertTrue(BackKey.fires(keyDown = true, repeatCount = 0))
        assertFalse(BackKey.fires(keyDown = true, repeatCount = 1))
        assertFalse(BackKey.fires(keyDown = false, repeatCount = 0))
    }
}
