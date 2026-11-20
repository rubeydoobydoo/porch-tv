package tv.porch.app.parse

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamJsonTest {
    @Test
    fun mapsLiveStreamsAndSkipsMovies() {
        val categories = resource("xtream-categories.json")
        val streams = resource("xtream-streams.json")
        val channels = XtreamMapper.toChannels(
            categories,
            streams,
            server = "http://example.com:8080/player_api.php",
            username = "demo",
            password = "secret",
        )
        assertEquals(2, channels.size)
        val sport = channels[0]
        assertEquals("Redfield Sport", sport.name)
        assertEquals(4, sport.number)
        assertEquals("Sport", sport.group)
        assertEquals("redfield.sport", sport.tvgId)
        assertEquals(501, sport.xtreamStreamId)
        assertEquals(5, sport.archiveDays)
        assertEquals("http://example.com:8080/live/demo/secret/501.ts", sport.streamUrl)
        assertEquals("xt:501", sport.identity)

        val news = channels[1]
        assertEquals("Harbor News", news.name)
        assertEquals("News", news.group)
        assertEquals(XtreamMapper.ARCHIVE_DAYS_WHEN_UNSPECIFIED, news.archiveDays)
    }

    @Test
    fun loginErrorObjectIsRefused() {
        assertThrows(IllegalArgumentException::class.java) {
            XtreamMapper.streams("""{"user_info":{"auth":0}}""")
        }
    }

    @Test
    fun catchupStampIsUtcAndPasswordIsEncoded() {
        val start = Instant.parse("2026-10-25T18:00:00Z").toEpochMilli()
        val url = XtreamUrls.catchup(
            server = "example.com:8080",
            username = "demo",
            password = "secret word",
            streamId = 501,
            startUtc = start,
            durationMinutes = 120,
        )
        assertEquals(
            "http://example.com:8080/timeshift/demo/secret%20word/120/2026-10-25:18-00/501.ts",
            url,
        )
    }

    @Test
    fun catchupStampIsWrittenInThePanelZone() {
        val rows = listOf(
            Triple("Australia/Sydney", "2026-10-25T22:07:00Z", "2026-10-26:09-07"),
            Triple("Europe/London", "2026-07-10T18:00:00Z", "2026-07-10:19-00"),
            Triple(null, "2026-10-25T22:07:00Z", "2026-10-25:22-07"),
        )
        for ((zoneId, start, stamp) in rows) {
            val startMs = Instant.parse(start).toEpochMilli()
            val url = if (zoneId == null) {
                XtreamUrls.catchup("example.com:8080", "demo", "demo", 501, startMs, 60)
            } else {
                XtreamUrls.catchup("example.com:8080", "demo", "demo", 501, startMs, 60, XtreamUrls.zoneOf(zoneId))
            }
            assertEquals("http://example.com:8080/timeshift/demo/demo/60/$stamp/501.ts", url)
        }
    }

    @Test
    fun serverTimeZoneComesFromTheLoginReply() {
        assertEquals(
            "Europe/London",
            XtreamMapper.serverTimeZone("""{"user_info":{"auth":1},"server_info":{"timezone":"Europe/London"}}"""),
        )
        assertNull(XtreamMapper.serverTimeZone("""{"user_info":{"auth":1},"server_info":{}}"""))
        assertNull(XtreamMapper.serverTimeZone("""{"user_info":{"auth":1},"server_info":{"timezone":"Mars/Olympus"}}"""))
        assertNull(XtreamMapper.serverTimeZone("not json"))
        assertTrue(XtreamMapper.isLoginReply("""{"user_info":{"auth":1},"server_info":{}}"""))
        assertFalse(XtreamMapper.isLoginReply("<html>maintenance</html>"))
        assertFalse(XtreamMapper.isLoginReply("[]"))
    }

    private fun resource(name: String) = checkNotNull(javaClass.classLoader.getResourceAsStream(name))
        .bufferedReader()
        .readText()
}
