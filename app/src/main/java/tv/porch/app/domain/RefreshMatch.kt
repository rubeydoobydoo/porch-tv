package tv.porch.app.domain

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Picks the TV refresh rate that suits a channel, so a 25 or 50 frame channel doesn't judder on a 60 Hz screen.
 * Pure: the player measures and applies. A switch can blink the screen, so staying put wins whenever the rate already fits.
 */
object RefreshMatch {
    data class Mode(val id: Int, val width: Int, val height: Int, val refresh: Float)

    /** Pictures to time before deciding. About three seconds, so zapping past a channel never switches the TV. */
    const val GAPS_TO_MEASURE = 75

    /** 24 and 23.976, or 60 and 59.94, are close enough that a blink costs more than it fixes. */
    private const val CLOSE_ENOUGH = 0.0025f

    /**
     * Frame rate from the gaps between picture times in microseconds, null when none make sense. Averages the gaps near
     * the median: dropped pictures and stream jumps fall outside, and streams with millisecond clocks mix 16 and 17 ms.
     */
    fun fps(gapsUs: List<Long>): Float? {
        val usable = gapsUs.filter { it > 0 }.sorted()
        if (usable.isEmpty()) return null
        val median = usable[usable.size / 2]
        val near = usable.filter { it > median * 3 / 4 && it < median * 5 / 4 }
        return 1_000_000f / near.average().toFloat()
    }

    /** The mode to switch to, or null to leave the TV alone. Only modes at the current size, so the picture never drops to a lower resolution. */
    fun pick(fps: Float, current: Mode, modes: List<Mode>): Int? {
        if (fps !in 20f..65f || miss(fps, current.refresh) <= CLOSE_ENOUGH) return null
        return modes
            .filter { it.width == current.width && it.height == current.height && miss(fps, it.refresh) <= CLOSE_ENOUGH }
            .sortedWith(compareBy<Mode> { miss(fps, it.refresh) }.thenByDescending { it.refresh })
            .firstOrNull()?.id
    }

    /** How far the refresh rate is from a whole number of frames, as a fraction. */
    private fun miss(fps: Float, refresh: Float): Float {
        val ratio = refresh / fps
        val whole = ratio.roundToInt()
        if (whole < 1) return 1f
        return abs(ratio - whole) / whole
    }
}
