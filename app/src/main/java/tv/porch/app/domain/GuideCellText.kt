package tv.porch.app.domain

import java.time.ZoneId

/** The guide hero's status line for the focused cell. [startUtc] and [stopUtc] are as stored; [offset] is the source's guide offset in minutes. */
object GuideCellText {
    fun status(
        startUtc: Long,
        stopUtc: Long,
        now: Long,
        offset: Int,
        catchup: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val start = GuideClock.shift(startUtc, offset)
        val stop = GuideClock.shift(stopUtc, offset)
        return when {
            now < start -> "Starts ${formatTime(start, zone)}"
            now < stop -> "On now  ·  ends ${formatTime(stop, zone)}  ·  OK plays"
            catchup -> "Ended ${formatTime(stop, zone)}  ·  Catch-up available  ·  OK plays it from the start"
            else -> "Ended ${formatTime(stop, zone)}  ·  No catch-up on this channel"
        }
    }

    /** A channel with no guide gets one bar across the window, named after it. Blank while programmes load, so a cell never says anything yet. */
    fun allDayBar(channelTitle: String, programmesLoaded: Boolean): String = if (programmesLoaded) AllDayText.bar(channelTitle) else ""

    /** A row with no cell to draw gets one bar across the window. Without it, focus on that row would show nothing. */
    fun showsBar(matched: Boolean, programmesLoaded: Boolean, cellsInWindow: Int): Boolean = !matched || !programmesLoaded || cellsInWindow == 0

    /** The bar's words. */
    fun barText(channelTitle: String, matched: Boolean, programmesLoaded: Boolean): String = when {
        !matched -> allDayBar(channelTitle, programmesLoaded)
        programmesLoaded -> "Nothing scheduled here"
        else -> ""
    }
}
