package tv.porch.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable
import tv.porch.app.parse.EspnCalendar

/** One finished game or event. [reveal] is the spoiler: shown only after OK on its row. [playedMs] is the game, race or final's start. */
@Serializable
data class SportResult(val id: String, val title: String, val league: String, val chip: String, val playedMs: Long, val reveal: String)

/** One team, driver or player on a ladder. [stats] is the spoiler text after the position, e.g. "27 pts · 10 played". */
@Serializable
data class LadderRow(val name: String, val position: Int, val stats: String)

/** One table. [group] is ESPN's child name ("Eastern Conference") when a league has more than one table, else null. */
@Serializable
data class Ladder(val league: String, val chip: String, val group: String?, val rows: List<LadderRow>)

/** What a results or ladder row shows. A result's [shown] is "Hidden" until [revealed]. A ladder row is always revealed and has a [position]. */
data class ScoreRow(val key: String, val title: String, val meta: String, val shown: String, val revealed: Boolean, val followed: Boolean, val position: Int? = null)

data class ScoreSection(val heading: String, val rows: List<ScoreRow>)

/** Where a league's ladder comes from. */
enum class LadderSource { Standings, Rankings, None }

/** Results (last 7 days) and ladders: addresses, the window, spoiler hiding and the words. All pure. */
object SportScoreRules {
    const val RESULT_DAYS = 7
    const val TENNIS_TOP = 20
    const val HIDDEN = "Hidden"
    private const val DAY_MS = 86_400_000L
    private val compact = DateTimeFormatter.BASIC_ISO_DATE

    /** First and last ESPN day to ask for results: 7 days back to today. */
    fun pastWindow(now: Long): Pair<LocalDate, LocalDate> =
        SportRules.espnDay(now - RESULT_DAYS * DAY_MS) to SportRules.espnDay(now)

    fun resultRangeUrl(league: SportLeague, now: Long): String {
        val (from, to) = pastWindow(now)
        return "${SportRules.plainUrl(league)}?dates=${from.format(compact)}-${to.format(compact)}"
    }

    /** Like [SportRules.daysToAsk] but over the past window. */
    fun pastDaysToAsk(calendar: EspnCalendar, now: Long): List<LocalDate> {
        val (from, to) = pastWindow(now)
        return when (calendar.type) {
            "day" -> calendar.days.filter { it >= from && it <= to }.distinct().sorted()
            "list" -> {
                val end = calendar.lastEnd
                if (end == null || end < from) emptyList() else generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.toList()
            }
            else -> emptyList()
        }
    }

    fun ladderSource(league: SportLeague): LadderSource = when (league.chip) {
        "Tennis" -> LadderSource.Rankings
        "UFC", "Golf" -> LadderSource.None
        else -> LadderSource.Standings
    }

    fun standingsUrl(league: SportLeague) = "https://site.api.espn.com/apis/v2/sports/${league.path}/standings"

    fun rankingsUrl(league: SportLeague) = "https://site.api.espn.com/apis/site/v2/sports/${league.path}/rankings"

    fun resultKey(r: SportResult) = "r:${r.chip}:${r.league}:${r.id}"

    fun ladderKey(l: Ladder, row: LadderRow) = "l:${l.league}:${l.group.orEmpty()}:${row.name}"

    // Each follow is read once and each row once, not once per comparison while sorting.
    private fun followed(wanted: List<List<String>>, vararg texts: String): Boolean {
        if (wanted.isEmpty()) return false
        val read = texts.map(FollowTerms::words)
        return wanted.any { t -> read.any { FollowTerms.matchesWords(t, it) } }
    }

    /** Results from the last 7 days, newest day first. Every row says [HIDDEN] until its key is in [revealed]. */
    fun resultSections(results: List<SportResult>, now: Long, zone: ZoneId, follows: List<String>, revealed: Set<String>): List<ScoreSection> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val wanted = follows.map(FollowTerms::words)
        return results.filter { it.playedMs >= now - RESULT_DAYS * DAY_MS && it.playedMs <= now }
            .groupBy { Instant.ofEpochMilli(it.playedMs).atZone(zone).toLocalDate() }
            .toSortedMap(compareByDescending { it })
            .map { (day, list) ->
                val ordered = list.map { it to followed(wanted, it.title, it.league) }
                    .sortedWith(compareBy({ !it.second }, { -it.first.playedMs }, { it.first.title }))
                val heading = when (day) {
                    today -> "Today"
                    today.minusDays(1) -> "Yesterday"
                    else -> formatGuideDay(day.atStartOfDay(zone).toInstant().toEpochMilli(), zone)
                }
                ScoreSection(heading, ordered.map { (r, follow) ->
                    val key = resultKey(r)
                    val open = key in revealed
                    ScoreRow(key, r.title, r.league, if (open) r.reveal else HIDDEN, open, follow)
                })
            }
    }

    /** One section per ladder, rows in position order. Always shown; followed teams are marked but do not move. */
    fun ladderSections(ladders: List<Ladder>, follows: List<String>): List<ScoreSection> {
        val wanted = follows.map(FollowTerms::words)
        return ladders.map { l ->
            val heading = if (l.group != null) "${l.league} · ${l.group}" else l.league
            ScoreSection(heading, l.rows.sortedBy { it.position }.map { row ->
                ScoreRow(ladderKey(l, row), row.name, "", row.stats, true, followed(wanted, row.name), row.position)
            })
        }
    }

    @JvmName("resultsForChip")
    fun forChip(results: List<SportResult>, chip: String): List<SportResult> =
        if (chip == SportRules.ALL) results else results.filter { it.chip == chip }

    @JvmName("laddersForChip")
    fun forChip(ladders: List<Ladder>, chip: String): List<Ladder> =
        if (chip == SportRules.ALL) ladders else ladders.filter { it.chip == chip }

    /** 1st, 2nd, 3rd, 4th, 11th, 21st, 101st. */
    fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
        return "$n$suffix"
    }

    fun emptyResults(chip: String): String =
        if (chip == SportRules.ALL) "No results in the last 7 days." else "No $chip results in the last 7 days."

    fun emptyLadders(chip: String): String = when (chip) {
        "UFC" -> "No ladder for UFC."
        "Golf" -> "No ladder for golf."
        else -> "No ladder yet."
    }

    /** [kind] is "Results" or "Ladders". */
    fun updatedText(kind: String, updatedAt: Long, now: Long) = "$kind from ESPN · updated ${SportRules.ageText(updatedAt, now)}"

    /** [word] is "results" or "ladders". */
    fun refreshNote(word: String, failed: List<String>, allFailed: Boolean, updatedAt: Long?, now: Long): String? {
        if (failed.isEmpty()) return null
        if (allFailed && updatedAt != null) return "Couldn't refresh. Showing the $word from ${SportRules.ageText(updatedAt, now)}."
        val names = if (failed.size == 1) failed[0] else failed.dropLast(1).joinToString(", ") + " and " + failed.last()
        return "Couldn't refresh $names."
    }
}
