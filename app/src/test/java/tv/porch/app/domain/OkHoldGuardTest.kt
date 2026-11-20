package tv.porch.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OkHoldGuardTest {
    @Test
    fun theKeyUpOfTheHoldThatOpenedTheMenuIsSwallowed() {
        val guard = OkHoldGuard()
        assertTrue(guard.swallows(down = true, repeatCount = 3)) // repeats of the hold
        assertTrue(guard.swallows(down = false, repeatCount = 0)) // its key-up must not pick row one
    }

    @Test
    fun aFreshPressPassesThroughDownAndUp() {
        val guard = OkHoldGuard()
        assertTrue(guard.swallows(down = false, repeatCount = 0))
        assertFalse(guard.swallows(down = true, repeatCount = 0))
        assertFalse(guard.swallows(down = false, repeatCount = 0))
    }

    @Test
    fun afterAPickTheNextStrayKeyUpIsSwallowedAgain() {
        val guard = OkHoldGuard()
        guard.swallows(down = true, repeatCount = 0)
        guard.swallows(down = false, repeatCount = 0)
        assertTrue(guard.swallows(down = false, repeatCount = 0))
    }
}
