package tv.porch.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.serialization.Serializable
import tv.porch.app.parse.EspnCalendar

/** How a league's scoreboard turns into fixtures. */
enum class SportKind {
    /** Two teams, one game per event. */
    Team,

    /** One event with sessions (F1): the qualifying and race sessions are fixtures. */
    Sessions,

    /** One fixture per event (UFC). */
    Event,

    /** A tournament over several days (golf, tennis). */
    Tournament,
}

/** One league Porch asks ESPN about. [chip] is the filter it belongs to, [label] what a row says under its title. */
data class SportLeague(val chip: String, val path: String, val label: String, val kind: SportKind, val byRange: Boolean)

/**
 * One thing to watch. Has no score, winner or result field on purpose: nothing here can leak one.
 * A multi-day event has [startDay] and [endDay] (ISO dates, ESPN's US Eastern days); [startMs] is then the start of that day.
 */
@Serializable
data class SportFixture(
    val id: String,
    val title: String,
    val league: String,
    val chip: String,
    val startMs: Long,
    val startDay: String? = null,
    val endDay: String? = null,
    /** ESPN said it was on when the copy was made. */
    val inPlay: Boolean = false,
)

/** What a row shows: [time] is empty when [live]. [followed] marks a game the viewer follows; [link] is the channel showing it, if any. */
data class SportRowText(
    val title: String,
    val league: String,
    val time: String,
    val live: Boolean,
    val key: String = "",
    val followed: Boolean = false,
    val link: SportLink? = null,
)

data class SportDay(val heading: String, val rows: List<SportRowText>)

/** Which leagues, which addresses, the 14-day window and the words. All pure: the clock and zone come in. */
object SportRules {
    const val WINDOW_DAYS = 14
    const val FRESH_MS = 60 * 60_000L
    /** A fixture saved as not yet started stays listed this long after its start, until a refresh says otherwise. */
    private const val JUST_STARTED_MS = 3 * 3_600_000L
    private const val LIVE_CAP_MS = 6 * 3_600_000L
    private const val BASE = "https://site.api.espn.com/apis/site/v2/sports/"

    /** ESPN names its days in US Eastern time. */
    val espnZone: ZoneId = ZoneId.of("America/New_York")

    val leagues: List<SportLeague> = listOf(
        SportLeague("AFL", "australian-football/afl", "AFL", SportKind.Team, false),
        SportLeague("NRL", "rugby-league/3", "NRL", SportKind.Team, false),
        SportLeague("Football", "soccer/eng.1", "Premier League", SportKind.Team, false),
        SportLeague("Football", "soccer/uefa.champions", "Champions League", SportKind.Team, false),
        SportLeague("Football", "soccer/aus.1", "A-League", SportKind.Team, false),
        SportLeague("NFL", "football/nfl", "NFL", SportKind.Team, false),
        SportLeague("NBA", "basketball/nba", "NBA", SportKind.Team, false),
        SportLeague("UFC", "mma/ufc", "UFC", SportKind.Event, true),
        SportLeague("F1", "racing/f1", "Formula 1", SportKind.Sessions, true),
        SportLeague("Golf", "golf/pga", "PGA Tour", SportKind.Tournament, true),
        SportLeague("Golf", "golf/eur", "DP World Tour", SportKind.Tournament, true),
        SportLeague("Golf", "golf/liv", "LIV Golf", SportKind.Tournament, true),
        SportLeague("Tennis", "tennis/atp", "ATP", SportKind.Tournament, true),
        SportLeague("Tennis", "tennis/wta", "WTA", SportKind.Tournament, true),
    )

    const val ALL = "All"

    /** All first, then each sport once, in league order. All ten always show. */
    val chips: List<String> = listOf(ALL) + leagues.map { it.chip }.distinct()

    private val compact = DateTimeFormatter.BASIC_ISO_DATE

