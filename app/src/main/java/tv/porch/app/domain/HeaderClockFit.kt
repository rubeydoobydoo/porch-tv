package tv.porch.app.domain

/**
 * What the top bar's clock shows beside the title. The tabs are measured first and always show whole,
 * so the clock gives way: it drops its day, then hides. Widths are in pixels.
 */
object HeaderClockFit {
    enum class Shown { Full, TimeOnly, None }

    fun pick(space: Int, title: Int, gap: Int, full: Int, timeOnly: Int): Shown = when {
        title + gap + full <= space -> Shown.Full
        title + gap + timeOnly <= space -> Shown.TimeOnly
        else -> Shown.None
    }

    /** "Wednesday 10:34 pm" becomes "10:34 pm". A clock with no day is left alone. */
    fun dropDay(clock: String): String {
        val parts = clock.split(' ')
        return if (parts.size > 2) parts.drop(1).joinToString(" ") else clock
    }
}
