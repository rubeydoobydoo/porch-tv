package tv.porch.app.domain

import java.time.ZoneId

/** The words on Home's preview pane. */
object HomePreview {
    /** [live] puts the word LIVE ahead of [text]. */
    data class Status(val live: Boolean, val text: String)

    /** A row with no end time is a channel the guide knows nothing about. */
    fun status(row: LiveRows.Row, now: Long, zone: ZoneId = ZoneId.systemDefault()): Status = when {
        row.endMs <= 0L -> Status(live = false, text = "No guide for this channel")
        row.startMs > now -> Status(live = false, text = "Starts ${formatTime(row.startMs, zone)}")
        else -> Status(live = true, text = "ends ${formatTime(row.endMs, zone)}")
    }
}
