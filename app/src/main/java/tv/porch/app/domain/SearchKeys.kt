package tv.porch.app.domain

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.format.TextStyle
import java.util.Locale

/** What each key on the Search grid does to the typed text. */
object SearchKeys {
    const val SPACE = "Space"
    const val DELETE = "Delete"
    const val CLEAR = "Clear"
    const val MAX_LENGTH = 40

    /** A to Z, then 1 to 9: five rows of seven. The 0 key sits on the bottom row. */
    val GRID: List<String> = ('A'..'Z').map { it.toString() } + ('1'..'9').map { it.toString() }

    /** True from a keystroke until the answer for this very query lands. [answered] is the query the shown results belong to. */
    fun isSearching(query: String, answered: String?): Boolean = query.isNotBlank() && answered != query

    fun apply(query: String, key: String): String = when (key) {
        CLEAR -> ""
        DELETE -> query.dropLast(1)
        SPACE -> if (query.isEmpty() || query.endsWith(" ")) query else (query + " ").take(MAX_LENGTH)
        else -> (query + key.lowercase()).take(MAX_LENGTH)
    }
}

/** Today before 6 pm "12:34 pm today", from 6 pm "8:00 pm tonight", then "tomorrow 9:00 am", then "Thu 7:30 pm". */
fun formatLaterStart(start: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val at = Instant.ofEpochMilli(start).atZone(zone)
    val time = formatTime(start, zone)
    val days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(now).atZone(zone).toLocalDate(), at.toLocalDate())
    return when {
        days == 0L && at.hour >= 18 -> "$time tonight"
        days == 0L -> "$time today"
        days == 1L -> "tomorrow $time"
        else -> "${at.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)} $time"
    }
}
