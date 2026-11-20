package tv.porch.app.parse

import java.time.LocalDate
import java.time.OffsetDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import tv.porch.app.domain.SportFixture
import tv.porch.app.domain.SportKind
import tv.porch.app.domain.SportLeague
import tv.porch.app.domain.SportRules

/** A season calendar from a scoreboard reply: [type] is "day" or "list". */
data class EspnCalendar(val type: String?, val days: List<LocalDate>, val lastEnd: LocalDate?)

/**
 * One ESPN scoreboard reply as fixtures. Reads only ids, names, dates, status, session type, team names and
 * the calendar. It never opens `score`, `winner`, `linescores`, `statistics`, `leaders` or any other result field.
 */
object EspnScoreboard {
    data class Parsed(val fixtures: List<SportFixture>, val calendar: EspnCalendar)

    fun parse(text: String, league: SportLeague): Parsed {
        val root = Json.parseToJsonElement(text) as? JsonObject ?: return Parsed(emptyList(), EspnCalendar(null, emptyList(), null))
        val events = (root["events"] as? JsonArray).orEmpty()
        val fixtures = events.flatMap { (it as? JsonObject)?.let { event -> fixturesOf(event, league) }.orEmpty() }
        return Parsed(fixtures.distinctBy { it.id }, calendarOf(root))
    }

    private fun fixturesOf(event: JsonObject, league: SportLeague): List<SportFixture> {
        val id = event.text("id") ?: return emptyList()
        val state = stateOf(event)
        // ESPN marks some running tennis events "post"; a tournament's end date decides instead.
        if (state == "post" && league.kind != SportKind.Tournament) return emptyList()
        val name = event.text("name").orEmpty()
        val start = instant(event.text("date"))
        return when (league.kind) {
            SportKind.Team -> {
                val competition = competitions(event).firstOrNull()
                val startMs = start ?: instant(competition?.text("date")) ?: return emptyList()
                listOf(fixture(id, teamTitle(competition) ?: name, league, startMs, inPlay = state == "in"))
            }
            SportKind.Event -> listOf(fixture(id, name, league, start ?: return emptyList(), inPlay = state == "in"))
            SportKind.Tournament -> {
                val startMs = start ?: return emptyList()
                val from = SportRules.espnZone.let { java.time.Instant.ofEpochMilli(startMs).atZone(it).toLocalDate() }
                val to = instant(event.text("endDate"))?.let { java.time.Instant.ofEpochMilli(it).atZone(SportRules.espnZone).toLocalDate() } ?: from
                listOf(fixture(id, name, league, startMs, from.toString(), maxOf(from, to).toString(), inPlay = state == "in"))
            }
            SportKind.Sessions -> {
                val label = event.text("shortName") ?: name
                competitions(event).mapNotNull { session ->
                    val code = (session["type"] as? JsonObject)?.text("abbreviation") ?: return@mapNotNull null
                    if (code.startsWith("FP", ignoreCase = true)) return@mapNotNull null
                    val sessionState = stateOf(session)
                    if (sessionState == "post") return@mapNotNull null
                    val startMs = instant(session.text("date")) ?: return@mapNotNull null
                    val sessionName = when (code.lowercase()) {
                        "qual" -> "Qualifying"
                        else -> code
                    }
                    fixture("$id:${session.text("id") ?: code}", "$label · $sessionName", league, startMs, inPlay = (sessionState ?: state) == "in")
                }
            }
        }
    }

    private fun fixture(id: String, title: String, league: SportLeague, startMs: Long, startDay: String? = null, endDay: String? = null, inPlay: Boolean) =
        SportFixture(id, title, league.label, league.chip, startMs, startDay, endDay, inPlay)

    /** "Home v Away", the Australian way. Null when the competitors are missing. */
    private fun teamTitle(competition: JsonObject?): String? {
        val sides = (competition?.get("competitors") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        fun team(side: String) = sides.firstOrNull { it.text("homeAway") == side }
            ?.let { (it["team"] as? JsonObject)?.text("displayName") }
        val home = team("home") ?: return null
        val away = team("away") ?: return null
        return "$home v $away"
    }

    private fun competitions(event: JsonObject) = (event["competitions"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

    private fun stateOf(item: JsonObject): String? =
        ((item["status"] as? JsonObject)?.get("type") as? JsonObject)?.text("state")

    private fun calendarOf(root: JsonObject): EspnCalendar {
        val first = ((root["leagues"] as? JsonArray)?.firstOrNull() as? JsonObject)
            ?: return EspnCalendar(null, emptyList(), null)
        val days = mutableListOf<LocalDate>()
        var lastEnd: LocalDate? = null
        fun end(value: String?) {
            val day = datePart(value) ?: return
            if (lastEnd == null || day > lastEnd!!) lastEnd = day
        }
        for (entry in (first["calendar"] as? JsonArray).orEmpty()) {
            when (entry) {
                is JsonPrimitive -> datePart(entry.contentOrNull)?.let { days += it }
                is JsonObject -> {
                    end(entry.text("endDate"))
                    for (inner in (entry["entries"] as? JsonArray).orEmpty()) (inner as? JsonObject)?.let { end(it.text("endDate")) }
                }
                else -> Unit
            }
        }
        return EspnCalendar(first.text("calendarType"), days, lastEnd)
    }

    private fun datePart(value: String?): LocalDate? = value?.takeIf { it.length >= 10 }?.let { runCatching { LocalDate.parse(it.substring(0, 10)) }.getOrNull() }

    /** "2026-11-21T12:30Z" and "2026-11-21T12:30:00Z" both read. */
    private fun instant(value: String?): Long? = value?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
}
