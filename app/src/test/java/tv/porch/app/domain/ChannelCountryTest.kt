package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelCountryTest {
    @Test
    fun countryComesFromTheLeadingLabelOfTheGroup() {
        assertEquals(Country.AU, ChannelCountry.of("AU| Example Sport"))
        assertEquals(Country.UK, ChannelCountry.of("UK: Example News"))
        assertEquals(Country.UK, ChannelCountry.of("GB| Example Movies"))
        assertEquals(Country.US, ChannelCountry.of("USA| Example Kids"))
        assertEquals(Country.NZ, ChannelCountry.of("NZ - Example"))
        assertEquals(Country.CA, ChannelCountry.of("CA| Example"))
        assertEquals(Country.IE, ChannelCountry.of("IE| Example"))
        // A language label, a label that is not a chip, and no label at all are not countries.
        assertNull(ChannelCountry.of("EN| Example"))
        assertNull(ChannelCountry.of("DE| Example"))
        assertNull(ChannelCountry.of("Example Sport"))
        // "Ukrainian" is a language, not the UK.
        assertNull(ChannelCountry.of("Ukrainian Example"))
    }
}
