package tv.porch.app.domain

/** The guide offset is added to stored XMLTV times. Positive means the guide is early. */
object GuideClock {
    fun shift(utc: Long, offsetMinutes: Int): Long = utc + offsetMinutes * 60_000L

    fun isOnNow(startUtc: Long, stopUtc: Long, nowUtc: Long, offsetMinutes: Int): Boolean {
        val start = shift(startUtc, offsetMinutes)
        val stop = shift(stopUtc, offsetMinutes)
        return nowUtc >= start && nowUtc < stop
    }

    fun startsWithin(startUtc: Long, nowUtc: Long, offsetMinutes: Int, windowMs: Long): Boolean {
        val start = shift(startUtc, offsetMinutes)
        return start > nowUtc && start <= nowUtc + windowMs
    }
}
