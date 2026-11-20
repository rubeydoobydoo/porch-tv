package tv.porch.app.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uParserTest {
    @Test
    fun parsesAttributesGroupsAndTitles() {
        val channels = M3uParser().parse(resource("sample.m3u"))
        assertEquals(5, channels.size)

        val sport = channels[0]
        assertEquals("Redfield Sport HD", sport.name)
        assertEquals("redfield.sport", sport.tvgId)
        assertEquals("Redfield Sport", sport.tvgName)
        assertEquals("Sport", sport.group)
        assertEquals("http://example.com/logo.png", sport.logo)
        assertEquals("http://example.com/live/redfield.ts", sport.streamUrl)
        assertEquals("m3u:http://example.com/live/redfield.ts", sport.identity)
        assertEquals(1, sport.number)

        val news = channels[1]
        assertEquals("Harbor News", news.name)
        assertEquals("News", news.group)
        assertEquals("harbor.news", news.tvgId)

        val cinema = channels[2]
        assertEquals(40, cinema.number)
        assertEquals(2, cinema.archiveDays)
        assertEquals("Ungrouped", cinema.group)
        assertEquals("West Pier Cinema", cinema.name)

        assertEquals("Title, with comma", channels[3].name)
        assertEquals("Kids", channels[3].group)

        val north = channels[4]
        assertEquals("North Harbor One", north.name)
        assertEquals("north.harbor", north.tvgId)
        assertNull(north.xtreamStreamId)
    }

    private fun resource(name: String) = checkNotNull(javaClass.classLoader.getResourceAsStream(name))
}
