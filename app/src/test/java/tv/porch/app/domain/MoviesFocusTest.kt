package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MoviesFocusTest {
    @Test
    fun continueWatchingTakesFirstFocusWhenThereIsAny() {
        assertEquals(MoviesFocus.Start.Continue, MoviesFocus.start(continueCount = 2, filmCount = 40))
    }

    @Test
    fun theFirstPosterGetsFocusWithNoContinueShelf() {
        assertEquals(MoviesFocus.Start.Grid, MoviesFocus.start(continueCount = 0, filmCount = 40))
    }

    @Test
    fun theChipsGetFocusWhenThereAreNoFilmsAtAll() {
        assertEquals(MoviesFocus.Start.Chips, MoviesFocus.start(continueCount = 0, filmCount = 0))
    }
}
