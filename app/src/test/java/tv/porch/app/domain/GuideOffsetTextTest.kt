package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GuideOffsetTextTest {
    @Test
    fun sentencesForLaterEarlierAndNone() {
        assertEquals(
            "Shows are listed 5 minutes later than your provider sends them. Use this if programmes keep starting after the guide says.",
            GuideOffsetText.sentence(5),
        )
        assertEquals(
            "Shows are listed 10 minutes earlier than your provider sends them. Use this if programmes keep starting before the guide says.",
            GuideOffsetText.sentence(-10),
        )
        assertEquals("Guide times are used as published.", GuideOffsetText.sentence(0))
        assertEquals("+5 min", GuideOffsetText.label(5))
        assertEquals("-10 min", GuideOffsetText.label(-10))
        assertEquals("None", GuideOffsetText.label(0))
        assertEquals(720, GuideOffsetText.step(720, 1))
        assertEquals(-5, GuideOffsetText.step(0, -1))
    }
}
