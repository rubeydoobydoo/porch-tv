package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import tv.porch.app.parse.XtreamUrls

class XtreamGuideTest {
    @Test
    fun emptyGuideUsesThePanelsOwn() {
        assertEquals(
            "http://example.com:8080/xmltv.php?username=u%20s&password=p%26w",
            XtreamUrls.xmltv("http://example.com:8080", "u s", "p&w"),
        )
        val xtream = SourceDraft(kind = "xtream", server = "example.com:8080", username = "demo", password = "pw").cleaned()
        assertEquals("http://example.com:8080/xmltv.php?username=demo&password=pw", xtream.xmltvUrl)
        val typed = SourceDraft(kind = "xtream", server = "example.com", username = "demo", password = "pw", xmltvUrl = "http://example.com/g.xml").cleaned()
        assertEquals("http://example.com/g.xml", typed.xmltvUrl)
        val refused = assertThrows(IllegalArgumentException::class.java) {
            SourceDraft(kind = "m3u", m3uUrl = "http://example.com/list.m3u").cleaned()
        }
        assertEquals("A playlist needs a guide URL that starts with http:// or https://.", refused.message)
    }
}
