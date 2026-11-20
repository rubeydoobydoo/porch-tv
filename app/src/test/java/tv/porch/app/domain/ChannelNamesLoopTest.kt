package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelNamesLoopTest {
    @Test
    fun aLeading247IsDroppedSoTheShowNameLeads() {
        // Every loop channel read "24/7 ..." and every badge read "24".
        assertEquals("Example Show", ChannelNames.display("24/7 Example Show").title)
        assertEquals("Example Show", ChannelNames.display("24/7: EXAMPLE SHOW").title)
        assertEquals("EX", ChannelBadge.letters(ChannelNames.display("24/7 Example Xtra").title))
    }

    @Test
    fun aNameThatIsOnly247KeepsIt() {
        assertEquals("24/7", ChannelNames.display("24/7").title)
    }

    @Test
    fun aGroupKeepsIts247BecauseThatIsWhatTheGroupIs() {
        assertEquals("24/7 Example Shows", ChannelNames.group("24/7 Example Shows").title)
    }
}
