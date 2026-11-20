package tv.porch.app.parse

import java.io.BufferedInputStream
import java.io.InputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.decodeToSequence

/** One film from `get_vod_streams`, cleaned. Every field was forgiving on the way in. */
data class XtreamFilm(
    val streamId: Long,
    val name: String,
    val iconUrl: String?,
    val categoryId: String,
    val ext: String?,
    val addedAt: Long?,
    val rating: Double?,
    val year: Int?,
)

/** The extra detail from `get_vod_info`. */
data class XtreamFilmInfo(
    val plot: String?,
    val durationSecs: Int?,
    val genre: String?,
    val year: Int?,
    val rating: Double?,
    val iconUrl: String?,
)

@Serializable
private data class RawFilm(
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("stream_id")
    val streamId: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    val name: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("stream_icon")
    val streamIcon: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("category_id")
    val categoryId: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("container_extension")
    val containerExtension: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    val added: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    val rating: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    val year: String = "",
)

object XtreamVod {
    private val titleYear = Regex("""\((\d{4})\)""")
    private val leadingYear = Regex("""^\s*(\d{4})""")
    private val clock = Regex("""^\s*(\d{1,2}):(\d{2})(?::(\d{2}))?\s*$""")

    /**
     * Films one at a time from a `get_vod_streams` reply, so 199,000 rows never become one String.
     * A single object is read as a one-film list. Items that are not objects, or fail, are skipped.
     * [skipped] gets the id of a skipped item that still has one, so pruning can keep its stored row.
     */
    fun filmRows(input: InputStream, skipped: (Long) -> Unit = {}): Sequence<XtreamFilm> =
        jsonItems(input).mapNotNull { element ->
            val film = try {
                toFilm(XtreamJsonFormat.json.decodeFromJsonElement(RawFilm.serializer(), element))
            } catch (error: Exception) {
                null
            }
            if (film == null) idOf(element, "stream_id")?.let(skipped)
            film
        }

    /** A positive id read from [key] of an item, or null. Shared with [XtreamSeries]. */
    internal fun idOf(element: JsonObject, key: String): Long? {
        val text = (element[key] as? JsonPrimitive)?.contentOrNull?.trim() ?: return null
        return (text.toLongOrNull() ?: text.toDoubleOrNull()?.toLong())?.takeIf { it > 0 }
    }

    /** True when the reply starts as a JSON array (`[`), even an empty one. An error object or an empty body is not. */
    fun isList(input: InputStream): Boolean {
        var first = input.read()
        while (first == ' '.code || first == '\n'.code || first == '\r'.code || first == '\t'.code || first == 0xEF || first == 0xBB || first == 0xBF) {
            first = input.read()
        }
        return first == '['.code
    }

    /** The objects of a JSON array one at a time (a single object is a one-item list). Shared with [XtreamSeries]. */
    @OptIn(ExperimentalSerializationApi::class)
    internal fun jsonItems(input: InputStream): Sequence<JsonObject> {
        val stream = BufferedInputStream(input)
        stream.mark(1)
        var first = stream.read()
        while (first == ' '.code || first == '\n'.code || first == '\r'.code || first == '\t'.code || first == 0xEF || first == 0xBB || first == 0xBF) {
            if (first == 0xEF) {
                // A byte order mark: skip its three bytes and carry on.
                stream.read(); stream.read()
            }
            stream.mark(1)
            first = stream.read()
        }
        if (first < 0) return emptySequence()
        stream.reset()
        val json = XtreamJsonFormat.json
        val elements: Sequence<JsonElement> = if (first == '{'.code) {
            sequenceOf(json.decodeFromStream<JsonElement>(stream))
        } else {
            json.decodeToSequence<JsonElement>(stream, DecodeSequenceMode.ARRAY_WRAPPED)
        }
        return elements.mapNotNull { it as? JsonObject }
    }

    private fun toFilm(raw: RawFilm): XtreamFilm? {
        val id = raw.streamId.trim().let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() } ?: return null
        if (id <= 0) return null
        val name = raw.name.trim()
        if (name.isEmpty()) return null
        return XtreamFilm(
            streamId = id,
            name = name,
            iconUrl = raw.streamIcon.trim().ifBlank { null },
            categoryId = raw.categoryId.trim(),
            ext = raw.containerExtension.trim().trim('.').ifBlank { null },
            addedAt = raw.added.trim().toLongOrNull()?.takeIf { it > 0 },
            rating = rating(raw.rating),
            year = year(raw.year, null, name),
        )
    }

    /** Null unless the number is above 0 and at most 10. */
    fun rating(text: String?): Double? = text?.trim()?.toDoubleOrNull()?.takeIf { it > 0.0 && it <= 10.0 }

    private fun validYear(value: Int?): Int? = value?.takeIf { it in 1888..2100 }

    /** The year field, else the release date, else "(2019)" in the title. */
    fun year(field: String?, releaseDate: String?, title: String?): Int? =
        validYear(field?.trim()?.toIntOrNull())
            ?: validYear(releaseDate?.let { leadingYear.find(it)?.groupValues?.get(1)?.toIntOrNull() })
            ?: validYear(title?.let { titleYear.find(it)?.groupValues?.get(1)?.toIntOrNull() })

    /** Reads a `get_vod_info` reply. Never throws; a reply that is not understood gives null. */
    fun filmInfo(body: String, title: String? = null): XtreamFilmInfo? = try {
        val root = XtreamJsonFormat.json.parseToJsonElement(body.trim()) as? JsonObject
        val info = root?.get("info") as? JsonObject
        if (info == null) null else {
            fun text(vararg keys: String): String? =
                keys.firstNotNullOfOrNull { key -> (info[key] as? JsonPrimitive)?.contentOrNull?.trim()?.ifBlank { null } }
            val seconds = text("duration_secs")?.toDoubleOrNull()?.toInt()?.takeIf { it > 0 }
                ?: text("duration")?.let { clockSeconds(it) }
            XtreamFilmInfo(
                plot = text("plot", "description"),
                durationSecs = seconds,
                genre = text("genre"),
                year = year(text("year"), text("releasedate", "release_date"), title),
                rating = rating(text("rating")),
                iconUrl = text("movie_image", "cover_big"),
            )
        }
    } catch (error: Exception) {
        null
    }

    /** "01:52:10" or "52:10" as seconds, or null. */
    fun clockSeconds(text: String): Int? {
        val match = clock.find(text) ?: return null
        val (a, b, c) = match.destructured
        val seconds = if (c.isEmpty()) a.toInt() * 60 + b.toInt() else a.toInt() * 3600 + b.toInt() * 60 + c.toInt()
        return seconds.takeIf { it > 0 }
    }
}
