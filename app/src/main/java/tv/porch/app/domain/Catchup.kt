package tv.porch.app.domain

/** Catch-up is offered when the channel advertises an archive and the programme start is still inside it. */
fun catchupAllowed(archiveDays: Int, startUtc: Long, nowUtc: Long): Boolean {
    if (archiveDays <= 0 || startUtc > nowUtc) return false
    val window = archiveDays * 24L * 60L * 60L * 1000L
    return nowUtc - startUtc <= window
}

fun catchupMinutes(startUtc: Long, stopUtc: Long): Int {
    return ((stopUtc - startUtc) / 60_000L).toInt().coerceIn(1, 12 * 60)
}

// The archive holds the real time, so catch-up asks for the guide start corrected by the offset.
fun catchupStart(startUtc: Long, offsetMinutes: Int): Long = GuideClock.shift(startUtc, offsetMinutes)
