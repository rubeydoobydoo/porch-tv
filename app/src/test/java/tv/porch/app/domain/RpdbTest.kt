package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RpdbTest {
    private val fakeKey = "fake-key-123"

    @Test
    fun theRatingPosterAddressNeedsAUsableKeyAndARealImdbId() {
        assertEquals(
            "https://api.ratingposterdb.com/fake-key-123/imdb/poster-default/tt0496424.jpg",
            Rpdb.posterUrl(fakeKey, "tt0496424"),
        )
        for (id in listOf("0496424", "tt", "ttabc", "tt12/../x", "TT0496424", " tt0496424", "tt04964240000000000")) {
            assertNull(id, Rpdb.posterUrl(fakeKey, id))
        }
        for (key in listOf("", "  ", "a/b", "key?x=1", "ke y")) assertNull(key, Rpdb.posterUrl(key, "tt0496424"))
        assertEquals(fakeKey, Rpdb.cleanKey("  $fakeKey \n"))
        assertNull(Rpdb.cleanKey("   "))

        val withId = """{"name":"Harbour Lights","externals":{"tvrage":1,"imdb":"tt0496424"}}"""
        val badId = """{"name":"Harbour Lights","externals":{"imdb":"not-an-id"}}"""
        val noId = """{"name":"Harbour Lights","externals":{"imdb":null}}"""
        val otherShow = """{"name":"Quarry Road","externals":{"imdb":"tt0496424"}}"""
        assertEquals("tt0496424", ShowLookup.imdbId(withId, "Harbour Lights"))
        assertNull(ShowLookup.imdbId(badId, "Harbour Lights"))
        assertNull(ShowLookup.imdbId(noId, "Harbour Lights"))
        assertNull(ShowLookup.imdbId(otherShow, "Harbour Lights"))
    }

    @Test
    fun aTinyOrSingleColourAnswerIsABlankPosterNotARealOne() {
        val plain = 0xFF202020.toInt()
        val half = IntArray(400) { if (it % 2 == 0) 0xFFCC3322.toInt() else 0xFF2233CC.toInt() }
        val almostPlain = IntArray(400) { if (it < 4) 0xFFFFFFFF.toInt() else plain }

        assertTrue(Rpdb.looksBlank(49, half))
        assertTrue(Rpdb.looksBlank(300, IntArray(400) { plain }))
        assertTrue(Rpdb.looksBlank(300, almostPlain))
        assertTrue(Rpdb.looksBlank(300, IntArray(0)))
        assertFalse(Rpdb.looksBlank(50, half))
        assertFalse(Rpdb.looksBlank(300, IntArray(400) { if (it < 100) 0xFFFFFFFF.toInt() else plain }))
    }
}
