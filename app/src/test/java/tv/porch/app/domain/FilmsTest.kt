package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmsTest {
    private val hour = 3_600_000L

    @Test
    fun aFilmCountsAsStartedBetweenTwoMinutesAndNinetyFivePercent() {
        val length = 2 * hour
        assertFalse(Films.started(119_000, length))
        assertTrue(Films.started(121_000, length))
        assertTrue(Films.started((length * 0.94).toLong(), length))
        assertFalse(Films.started((length * 0.96).toLong(), length))
    }

    @Test
    fun matchingCategoriesMergeAndOnlyHomeLabelsStartChosen() {
        val shelves = Films.shelves(
            listOf("1" to "EN | ACTION", "2" to "UK | ACTION", "3" to "FR | COMEDIE"),
            "AU",
        )
        assertEquals(2, shelves.size)
        assertEquals("Action", shelves[0].title)
        assertEquals(listOf("1", "2"), shelves[0].categoryIds)
        assertTrue(shelves[0].chosen)
        assertFalse(shelves[1].chosen)
    }

    @Test
    fun aLabelBehindALeadingBarIsReadAndOtherLanguagesStayOut() {
        val shelves = Films.shelves(
            listOf("1" to "|EN| ACTION", "2" to "|FR| ACTION", "3" to "|FR| COMEDIE"),
            "AU",
        )
        assertEquals(listOf("Action", "Action", "Comedie"), shelves.map { it.title })
        assertEquals(listOf(listOf("1"), listOf("2"), listOf("3")), shelves.map { it.categoryIds })
        assertEquals(listOf(true, false, false), shelves.map { it.chosen })
        assertEquals(listOf(listOf("1")), Films.shown(shelves).map { it.categoryIds })
    }

    @Test
    fun theSheetOffersResumeOnlyForAStartedFilm() {
        val length = 6_730_000L // 1:52:10
        val fresh = Films.sheet(null, null, 6_730)
        assertEquals(listOf("Play"), fresh.pills)
        assertEquals(null, fresh.resumeLine)
        assertEquals("1 h 52 min", fresh.length)

        val started = Films.sheet(2_472_000L, length, 6_730)
        assertEquals(listOf("Resume from 41:12", "Play from the start"), started.pills)
        assertEquals("41 min in \u00b7 1 h 11 min left", started.resumeLine)

        val nearlyDone = Films.sheet((length * 0.97).toLong(), length, 6_730)
        assertEquals(listOf("Play"), nearlyDone.pills)
        assertEquals(null, nearlyDone.resumeLine)
    }
}
