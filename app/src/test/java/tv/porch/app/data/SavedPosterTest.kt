package tv.porch.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedPosterTest {
    @Test
    fun anOldNothingIsAskedAboutOnceMoreAndTheNewNothingIsNot() {
        // An empty entry was written before ids were kept: TVmaze may have matched the show without artwork.
        assertTrue(SavedPoster.lacksId(""))
        assertNull(SavedPoster.read("").url)
        // Asked since, with nothing there: kept in the current form so it is never asked again.
        assertEquals(SavedPoster.NOTHING, SavedPoster.encode(null, null))
        assertFalse(SavedPoster.lacksId(SavedPoster.NOTHING))
        assertNull(SavedPoster.read(SavedPoster.NOTHING).imdb)
        // A match with an id and no artwork keeps its id.
        assertEquals("tt0123456", SavedPoster.read(SavedPoster.encode(null, "tt0123456")).imdb)
        // A poster saved before ids were kept is still asked about.
        assertTrue(SavedPoster.lacksId("https://static.example/p.jpg"))
    }
}
