package tv.porch.app.domain

import java.time.ZoneId

/** What the Home hero says about the focused tile. [nextLine] is null when nothing is coming up. */
data class HeroLines(
    val title: String,
    val subtitle: String,
    val nextLine: String?,
    val okLine: String,
    val progress: Float?,
)

object HeroText {
    const val NO_GUIDE = "Live now. Programme details will appear when the guide is in."

    fun of(
        channelTitle: String,
        programme: Programme?,
        next: Programme?,
        now: Long,
        offsetMinutes: Int = 0,
        zone: ZoneId = ZoneId.systemDefault(),
    ): HeroLines {
        val ok = "Watch $channelTitle"
        if (programme == null) return HeroLines(channelTitle, NO_GUIDE, null, ok, null)
        val start = GuideClock.shift(programme.startUtc, offsetMinutes)
        val stop = GuideClock.shift(programme.stopUtc, offsetMinutes)
        val range = timeRange(start, stop, zone)
        val nextLine = next?.let { "Next ${formatTime(GuideClock.shift(it.startUtc, offsetMinutes), zone)} ${it.title}" }
        return HeroLines(
            title = programme.title,
            subtitle = "$range · ${formatLeft(stop - now)}",
            nextLine = nextLine,
            okLine = ok,
            progress = progressOf(start, stop, now),
        )
    }

    /** "8:00 – 9:00 pm" when both ends share am/pm, else "11:30 am – 12:30 pm". */
    private fun timeRange(start: Long, stop: Long, zone: ZoneId): String {
        val from = formatTime(start, zone)
        val to = formatTime(stop, zone)
        return if (from.takeLast(2) == to.takeLast(2)) "${formatHourMinute(start, zone)} – $to" else "$from – $to"
    }
}
