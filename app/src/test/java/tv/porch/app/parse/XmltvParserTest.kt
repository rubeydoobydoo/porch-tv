package tv.porch.app.parse

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.porch.app.domain.GuideRules

class XmltvParserTest {
    @Test
    fun parsesSampleGuide() {
        val sink = CollectingSink()
        XmltvParser().parse(resource("sample.xmltv"), batchSize = 10, sink = sink)
        assertEquals(listOf("redfield.sport", "harbor.news"), sink.channels.map { it.xmltvId })
        assertEquals(listOf("Redfield Sport", "Redfield Sport HD"), sink.channels[0].names)
        assertEquals(2, sink.programmes.size)

        val replay = sink.programmes[0]
        assertEquals("Grand Final replay", replay.title)
        assertEquals("A recorded match from North Harbor.", replay.description)
        assertEquals("Sport", replay.category)
        assertEquals("redfield.sport", replay.channelKey)

        val bulletin = sink.programmes[1]
        assertEquals("Evening & Bulletin", bulletin.title)
        assertEquals("West Pier & docks", bulletin.description)
        assertEquals(parseXmltvTime("20261025170000 +0000"), bulletin.startUtc)
    }

    @Test
    fun offsetZonesAndMissingZone() {
        val utc = parseXmltvTime("20261025180000 +0000")
        val plusHour = parseXmltvTime("20261025180000 +0100")
        val colon = parseXmltvTime("20261025180000 +01:00")
        val bare = parseXmltvTime("20261025180000")
        assertEquals(utc!! - 60 * 60 * 1000, plusHour)
        assertEquals(plusHour, colon)
        assertEquals(utc, bare)
        assertNull(parseXmltvTime("not-a-time"))
    }

    @Test
    fun streamsLargeGuidesInBatches() {
        val body = buildString {
            append("<tv>")
            repeat(4_000) { index ->
                append(
                    """<programme start="${stamp(index)}" stop="${stamp(index + 1)}" channel="redfield.sport"><title>Show $index</title></programme>""",
                )
            }
            append("</tv>")
        }
        val sink = CollectingSink()
        XmltvParser().parse(ByteArrayInputStream(body.toByteArray()), batchSize = 400, sink = sink)
        assertTrue(sink.batches > 1)
        assertEquals(4_000, sink.programmes.size)
        assertEquals("Show 0", sink.programmes.first().title)
        assertEquals("Show 3999", sink.programmes.last().title)
        assertTrue(sink.channels.isEmpty())
    }

    @Test
    fun gzippedGuideParsesLikeThePlainOne() {
        val plain = CollectingSink()
        XmltvParser().parse(resource("sample.xmltv"), sink = plain)
        val zipped = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(resource("sample.xmltv").readBytes()) }
        }.toByteArray()
        val fromGzip = CollectingSink()
        XmltvParser().parse(ByteArrayInputStream(zipped), sink = fromGzip)
        assertEquals(plain.channels, fromGzip.channels)
        assertEquals(plain.programmes, fromGzip.programmes)
    }

    @Test
    fun htmlOrEmptyGuideIsRefused() {
        val html = "<!DOCTYPE html><html><body><h1>502 Bad Gateway</h1></body></html>"
        val empty = """<?xml version="1.0"?><tv><channel id="redfield.sport"><display-name>Redfield</display-name></channel></tv>"""
        for (body in listOf(html, empty)) {
            val sink = CollectingSink()
            XmltvParser().parse(ByteArrayInputStream(body.toByteArray()), sink = sink)
            assertFalse(GuideRules.accepts(sink.programmes.size))
        }
        assertTrue(GuideRules.accepts(1))
    }

    @Test
    fun aProgrammeTakesItsOwnFirstIconAndNeverItsChannels() {
        val sink = parsed(
            """<tv><channel id="harbour.sport"><display-name>Harbour Sport</display-name><icon src="https://example.com/channel.png"/></channel>""" +
                programmeXml("With pictures", """<icon src="https://example.com/first.jpg"/><icon src="https://example.com/second.jpg"/>""") +
                programmeXml("No picture", "") +
                "</tv>",
        )
        assertEquals(listOf("https://example.com/first.jpg", null), sink.programmes.map { it.iconUrl })
    }

    @Test
    fun anIconThatIsNotHttpOrHttpsIsDropped() {
        val sink = parsed(
            "<tv>" +
                programmeXml("File", """<icon src="file:///data/example.png"/>""") +
                programmeXml("Script", """<icon src="javascript:example()"/>""") +
                programmeXml("Web", """<icon src="http://example.com/web.jpg?a=1&amp;b=2"/>""") +
                "</tv>",
        )
        assertEquals(listOf(null, null, "http://example.com/web.jpg?a=1&b=2"), sink.programmes.map { it.iconUrl })
    }

    private fun parsed(body: String) = CollectingSink().also { XmltvParser().parse(ByteArrayInputStream(body.toByteArray()), sink = it) }

    private fun programmeXml(title: String, inner: String) =
        """<programme start="${stamp(0)}" stop="${stamp(30)}" channel="harbour.sport"><title>$title</title>$inner</programme>"""

    private fun resource(name: String) = checkNotNull(javaClass.classLoader.getResourceAsStream(name))

    private fun stamp(minuteOfMonth: Int): String {
        val day = 1 + minuteOfMonth / 1_440
        val rem = minuteOfMonth % 1_440
        val hour = rem / 60
        val minute = rem % 60
        return "202611%02d%02d%02d00 +0000".format(day, hour, minute)
    }
}

private class CollectingSink : XmltvSink {
    val channels = mutableListOf<ParsedGuideChannel>()
    val programmes = mutableListOf<ParsedProgramme>()
    var batches: Int = 0

    override fun onChannels(channels: List<ParsedGuideChannel>) {
        this.channels.addAll(channels)
    }

    override fun onProgrammes(batch: List<ParsedProgramme>) {
        batches += 1
        programmes.addAll(batch)
    }
}
