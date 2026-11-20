package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FilmShelvesShownTest {
    private val action = MovieShelf("Action", listOf("1"), true)
    private val comedy = MovieShelf("Comedy", listOf("2"), false)

    @Test
    fun onlyChosenShelvesShowWhenSomeAreChosen() {
        assertEquals(listOf(action), Films.shown(listOf(action, comedy)))
    }

    @Test
    fun everyShelfShowsWhenNoneIsChosen() {
        val none = listOf(comedy, MovieShelf("Drama", listOf("3"), false))
        assertEquals(none, Films.shown(none))
    }
}