    /** The ESPN (US Eastern) day an instant falls on. */
    fun espnDay(epoch: Long): LocalDate = Instant.ofEpochMilli(epoch).atZone(espnZone).toLocalDate()

    /** First and last day to ask ESPN about: today in New York to 14 days on. */
    fun windowDays(now: Long): Pair<LocalDate, LocalDate> = espnDay(now) to espnDay(now + WINDOW_DAYS * 86_400_000L)

    fun plainUrl(league: SportLeague) = "$BASE${league.path}/scoreboard"

    fun dayUrl(league: SportLeague, day: LocalDate) = "${plainUrl(league)}?dates=${day.format(compact)}"

    fun rangeUrl(league: SportLeague, now: Long): String {
        val (from, to) = windowDays(now)
        return "${plainUrl(league)}?dates=${from.format(compact)}-${to.format(compact)}"
    }

    /**
     * The single days to ask a team league for, after its plain reply: a "day" calendar's listed days inside the window,
     * every day of the window for a "list" season still running, nothing for a season that has ended.
     */
    fun daysToAsk(calendar: EspnCalendar, now: Long): List<LocalDate> {
        val (from, to) = windowDays(now)
        return when (calendar.type) {
            "day" -> calendar.days.filter { it >= from && it <= to }.distinct().sorted()
            "list" -> {
                val end = calendar.lastEnd
                if (end == null || end < from) emptyList() else generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.toList()
            }
            else -> emptyList()
        }
    }

    fun forChip(fixtures: List<SportFixture>, chip: String): List<SportFixture> =
        if (chip == ALL) fixtures else fixtures.filter { it.chip == chip }

    private fun multiDay(f: SportFixture) = f.startDay != null

    private fun startDay(f: SportFixture) = LocalDate.parse(checkNotNull(f.startDay))
    private fun endDay(f: SportFixture) = LocalDate.parse(f.endDay ?: f.startDay)

    private fun isLive(f: SportFixture, now: Long) = f.inPlay && now < f.startMs + LIVE_CAP_MS

    /** Fixtures from now to 14 days on, anything on now, and a multi-day event until its end date. */
    private fun inWindow(f: SportFixture, now: Long, today: LocalDate): Boolean =
        if (multiDay(f)) endDay(f) >= today && startDay(f) <= today.plusDays(WINDOW_DAYS.toLong())
        else isLive(f, now) || (f.startMs >= now - JUST_STARTED_MS && f.startMs <= now + WINDOW_DAYS * 86_400_000L)

    /** True when the viewer follows a name in the game's title or its league. */
    fun followed(f: SportFixture, follows: List<String>): Boolean = followedWords(f, follows.map(FollowTerms::words))

    // Each follow and each title is read once, not once per pair: this runs on every row each minute.
    private fun followedWords(f: SportFixture, wanted: List<List<String>>): Boolean {
        if (wanted.isEmpty()) return false
        val title = FollowTerms.words(f.title)
        val league = FollowTerms.words(f.league)
        return wanted.any { FollowTerms.matchesWords(it, title) || FollowTerms.matchesWords(it, league) }
    }

    /** What OK does: plays a game that is on or about to start, sets (or takes off) a reminder for a later one. */
    fun okFor(link: SportLink?, now: Long, hasReminder: Boolean): SportOk = when {
        link == null -> SportOk.None
        !Reminders.canRemind(link.startMs, now) -> SportOk.Play
        hasReminder -> SportOk.Unremind
        else -> SportOk.Remind
    }

    fun okHint(ok: SportOk): String = when (ok) {
        SportOk.Play -> "OK plays"
        SportOk.Remind -> "OK sets a reminder"
        SportOk.Unremind -> "OK removes the reminder"
        SportOk.None -> "No channel in your guide for this game."
    }

