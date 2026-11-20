package tv.porch.app.parse

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class XmlCursorPushbackTest {
    @Test
    fun nonAsciiCommentsAndCdataParseTheSame() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<tv>
<!-- a comment with Ünï and -- dashes -->
<channel id="example.turk"><display-name>Türk Ünlü</display-name></channel>
<programme start="20261025170000 +0000" stop="20261025180000 +0000" channel="example.turk">
<title><![CDATA[Şöför & <Friends>]]></title>
<desc>Güzel akşam</desc>
</programme>
</tv>"""
        val channels = mutableListOf<ParsedGuideChannel>()
        val programmes = mutableListOf<ParsedProgramme>()
        XmltvParser().parse(
            ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)),
            sink = object : XmltvSink {
                override fun onChannels(list: List<ParsedGuideChannel>) {
                    channels.addAll(list)
                }
                override fun onProgrammes(batch: List<ParsedProgramme>) {
                    programmes.addAll(batch)
                }
            },
        )
        assertEquals(listOf("example.turk"), channels.map { it.xmltvId })
        assertEquals(listOf("Türk Ünlü"), channels[0].names)
        assertEquals(1, programmes.size)
        assertEquals("Şöför & <Friends>", programmes[0].title)
        assertEquals("Güzel akşam", programmes[0].description)
    }
}
