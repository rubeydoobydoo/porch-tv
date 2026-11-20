package tv.porch.app.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XtreamSeriesTest {
    @Test
    fun showRowsAcceptMixedFieldsAndSkipBadItems() {
        val body = """
            [{"series_id": 501, "name": "Harbour Detectives", "cover": "", "category_id": 31, "rating": "7.5",
              "releaseDate": "2019-03-02", "plot": "A plot.", "genre": "Crime", "last_modified": "1700000100"},
             "junk",
             {"series_id": "502", "name": "  ", "category_id": "31"},
             {"series_id": "503", "name": "Flatmates (2015)", "category_id": "32", "rating": 0, "cover": "http://example.com/c.jpg"}]
        """.trimIndent()
        val shows = XtreamSeries.showRows(body.byteInputStream()).toList()
        assertEquals(listOf(501L, 503L), shows.map { it.seriesId })
        assertEquals("31", shows[0].categoryId)
        assertEquals(7.5, shows[0].rating!!, 0.0)
        assertEquals(2019, shows[0].year)
        assertEquals(1700000100L, shows[0].addedAt)
        assertNull(shows[0].coverUrl)
        assertNull(shows[1].rating)
        assertEquals(2015, shows[1].year)
        assertEquals("http://example.com/c.jpg", shows[1].coverUrl)
    }

    @Test
    fun aSingleObjectIsOneShow() {
        val shows = XtreamSeries.showRows("""{"series_id":9,"name":"Solo"}""".byteInputStream()).toList()
        assertEquals(listOf(9L), shows.map { it.seriesId })
    }

    @Test
    fun infoReadsEpisodesKeyedBySeason() {
        val body = """
            {"info":{"plot":"P","genre":"Crime","releaseDate":"2019-01-01","rating":"8","cover":"http://example.com/x.jpg"},
             "episodes":{"2":[{"id":"22","episode_num":1,"season":2,"title":"","container_extension":"mkv","info":{"duration":"00:00:40"}}],
                         "1":[{"id":"12","episode_num":2,"title":"Second","container_extension":"mp4","info":{"duration_secs":2400,"plot":"E"}},
                              {"id":11,"episode_num":"1","title":"First","info":{}},
                              {"id":"bad","episode_num":3,"title":"Skipped"}]}}
        """.trimIndent()
        val info = XtreamSeries.showInfo(body)!!
        assertEquals("P", info.plot)
        assertEquals(2019, info.year)
        assertEquals(listOf(1, 2), info.seasons.map { it.number })
        assertEquals(listOf(11L, 12L), info.seasons[0].episodes.map { it.id })
        assertEquals(2400, info.seasons[0].episodes[1].durationSecs)
        assertEquals("Episode 1", info.seasons[1].episodes[0].title)
        assertEquals(40, info.seasons[1].episodes[0].durationSecs)
        assertEquals("mkv", info.seasons[1].episodes[0].ext)
    }

    @Test
    fun infoReadsEpisodesAsListOfLists() {
        val body = """{"info":{},"episodes":[[{"id":1,"episode_num":1,"season":1,"title":"A"}],[{"id":2,"episode_num":1,"season":2,"title":"B"}],[]]}"""
        val info = XtreamSeries.showInfo(body)!!
        assertEquals(listOf(1, 2), info.seasons.map { it.number })
        assertEquals(2L, info.seasons[1].episodes[0].id)
    }

    @Test
    fun anEpisodeListedTwiceShowsOnce() {
        val body = """{"info":{},"episodes":{"1":[{"id":5,"episode_num":1,"title":"A"},{"id":5,"episode_num":1,"title":"A"},{"id":6,"episode_num":2,"title":"B"}]}}"""
        val info = XtreamSeries.showInfo(body)!!
        assertEquals(listOf(5L, 6L), info.seasons.single().episodes.map { it.id })
    }

    @Test
    fun garbageGivesNull() {
        assertNull(XtreamSeries.showInfo("<html>nope</html>"))
        assertNull(XtreamSeries.showInfo("[]"))
        assertNull(XtreamSeries.showInfo("""{"episodes":[]}"""))
    }

    @Test
    fun episodeUrlUsesTheSeriesPath() {
        assertEquals(
            "http://example.com:8080/series/demo/secret/77.mkv",
            XtreamUrls.episode("http://example.com:8080", "demo", "secret", 77, "mkv"),
        )
        assertEquals(
            "http://example.com:8080/series/demo/secret/77.mp4",
            XtreamUrls.episode("http://example.com:8080", "demo", "secret", 77, " "),
        )
    }
}
