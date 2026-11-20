package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RefreshMatchTest {
    private val modes = listOf(
        RefreshMatch.Mode(1, 3840, 2160, 59.94f),
        RefreshMatch.Mode(2, 3840, 2160, 60f),
        RefreshMatch.Mode(3, 3840, 2160, 50f),
        RefreshMatch.Mode(4, 3840, 2160, 25f),
        RefreshMatch.Mode(5, 3840, 2160, 23.976f),
        RefreshMatch.Mode(6, 3840, 2160, 24f),
        RefreshMatch.Mode(7, 1920, 1080, 50f),
    )
    private val at60 = modes[0]
    private val at50 = modes[2]

    @Test
    fun aChannelThatDoesNotFitTheTvMovesItToTheBestRateAtTheSameSize() {
        assertEquals(3, RefreshMatch.pick(25f, at60, modes))
        assertEquals(3, RefreshMatch.pick(50f, at60, modes))
        assertEquals(5, RefreshMatch.pick(23.976f, at60, modes))
        assertEquals(1, RefreshMatch.pick(59.94f, at50, modes))
    }

    @Test
    fun aChannelThatAlreadyFitsNeverBlinksTheScreen() {
        assertNull(RefreshMatch.pick(29.97f, at60, modes))
        assertNull(RefreshMatch.pick(60f, at60, modes))
        assertNull(RefreshMatch.pick(25f, at50, modes))
    }

    @Test
    fun noFittingModeOrAnOddMeasureLeavesTheTvAlone() {
        assertNull(RefreshMatch.pick(25f, at60, listOf(at60, modes[1])))
        assertNull(RefreshMatch.pick(0f, at60, modes))
    }

    @Test
    fun theRateComesFromTheUsualGapBetweenPictures() {
        val gaps = List(30) { 40_000L } + listOf(80_000L, 120_000L, 0L, -5_000L)
        assertEquals(25f, RefreshMatch.fps(gaps)!!, 0.01f)
        // A millisecond clock: 59.94 frames arrive as a mix of 16 and 17 ms.
        val mixed = List(60) { if (it % 3 == 0) 16_000L else 17_000L }
        assertEquals(60f, RefreshMatch.fps(mixed)!!, 0.5f)
        assertNull(RefreshMatch.fps(emptyList()))
    }
}
