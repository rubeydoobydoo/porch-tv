package tv.porch.app.domain

import java.time.Instant
import java.time.ZoneId

/** What the programme details sheet says and offers. The sheet only draws it. */
object ProgrammeCard {
    enum class State { OnNow, NotStarted, Finished }

    /** [live] is true when the line starts with the word Live, which the sheet draws in its own colour. */
    data class WhenLine(val text: String, val live: Boolean)

    enum class Kind {
        /** Tune the channel as it is now. */
        WatchLive,

        /** Play the programme as catch-up. */
        WatchCatchup,

        /** Ask to be nudged before it starts. */
        Remind,

        /** Follow the programme's name, or stop following it. */
        Follow,
    }

    /**
     * Following offered on the sheet. [choices] are what a follow could be (the whole title last); [following] is the follow
     * that already fits this title, or null.
     */
    data class FollowPill(val choices: List<String>, val following: String?)

    /** [chosen] draws the amber tick: the reminder is on. */
    data class Action(val kind: Kind, val label: String, val chosen: Boolean = false)

    /** An OK this soon after the buttons changed may have been meant for the old ones, so it is ignored. */
    const val SETTLE_MS = 500L

    fun settling(changedAtMs: Long, nowMs: Long): Boolean = nowMs - changedAtMs < SETTLE_MS

    /** Times are as stored; the guide [offsetMinutes] is applied here, as the guide does. */
    fun stateOf(startUtc: Long, stopUtc: Long, nowUtc: Long, offsetMinutes: Int): State = when {
        GuideClock.isOnNow(startUtc, stopUtc, nowUtc, offsetMinutes) -> State.OnNow
        GuideClock.shift(startUtc, offsetMinutes) > nowUtc -> State.NotStarted
        else -> State.Finished
    }

    fun whenLine(startUtc: Long, stopUtc: Long, nowUtc: Long, offsetMinutes: Int, zone: ZoneId = ZoneId.systemDefault()): WhenLine {
        val start = GuideClock.shift(startUtc, offsetMinutes)
        val stop = GuideClock.shift(stopUtc, offsetMinutes)
        return when (stateOf(startUtc, stopUtc, nowUtc, offsetMinutes)) {
            State.OnNow -> WhenLine("Live · ends ${formatTime(stop, zone)}", live = true)
            State.NotStarted -> WhenLine(upcomingText(start, nowUtc, zone), live = false)
            State.Finished -> {
                val day = if (dayOf(stop, zone) == dayOf(nowUtc, zone)) "" else formatGuideDay(stop, zone) + " "
                WhenLine("Ended $day${formatTime(stop, zone)}", live = false)
            }
        }
    }

    private fun upcomingText(start: Long, now: Long, zone: ZoneId): String {
        val time = formatTime(start, zone)
        val days = dayOf(start, zone) - dayOf(now, zone)
        return when (days) {
            0L -> "Today $time · ${away(start - now)}"
            1L -> "Tomorrow $time"
            else -> "${formatGuideDay(start, zone)} $time"
        }
    }

    private fun away(ms: Long): String {
        val minutes = (ms / 60_000L).toInt().coerceAtLeast(1)
        val hours = minutes / 60
        return when {
            hours >= 1 -> "in $hours hour${if (hours == 1) "" else "s"}"
            else -> "in $minutes minute${if (minutes == 1) "" else "s"}"
        }
    }

    private fun dayOf(epoch: Long, zone: ZoneId): Long = Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate().toEpochDay()

    /**
     * The buttons, first one focused. [catchup] is whether a copy of the channel can play this programme from the archive.
     * [reminderOn] is null where reminders aren't offered. Otherwise a programme that starts in more than five minutes leads with
     * "Remind me" (or "Reminder on" once set); closer than that, or started, there is no reminder pill.
     */
    fun actions(
        startUtc: Long,
        stopUtc: Long,
        nowUtc: Long,
        offsetMinutes: Int,
        catchup: Boolean,
        channelName: String,
        reminderOn: Boolean? = null,
        follow: FollowPill? = null,
    ): List<Action> {
        val main = when (stateOf(startUtc, stopUtc, nowUtc, offsetMinutes)) {
            State.OnNow -> buildList {
                add(Action(Kind.WatchLive, "Watch now"))
                if (catchup) add(Action(Kind.WatchCatchup, "Watch from the start"))
            }
            State.NotStarted -> buildList {
                if (reminderOn != null && Reminders.canRemind(GuideClock.shift(startUtc, offsetMinutes), nowUtc)) {
                    add(Action(Kind.Remind, if (reminderOn) "Reminder on" else "Remind me", chosen = reminderOn))
                }
                add(Action(Kind.WatchLive, "Watch $channelName now"))
            }
            State.Finished ->
                if (catchup) listOf(Action(Kind.WatchCatchup, "Watch")) else listOf(Action(Kind.WatchLive, "Watch $channelName now"))
        }
        if (follow == null || follow.choices.isEmpty()) return main
        // The title sits right above the pill, so a whole-title follow needs no name; a long name is cut at a word to fit the row.
        fun named(word: String, term: String): String =
            if (term.equals(follow.choices.last(), ignoreCase = true)) word else "$word ${shortName(term)}"
        val pill = if (follow.following != null) Action(Kind.Follow, named("Following", follow.following), chosen = true) else Action(Kind.Follow, named("Follow", follow.choices.first()))
        return main + pill
    }

    private const val NAME_CAP = 16

    private fun shortName(name: String): String {
        if (name.length <= NAME_CAP) return name
        val cut = name.take(NAME_CAP + 1).substringBeforeLast(' ', "").ifEmpty { name.take(NAME_CAP) }
        return cut.trimEnd() + "…"
    }
}
