package tv.porch.app.parse

import java.io.InputStream
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** One show from `get_series`, cleaned. */
data class XtreamShow(
    val seriesId: Long,
    val name: String,
    val coverUrl: String?,
    val categoryId: String,
    val rating: Double?,
    val year: Int?,
    val plot: String?,
    val genre: String?,
    val addedAt: Long?,
)

/** One episode from `get_series_info`. */
data class XtreamEpisode(
    val id: Long,
    val season: Int,
    val number: Int,
    val title: String,
    val ext: String?,
    val durationSecs: Int?,
    val plot: String?,
)

/** A season's episodes, in order. */
data class XtreamSeason(val number: Int, val episodes: List<XtreamEpisode>)

/** The detail and episode list from `get_series_info`. */
data class XtreamShowInfo(
    val plot: String?,
    val genre: String?,
    val year: Int?,
    val rating: Double?,
    val coverUrl: String?,
    val seasons: List<XtreamSeason>,
)

@Serializable
private data class RawShow(
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("series_id")
    val seriesId: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    val name: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    val cover: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("category_id")
    val categoryId: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    val rating: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    val year: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    val releaseDate: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("release_date")
    val releaseDateSnake: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    val plot: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    val genre: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("last_modified")
    val lastModified: String = "",
)

object XtreamSeries {
    /**
     * Shows one at a time from a `get_series` reply. Bad items are skipped; one object is a one-show list.
     * [skipped] gets the id of a skipped item that still has one, so pruning can keep its stored row.
     */
    fun showRows(input: InputStream, skipped: (Long) -> Unit = {}): Sequence<XtreamShow> =
        XtreamVod.jsonItems(input).mapNotNull { element ->
            val show = try {
                toShow(XtreamJsonFormat.json.decodeFromJsonElement(RawShow.serializer(), element))
            } catch (error: Exception) {
                null
            }
            if (show == null) XtreamVod.idOf(element, "series_id")?.let(skipped)
            show
        }

    private fun toShow(raw: RawShow): XtreamShow? {
        val id = raw.seriesId.trim().let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() } ?: return null
        if (id <= 0) return null
        val name = raw.name.trim()
        if (name.isEmpty()) return null
        return XtreamShow(
            seriesId = id,
            name = name,
            coverUrl = raw.cover.trim().ifBlank { null },
            categoryId = raw.categoryId.trim(),
            rating = XtreamVod.rating(raw.rating),
            year = XtreamVod.year(raw.year, raw.releaseDate.ifBlank { raw.releaseDateSnake }, name),
            plot = raw.plot.trim().ifBlank { null },
            genre = raw.genre.trim().ifBlank { null },
            addedAt = raw.lastModified.trim().toLongOrNull()?.takeIf { it > 0 },
        )
    }

    /** Reads a `get_series_info` reply. Never throws; a reply that is not understood gives null. */
    fun showInfo(body: String, title: String? = null): XtreamShowInfo? = try {
        val root = XtreamJsonFormat.json.parseToJsonElement(body.trim()) as? JsonObject
        val info = root?.get("info") as? JsonObject
        val episodes = root?.get("episodes")
        if (root == null || (info == null && episodes == null)) null else {
            fun text(source: JsonObject?, vararg keys: String): String? =
                keys.firstNotNullOfOrNull { key -> (source?.get(key) as? JsonPrimitive)?.contentOrNull?.trim()?.ifBlank { null } }
            val groups: List<Pair<Int?, JsonElement>> = when (episodes) {
                is JsonObject -> episodes.entries.map { (key, value) -> key.trim().toIntOrNull() to value }
                is JsonArray -> episodes.map { null to it }
                else -> emptyList()
            }
            val seasons = groups.flatMap { (key, value) ->
                val list = (value as? JsonArray) ?: if (value is JsonObject) JsonArray(listOf(value)) else JsonArray(emptyList())
                list.mapNotNull { episode(it, key) }
            }.distinctBy { it.id } // Some panels repeat an episode; a repeated id would crash the episode list.
                .groupBy { it.season }.toSortedMap().map { (number, list) ->
                XtreamSeason(number, list.sortedBy { it.number })
            }.filter { it.episodes.isNotEmpty() }
            XtreamShowInfo(
                plot = text(info, "plot", "description"),
                genre = text(info, "genre"),
                year = XtreamVod.year(text(info, "year"), text(info, "releaseDate", "releasedate", "release_date"), title),
                rating = XtreamVod.rating(text(info, "rating")),
                coverUrl = text(info, "cover", "cover_big"),
                seasons = seasons,
            ).takeIf { info != null || seasons.isNotEmpty() }
        }
    } catch (error: Exception) {
        null
    }

    private fun episode(element: JsonElement, seasonKey: Int?): XtreamEpisode? {
        val item = element as? JsonObject ?: return null
        fun prim(source: JsonObject, key: String) = (source[key] as? JsonPrimitive)?.contentOrNull?.trim()?.ifBlank { null }
        val id = prim(item, "id")?.let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() }?.takeIf { it > 0 } ?: return null
        val season = seasonKey ?: prim(item, "season")?.toDoubleOrNull()?.toInt() ?: 1
        val number = prim(item, "episode_num")?.toDoubleOrNull()?.toInt() ?: 0
        val info = item["info"] as? JsonObject
        val seconds = info?.let { prim(it, "duration_secs") }?.toDoubleOrNull()?.toInt()?.takeIf { it > 0 }
            ?: info?.let { prim(it, "duration") }?.let { XtreamVod.clockSeconds(it) }
        return XtreamEpisode(
            id = id,
            season = season,
            number = number,
            title = prim(item, "title") ?: "Episode $number",
            ext = prim(item, "container_extension")?.trim('.')?.ifBlank { null },
            durationSecs = seconds,
            plot = info?.let { prim(it, "plot") },
        )
    }
}
