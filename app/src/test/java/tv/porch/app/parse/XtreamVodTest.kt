package tv.porch.app.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XtreamVodTest {
    @Test
    fun forgivingFilmRowsAreStreamParsed() {
        val input = javaClass.classLoader!!.getResourceAsStream("xtream-vod.json")!!
        val films = XtreamVod.filmRows(input).toList()
        // The blank name, the non-object item and the row without an id are skipped.
        assertEquals(listOf(1001L, 1003L), films.map { it.streamId })
        val first = films[0]
        assertEquals(2019, first.year)
        assertEquals(7.5, first.rating!!, 0.0)
        assertEquals("mkv", first.ext)
        val second = films[1]
        assertNull(second.rating)
        assertEquals(2015, second.year)
        assertEquals(
            "http://example.com:8080/movie/demo/secret/1003.mp4",
            XtreamUrls.movie("http://example.com:8080", "demo", "secret", second.streamId, second.ext),
        )
    
        val info = XtreamVod.filmInfo("""{"info":{"plot":null,"movie_image":null,"duration":"01:52:10","genre":" "}}""")!!
        assertNull(info.plot)
        assertNull(info.iconUrl)
        assertNull(info.genre)
        assertEquals(6730, info.durationSecs)
    }
}

class XtreamVodReplyTest {
    @Test
    fun onlyAJsonArrayCountsAsAList() {
        assertEquals(true, XtreamVod.isList("  []".byteInputStream()))
        assertEquals(true, XtreamVod.isList("﻿[{\"stream_id\":1}]".byteInputStream()))
        assertEquals(false, XtreamVod.isList("""{"user_info":{"auth":0}}""".byteInputStream()))
        assertEquals(false, XtreamVod.isList("".byteInputStream()))
    }

    @Test(expected = Exception::class)
    fun aCutOffListThrowsSoNothingIsPruned() {
        // A download cut off mid-list must fail, not look like a short list.
        XtreamVod.filmRows("""[{"stream_id":1,"name":"Alpha"},{"stream_id":2,"na""".byteInputStream()).toList()
    }
}

class XtreamSkippedIdTest {
    @Test
    fun aListedFilmThatCannotBeReadStillReportsItsId() {
        // Still listed by the provider, so pruning must keep it even though no row is written for it.
        val skipped = mutableListOf<Long>()
        val body = """[{"stream_id":"7","name":"Alpha"},{"stream_id":"8","name":" "},{"stream_id":9.0,"name":""},{"name":"No id"}]"""
        val films = XtreamVod.filmRows(body.byteInputStream()) { skipped += it }.toList()
        assertEquals(listOf(7L), films.map { it.streamId })
        assertEquals(listOf(8L, 9L), skipped)
    }

    @Test
    fun aListedShowThatCannotBeReadStillReportsItsId() {
        val skipped = mutableListOf<Long>()
        val body = """[{"series_id":21,"name":"Beta"},{"series_id":"22","name":""}]"""
        val shows = XtreamSeries.showRows(body.byteInputStream()) { skipped += it }.toList()
        assertEquals(listOf(21L), shows.map { it.seriesId })
        assertEquals(listOf(22L), skipped)
    }
}
