package tv.porch.app.parse

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import tv.porch.app.domain.Ladder
import tv.porch.app.domain.LadderRow
import tv.porch.app.domain.SportLeague
import tv.porch.app.domain.SportScoreRules

/** ESPN standings and rankings replies as ladders. Every stat here is spoiler text; the rules hide it until OK. */
object EspnLadders {
    fun standings(text: String, league: SportLeague): List<Ladder> {
        val root = Json.parseToJsonElement(text) as? JsonObject ?: throw IllegalArgumentException("not an object")
        val children = (root["children"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val tables: List<Pair<String?, JsonObject>> =
            if (children.isNotEmpty()) children.map { it.text("name") to it }
            else if (root["standings"] is JsonObject) listOf(null to root) else emptyList()
        // Group and team names make the screen's list keys, so a missing or repeated one would crash it.
        val seen = mutableSetOf<String>()
        return tables.mapIndexedNotNull { index, (given, table) ->
            val name = given?.takeIf { it.isNotBlank() && seen.add(it) } ?: generateSequence(index + 1) { it + 1 }.map { "Table $it" }.first { seen.add(it) }
            val entries = ((table["standings"] as? JsonObject)?.get("entries") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            val rows = entries.mapNotNull { rowOf(it, league) }.distinctBy { it.name }.sortedBy { it.position }
            if (rows.isEmpty()) null else Ladder(league.label, league.chip, if (tables.size > 1) name else null, rows)
        }
    }

    fun rankings(text: String, league: SportLeague): List<Ladder> {
        val root = Json.parseToJsonElement(text) as? JsonObject ?: throw IllegalArgumentException("not an object")
        val ranks = (((root["rankings"] as? JsonArray)?.firstOrNull() as? JsonObject)?.get("ranks") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val rows = ranks.mapNotNull { r ->
            val position = (r["current"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: return@mapNotNull null
            val name = (r["athlete"] as? JsonObject)?.text("displayName") ?: return@mapNotNull null
            val points = (r["points"] as? JsonPrimitive)?.doubleOrNull
            LadderRow(name, position, points?.let { "${it.toLong()} pts" }.orEmpty())
        }.distinctBy { it.name }.sortedBy { it.position }.take(SportScoreRules.TENNIS_TOP)
        return if (rows.isEmpty()) emptyList() else listOf(Ladder(league.label, league.chip, null, rows))
    }

    private fun rowOf(entry: JsonObject, league: SportLeague): LadderRow? {
        val name = (entry["team"] as? JsonObject)?.text("displayName") ?: (entry["athlete"] as? JsonObject)?.text("displayName") ?: return null
        val stats = (entry["stats"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.associateBy { it.text("name") }
        fun number(key: String): Int? =
            (stats[key]?.get("value") as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt()
                ?: stats[key]?.text("displayValue")?.toIntOrNull()
        fun shown(key: String): String? = stats[key]?.text("displayValue")?.takeIf { it.isNotBlank() }
            ?: (stats[key]?.get("value") as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toLong()?.toString()
        val position = (entry["rank"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: number("rank") ?: number("playoffSeed") ?: return null
        val text = when (league.chip) {
            "AFL", "NRL", "Football" -> listOfNotNull(shown("points")?.let { "$it pts" }, shown("gamesPlayed")?.let { "$it played" }).joinToString(" · ")
            "NFL", "NBA" -> shown("overall")?.let { "Record $it" }.orEmpty()
            "F1" -> (shown("championshipPts") ?: shown("points"))?.let { "$it pts" }.orEmpty()
            else -> ""
        }
        return LadderRow(name, position, text)
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
}
