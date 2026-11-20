package tv.porch.app.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

fun formatTime(epoch: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val zoned = Instant.ofEpochMilli(epoch).atZone(zone)
    val hour = zoned.hour % 12
    val hour12 = if (hour == 0) 12 else hour
    val minute = zoned.minute.toString().padStart(2, '0')
    val suffix = if (zoned.hour < 12) "am" else "pm"
    return "$hour12:$minute $suffix"
}

fun formatHourMinute(epoch: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val zoned = Instant.ofEpochMilli(epoch).atZone(zone)
    val hour = zoned.hour % 12
    val hour12 = if (hour == 0) 12 else hour
    val minute = zoned.minute.toString().padStart(2, '0')
    return "$hour12:$minute"
}

fun formatHeaderClock(epoch: Long, withDay: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
    if (!withDay) return formatTime(epoch, zone)
    val day = Instant.ofEpochMilli(epoch).atZone(zone).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
    return "$day ${formatTime(epoch, zone)}"
}

/** "Monday 8:42 pm": the full weekday, for the Studio top bar. */
fun formatHeaderClockLong(epoch: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val day = Instant.ofEpochMilli(epoch).atZone(zone).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
    return "$day ${formatTime(epoch, zone)}"
}

fun formatGuideDay(epoch: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val zoned = Instant.ofEpochMilli(epoch).atZone(zone)
    val day = zoned.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
    val month = zoned.month.getDisplayName(TextStyle.SHORT, Locale.US)
    return "$day ${zoned.dayOfMonth} $month"
}

/** "Monday 29 Oct": the guide's day label. */
fun formatGuideDayLong(epoch: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val zoned = Instant.ofEpochMilli(epoch).atZone(zone)
    val day = zoned.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
    val month = zoned.month.getDisplayName(TextStyle.SHORT, Locale.US)
    return "$day ${zoned.dayOfMonth} $month"
}

fun formatLeft(remainingMs: Long): String {
    val minutes = (remainingMs / 60_000L).toInt()
    if (minutes < 1) return "ending"
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> "$rest min left"
        rest == 0 -> "$hours h left"
        else -> "$hours h $rest min left"
    }
}

fun progressOf(start: Long, stop: Long, now: Long): Float {
    val span = (stop - start).coerceAtLeast(1L)
    return ((now - start).toFloat() / span.toFloat()).coerceIn(0f, 1f)
}
