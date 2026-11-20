package tv.porch.app.domain

import kotlinx.serialization.Serializable

/**
 * A programme the viewer asked to be nudged about. The channel is named by [sourceId] and [identity], the same pair that
 * carries favourites across a refresh, because a refresh gives every channel a new id. [startMs] and [stopMs] are guide-shifted: the source's guide offset is
 * already added, as everywhere the app compares a programme with the clock.
 */
@Serializable
data class Reminder(
    val sourceId: Long,
    val identity: String,
    val title: String,
    val startMs: Long,
    val stopMs: Long,
)

/** The rules for reminders. They fire only while the app is open, so a late one is still worth a nudge, but not for long. */
object Reminders {
    /** How long before the start the nudge comes. */
    const val LEAD_MS = 5 * 60_000L

    /** How long after the start a reminder is still worth showing, and then kept at all. */
    const val LATE_MS = 15 * 60_000L

    /** A reminder only makes sense when the start is more than the lead time away. */
    fun canRemind(startMs: Long, nowMs: Long): Boolean = startMs - nowMs > LEAD_MS

    /** From [LEAD_MS] before the start up to [LATE_MS] after it, earliest start first. */
    fun due(reminders: List<Reminder>, nowMs: Long): List<Reminder> =
        reminders.filter { nowMs >= it.startMs - LEAD_MS && nowMs <= it.startMs + LATE_MS }.sortedBy { it.startMs }

    /** Drops reminders more than [LATE_MS] past their start: one set while the app was closed for hours just vanishes. */
    fun prune(reminders: List<Reminder>, nowMs: Long): List<Reminder> = reminders.filter { nowMs - it.startMs <= LATE_MS }

    /** The same programme on the same channel is one reminder. */
    fun same(a: Reminder, b: Reminder): Boolean = a.sourceId == b.sourceId && a.identity == b.identity && a.startMs == b.startMs

    fun has(reminders: List<Reminder>, sourceId: Long, identity: String, startMs: Long): Boolean =
        reminders.any { it.sourceId == sourceId && it.identity == identity && it.startMs == startMs }

    fun add(reminders: List<Reminder>, reminder: Reminder): List<Reminder> = reminders.filterNot { same(it, reminder) } + reminder

    fun remove(reminders: List<Reminder>, reminder: Reminder): List<Reminder> = reminders.filterNot { same(it, reminder) }

    /** What the card says, e.g. "Starts in 5 minutes on Harbour News". Under a minute, or once it has started, it is "now". */
    fun cardLine(reminder: Reminder, nowMs: Long, channelName: String): String {
        val left = reminder.startMs - nowMs
        if (left < 60_000L) return "Starting now on $channelName"
        val minutes = ((left + 59_999L) / 60_000L).toInt()
        return "Starts in $minutes minute${if (minutes == 1) "" else "s"} on $channelName"
    }
}