    /**
     * The list for a chip: grouped by day in [zone]. A followed game comes first on its day, then each day's games go by
     * start time then title. [links] (by [keyOf]) says which channel shows a game.
     */
    fun days(
        fixtures: List<SportFixture>,
        now: Long,
        zone: ZoneId,
        follows: List<String> = emptyList(),
        links: Map<String, SportLink> = emptyMap(),
    ): List<SportDay> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val wanted = follows.map(FollowTerms::words)
        return fixtures.filter { inWindow(it, now, today) }
            .groupBy { dayOf(it, today, zone) }
            .toSortedMap()
            .map { (day, list) ->
                val marked = list.map { it to followedWords(it, wanted) }
                val ordered = marked.sortedWith(compareBy({ !it.second }, { it.first.startMs }, { it.first.title }))
                SportDay(heading(day, today, zone), ordered.map { (f, follow) -> rowText(f, now, zone).copy(followed = follow, link = links[keyOf(f)]) })
            }
    }

    private fun dayOf(f: SportFixture, today: LocalDate, zone: ZoneId): LocalDate =
        if (multiDay(f)) maxOf(startDay(f), today) else maxOf(Instant.ofEpochMilli(f.startMs).atZone(zone).toLocalDate(), today)

    private fun heading(day: LocalDate, today: LocalDate, zone: ZoneId): String = when (day) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> formatGuideDay(day.atStartOfDay(zone).toInstant().toEpochMilli(), zone)
    }

    fun rowText(f: SportFixture, now: Long, zone: ZoneId): SportRowText {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        if (multiDay(f)) {
            val time = if (startDay(f) <= today) "On now" else dateRange(startDay(f), endDay(f))
            return SportRowText(f.title, f.league, time, live = false, key = keyOf(f))
        }
        return if (isLive(f, now)) SportRowText(f.title, f.league, "", live = true, key = keyOf(f))
        else SportRowText(f.title, f.league, formatTime(f.startMs, zone), live = false, key = keyOf(f))
    }

    fun keyOf(f: SportFixture) = "${f.chip}:${f.league}:${f.id}"

    private fun short(day: LocalDate) = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US) + " " + day.dayOfMonth
    private fun month(day: LocalDate) = day.month.getDisplayName(TextStyle.SHORT, Locale.US)

    /** "Thu 19 to Sun 22 Nov", or with both months when they differ. */
    fun dateRange(from: LocalDate, to: LocalDate): String = when {
        to <= from -> "${short(from)} ${month(from)}"
        from.month == to.month && from.year == to.year -> "${short(from)} to ${short(to)} ${month(to)}"
        else -> "${short(from)} ${month(from)} to ${short(to)} ${month(to)}"
    }

    /** What a chip says when it has nothing. */
    fun emptyText(chip: String): String = when (chip) {
        ALL -> "No games in the next 14 days."
        "UFC", "F1", "Golf", "Tennis" -> "No $chip events in the next 14 days."
        else -> "No $chip games in the next 14 days."
    }

    /** "10 min ago", "3 h ago", "3 days ago". Never under a minute. */
    fun ageText(updatedAt: Long, now: Long): String {
        val minutes = ((now - updatedAt) / 60_000L).coerceAtLeast(1L)
        val hours = minutes / 60
        return when {
            minutes < 60 -> "$minutes min ago"
            hours < 48 -> "$hours h ago"
            else -> "${hours / 24} days ago"
        }
    }

    /** The line above the list after a refresh that did not fully work, or null. */
    fun refreshNote(failed: List<String>, allFailed: Boolean, updatedAt: Long?, now: Long): String? {
        if (failed.isEmpty()) return null
        if (allFailed && updatedAt != null) return "Couldn't refresh. Showing the schedule from ${ageText(updatedAt, now)}."
        val names = if (failed.size == 1) failed[0] else failed.dropLast(1).joinToString(", ") + " and " + failed.last()
        return "Couldn't refresh $names."
    }

    fun updatedText(updatedAt: Long, now: Long) = "Schedule from ESPN · updated ${ageText(updatedAt, now)}"
}
