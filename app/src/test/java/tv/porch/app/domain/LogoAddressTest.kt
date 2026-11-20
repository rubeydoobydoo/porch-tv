package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogoAddressTest {
    @Test
    fun blankOrNonHttpMeansLetterBadge() {
        assertNull(LogoAddress.clean(null))
        assertNull(LogoAddress.clean(""))
        assertNull(LogoAddress.clean("   "))
        assertNull(LogoAddress.clean("ftp://example.com/logo.png"))
        assertNull(LogoAddress.clean("file:///sdcard/logo.png"))
        assertNull(LogoAddress.clean("data:image/png;base64,AAAA"))
        assertNull(LogoAddress.clean("example.com/logo.png"))
        assertNull(LogoAddress.clean("https://"))
    }

    @Test
    fun goodAddressesComeBackTrimmed() {
        assertEquals("http://example.com/a.png", LogoAddress.clean("http://example.com/a.png"))
        assertEquals("https://example.com/a.png", LogoAddress.clean("  https://example.com/a.png \n"))
        assertEquals("HTTPS://example.com/a.png", LogoAddress.clean("HTTPS://example.com/a.png"))
    }
}
