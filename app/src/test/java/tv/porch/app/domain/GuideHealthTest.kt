package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GuideHealthTest {
    @Test
    fun outOfDateNeedsTwentyMatchedAndUnderQuarterOnNow() {
        val cases = listOf(
            Triple(19, 0, false), // too few channels to judge
            Triple(20, 4, true), // 20%
            Triple(20, 5, false), // exactly 25% is fine
            Triple(100, 24, true),
            Triple(100, 25, false),
            Triple(0, 0, false),
        )
        for ((matched, onNow, expected) in cases) {
            assertEquals("$matched matched, $onNow on now", expected, GuideHealth.outOfDate(matched, onNow))
        }
    }
}
