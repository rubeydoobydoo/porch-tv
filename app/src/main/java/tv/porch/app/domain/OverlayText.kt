package tv.porch.app.domain

import java.time.ZoneId

/** The time line under the programme title in the player overlay. */
object OverlayText {
    /** Live: "8:00 – 9:00 pm · 18 min left". Catch-up: "Catch-up · 8:00 – 9:00 pm". No programme: "". */
    fun of(start: Long?, stop: Long?, catchup: Boolean, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        if (start == null || stop == null) return ""
        val from = formatTime(start, zone)
        val to = formatTime(stop, zone)
        // "8:00 – 9:00 pm" when both ends share am or pm; otherwise each end keeps its own.
        val span = if (from.takeLast(2) == to.takeLast(2)) "${formatHourMinute(start, zone)} – $to" else "$from – $to"
        return if (catchup) "Catch-up · $span" else "$span · ${formatLeft(stop - now)}"
    }
}
