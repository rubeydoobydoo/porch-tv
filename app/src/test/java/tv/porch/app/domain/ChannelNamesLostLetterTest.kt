package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelNamesLostLetterTest {
    @Test
    fun aLostLetterMarkIsDroppedNotGuessed() {
        assertEquals("90 Day Fianc", ChannelNames.display("90 Day Fianc\uFFFD").title)
        assertEquals("Caf Sport", ChannelNames.display("AU| Caf\uFFFD Sport").title)
    }
}
