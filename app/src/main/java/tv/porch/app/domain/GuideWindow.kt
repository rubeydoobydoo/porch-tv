package tv.porch.app.domain

/** Where a programme sits across the guide window. [width] is zero when the programme is outside it. */
data class GuideSpan(val x: Float, val width: Float)

object GuideWindow {
    /**
     * True when the red now chip, centred on [now], would sit on the time label that starts at [mark].
     * The label runs about 8 minutes of a 2-hour window wide and the chip about 10, and both take more minutes in a wider one.
     */
    fun markHiddenByNow(mark: Long, now: Long, windowMs: Long = 120 * MINUTE): Boolean {
        val scale = windowMs.toDouble() / (120 * MINUTE)
        return now - mark in (-6 * MINUTE * scale).toLong()..(14 * MINUTE * scale).toLong()
    }

    private const val MINUTE = 60_000L

    /** Start and width across [widthDp] for a programme, clamped to the window. Times are already shifted by the guide offset. */
    fun span(startUtc: Long, stopUtc: Long, windowStart: Long, windowMs: Long, widthDp: Float): GuideSpan {
        val windowEnd = windowStart + windowMs
        if (stopUtc <= windowStart || startUtc >= windowEnd) return GuideSpan(0f, 0f)
        val from = maxOf(startUtc, windowStart)
        val to = minOf(stopUtc, windowEnd)
        val x = (from - windowStart).toFloat() / windowMs * widthDp
        val end = (to - windowStart).toFloat() / windowMs * widthDp
        return GuideSpan(x, end - x)
    }

    /** How wide the elapsed tone is in a cell starting at [cellX]: up to the now-line for a programme on air, otherwise nothing. */
    fun elapsedWidth(startUtc: Long, stopUtc: Long, now: Long, windowStart: Long, windowMs: Long, widthDp: Float, cellX: Float): Float {
        if (now < startUtc || now >= stopUtc) return 0f
        return ((now - windowStart).toFloat() / windowMs * widthDp - cellX).coerceAtLeast(0f)
    }
}
