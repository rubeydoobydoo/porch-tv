package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelNamesListTest {
    @Test
    fun providerNoiseAndTheHeadingsOwnLabelAreGoneFromRows() {
        assertEquals("9Now", ChannelNames.listTitle("AU| 9NOW RAW"))
        assertEquals("Example Movies", ChannelNames.listTitle("Example Movies 60fps"))
        assertEquals("Example Sport", ChannelNames.listTitle("VIP Example Sport"))
        // Raised letters, and a name that is only noise, are left as they were.
        assertEquals("Example", ChannelNames.listTitle("Example ᴿᴬᵂ ⁶⁰ᶠᵖˢ"))
        assertEquals("Vip", ChannelNames.listTitle("VIP"))
        // The chip already says the country, so a trailing label goes too.
        assertEquals("Example One", ChannelNames.listTitle("Example One AU", heading = "AU"))
        assertEquals("Example One AU", ChannelNames.listTitle("Example One AU", heading = "NZ"))

        assertEquals("Sport", ChannelNames.groupRow("EN| Sport 4K RAW", heading = "EN"))
        assertEquals("Sport · EN", ChannelNames.groupRow("EN| Sport", heading = "Other"))
        assertEquals("Example Kids", ChannelNames.groupRow("Example Kids VIP", heading = "Other"))
    }
}
