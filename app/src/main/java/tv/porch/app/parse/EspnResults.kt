package tv.porch.app.parse

import java.time.LocalDate
import java.time.OffsetDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import tv.porch.app.domain.SportKind
import tv.porch.app.domain.SportLeague
import tv.porch.app.domain.SportResult
import tv.porch.app.domain.SportRules

/** One ESPN scoreboard reply as finished results. The reveal text is the spoiler; nothing else here holds a score. */
object EspnResults {
    data class Parsed(val results: List<SportResult>, val calendar: EspnCalendar)

    fun parse(text: String, league: SportLeague, now: Long): Parsed {
        val root = Json.parseToJsonElement(text) as? JsonObject ?: throw IllegalArgumentException("not an object")
        val events = (root["events"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val results = events.mapNotNull { resultOf(it, league, now) }
        return Parsed(results.distinctBy { it.id }, calendarOf(root))
    }

    private fun resultOf(event: JsonObject, league: SportLeague, now: Long): SportResult? {
        val id = event.text("id") ?: return null
        if (stateOf(event) != "post") return null
        val name = event.text("name").orEmpty()
        val date = instant(event.text("date"))
        fun result(title: String, playedMs: Long, reveal: String) = SportResult(id, title, league.label, league.chip, playedMs, reveal)
        return when (league.kind) {
            SportKind.Team -> {
                val competition = competitions(event).firstOrNull() ?: return null
                val sides = (competition["competitors"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                fun side(which: String) = sides.firstOrNull { it.text("homeAway") == which }
                val home = side("home") ?: return null
                val away = side("away") ?: return null
                val homeName = (home["team"] as? JsonObject)?.text("displayName") ?: return null
                val awayName = (away["team"] as? JsonObject)?.text("displayName") ?: return null
                val homeScore = home.text("score")?.takeIf { it.isNotBlank() } ?: return null
                val awayScore = away.text("score")?.takeIf { it.isNotBlank() } ?: return null
                result("$homeName v $awayName", date ?: instant(competition.text("date")) ?: return null, "$homeName $homeScore, $awayName $awayScore")
            }
            SportKind.Event -> {
                val main = competitions(event).lastOrNull { stateOf(it) == "post" } ?: return null
                val sides = competitors(main)
                val winner = sides.firstOrNull { it.flag("winner") }
                val loser = sides.firstOrNull { it !== winner }
                val reveal = if (winner == null) "Main event: no winner"
                else "Main event: ${athlete(winner) ?: return null}" + (loser?.let { athlete(it) }?.let { " beat $it" }.orEmpty())
                result(name, date ?: return null, reveal)
            }
            SportKind.Sessions -> {
                val race = competitions(event).firstOrNull {
                    ((it["type"] as? JsonObject)?.text("abbreviation")).equals("Race", ignoreCase = true) && stateOf(it) == "post"
                } ?: return null
                val sides = competitors(race)
                val winner = sides.firstOrNull { it.flag("winner") } ?: sides.firstOrNull { it.number("order") == 1 } ?: return null
                result(name, instant(race.text("date")) ?: date ?: return null, "Race winner: ${athlete(winner) ?: return null}")
            }
            SportKind.Tournament -> if (league.chip == "Tennis") tennis(event, ::result) else golf(event, now, date, ::result)
        }
    }

    private fun golf(event: JsonObject, now: Long, date: Long?, result: (String, Long, String) -> SportResult): SportResult? {
        val end = instant(event.text("endDate"))
        val finished = end != null && SportRules.espnDay(end) < SportRules.espnDay(now)
        if (!finished) return null
        val leader = competitions(event).firstOrNull()?.let { competitors(it) }.orEmpty()
            .filter { it.number("order") != null }.minByOrNull { it.number("order")!! } ?: return null
        val name = athlete(leader) ?: return null
        val score = leader.text("score")?.takeIf { it.isNotBlank() }
        return result(event.text("name").orEmpty(), end ?: date ?: return null, "Winner: $name" + (score?.let { " ($it)" }.orEmpty()))
    }

    private fun tennis(event: JsonObject, result: (String, Long, String) -> SportResult): SportResult? {
        val found = mutableListOf<String>()
        var latest: Long? = null
        for (group in (event["groupings"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }) {
            val label = when ((group["grouping"] as? JsonObject)?.text("displayName")?.lowercase()) {
                "men's singles" -> "Men's singles"
                "women's singles" -> "Women's singles"
                else -> continue
            }
            val final = competitions(group).firstOrNull {
                ((it["round"] as? JsonObject)?.text("displayName")).equals("Final", ignoreCase = true) && stateOf(it) == "post"
            } ?: continue
            val winner = competitors(final).firstOrNull { it.flag("winner") }?.let { athlete(it) } ?: continue
            found += "$label: $winner"
            instant(final.text("date"))?.let { latest = maxOf(latest ?: it, it) }
        }
        if (found.isEmpty()) return null
        return result(event.text("name").orEmpty(), latest ?: instant(event.text("endDate")) ?: instant(event.text("date")) ?: return null, found.joinToString(". "))
    }

    private fun competitions(item: JsonObject) = (item["competitions"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    private fun competitors(item: JsonObject) = (item["competitors"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    private fun athlete(c: JsonObject) = (c["athlete"] as? JsonObject)?.text("displayName")

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

    private fun instant(value: String?): Long? = value?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    private fun JsonObject.flag(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull == true
    private fun JsonObject.number(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
}
