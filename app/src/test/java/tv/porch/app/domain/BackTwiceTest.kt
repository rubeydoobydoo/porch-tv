package tv.porch.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackTwiceTest {
    @Test
    fun secondBackWithinTwoSecondsLeavesAndALateOneWarnsAgain() {
        val back = BackTwice(windowMs = 2_000)
        assertFalse(back.press(10_000))
        assertTrue(back.warning(11_999))
        assertFalse(back.warning(12_000))
        assertTrue(back.press(11_000))
        assertFalse(back.press(20_000))
        assertFalse(back.press(22_000))
        assertTrue(back.press(23_000))
    }
}
