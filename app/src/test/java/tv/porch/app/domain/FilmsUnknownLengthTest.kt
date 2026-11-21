package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A stream that reports no length (0) must still resume from where it stopped, and must not show "0:00". */
class FilmsUnknownLengthTest {
    private val minute = 60_000L

    @Test
    fun aFilmOfUnknownLengthCountsAsStartedAfterTwoMinutes() {
        assertTrue(Films.started(5 * minute, 0L))
        assertFalse(Films.started(minute, 0L))
    }

    @Test
    fun theOverlayHidesTheLengthWhenItIsUnknown() {
        assertEquals("0:09", Films.clockPair(9_000L, 0L))
        assertEquals("0:09 / 1:30:00", Films.clockPair(9_000L, 90 * minute))
    }

    @Test
    fun theResumeBarIsEmptyNotBrokenWhenTheLengthIsUnknown() {
        assertEquals(0f, Films.progress(5 * minute, 0L), 0f)
        assertEquals(0.5f, Films.progress(45 * minute, 90 * minute), 0.001f)
    }

    @Test
    fun theFilmSheetOffersResumeWithoutALeftTimeWhenTheLengthIsUnknown() {
        val text = Films.sheet(5 * minute, 0L, null)
        assertEquals(listOf("Resume from 5:00", "Play from the start"), text.pills)
        assertEquals("5 min in", text.resumeLine)
        assertNull(text.length)
    }

    @Test
    fun anEpisodeOfUnknownLengthKeepsItsPlace() {
        val episodes = listOf(episode(1), episode(2))
        assertEquals(
            ShowProgressChange.Keep(1L, 5 * minute, 0L),
            Shows.afterWatch(episodes, 1L, 5 * minute, 0L),
        )
    }

    @Test
    fun aFilmOfUnknownLengthThatPlaysToTheEndCountsAsFinished() {
        val end = Films.endMark(95 * minute, 0L)
        assertEquals(true, Films.finished(end, end))
        assertEquals(90 * minute, Films.endMark(89 * minute, 90 * minute))
    }

    private fun episode(id: Long) = tv.porch.app.parse.XtreamEpisode(id, 1, id.toInt(), "Ep $id", "mkv", null, null)

    @Test
    fun noLengthHidesTheBarButKeepsTheHints() {
        assertTrue(Films.showsBar(durationMs = 5_400_000L))
        assertFalse(Films.showsBar(durationMs = 0L))
        assertEquals("OK pauses · Left and Right skip 10 seconds · Back closes", Films.panelHint(paused = false, canSkip = true))
        assertEquals("OK plays · Left and Right skip 10 seconds · Back closes", Films.panelHint(paused = true, canSkip = true))
        assertEquals("OK pauses · Back closes", Films.panelHint(paused = false, canSkip = false))
    }

    @Test
    fun aFilmThatWontSeekDoesNotOfferSkipping() {
        assertEquals("OK pauses · Back closes", Films.panelHint(paused = false, canSkip = false))
    }
}
