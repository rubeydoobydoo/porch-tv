package tv.porch.app.parse

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Xtream Codes live API mapping.
 *
 * Live play URLs are `{server}/live/{user}/{pass}/{id}.ts`.
 * Catch-up URLs are `{server}/timeshift/{user}/{pass}/{minutes}/{yyyy-MM-dd:HH-mm}/{id}.ts`,
 * with the start corrected by the guide offset and written in the panel's own time zone
 * (`server_info.timezone` from the login reply; UTC when the panel gives none). See the README.
 */
object XtreamUrls {
    fun normalizeServer(raw: String): String {
        var text = raw.trim()
        if (!text.startsWith("http://") && !text.startsWith("https://")) {
            text = "http://$text"
        }
        text = text.substringBefore('?').trimEnd('/')
        text = text.removeSuffix("/player_api.php").trimEnd('/')
        return text
    }

    fun api(server: String, username: String, password: String, action: String): String {
        val base = normalizeServer(server)
        return "$base/player_api.php?username=${enc(username)}&password=${enc(password)}&action=$action"
    }

    /** The login reply: the same address as [api] with no action. It carries `server_info`. */
    fun login(server: String, username: String, password: String): String {
        val base = normalizeServer(server)
        return "$base/player_api.php?username=${enc(username)}&password=${enc(password)}"
    }

    /** The panel's own guide, used when no guide address is typed. Never log or show it. */
    fun xmltv(server: String, username: String, password: String): String {
        val base = normalizeServer(server)
        return "$base/xmltv.php?username=${enc(username)}&password=${enc(password)}"
    }

    /** The panel's zone, or UTC when the id is missing or not a real zone. Never throws. */
    fun zoneOf(id: String?): ZoneId = try {
        if (id.isNullOrBlank()) ZoneOffset.UTC else ZoneId.of(id)
    } catch (error: Exception) {
        ZoneOffset.UTC
    }

    fun live(server: String, username: String, password: String, streamId: Int, extension: String = "ts"): String {
        val base = normalizeServer(server)
        val ext = extension.trim('.').ifBlank { "ts" }
        return "$base/live/${enc(username)}/${enc(password)}/$streamId.$ext"
    }

    fun catchup(
        server: String,
        username: String,
        password: String,
        streamId: Int,
        startUtc: Long,
        durationMinutes: Int,
        zone: ZoneId = ZoneOffset.UTC,
    ): String {
        val base = normalizeServer(server)
        val minutes = durationMinutes.coerceIn(1, 12 * 60)
        val stamp = CATCHUP_STAMP.withZone(zone).format(Instant.ofEpochMilli(startUtc))
        return "$base/timeshift/${enc(username)}/${enc(password)}/$minutes/$stamp/$streamId.ts"
    }

    fun vodCategories(server: String, username: String, password: String): String =
        api(server, username, password, "get_vod_categories")

    /** Films, all or one category's. Never log or show it. */
    fun vodStreams(server: String, username: String, password: String, categoryId: String? = null): String {
        val more = if (categoryId.isNullOrBlank()) "" else "&category_id=${enc(categoryId)}"
        return api(server, username, password, "get_vod_streams") + more
    }

    fun vodInfo(server: String, username: String, password: String, vodId: Long): String =
        api(server, username, password, "get_vod_info") + "&vod_id=$vodId"

    /** A film's play address: `{server}/movie/{user}/{pass}/{id}.{ext}`, "mp4" when the panel gave no extension. */
    fun movie(server: String, username: String, password: String, streamId: Long, extension: String? = null): String {
        val base = normalizeServer(server)
        val ext = extension.orEmpty().trim().trim('.').ifBlank { "mp4" }
        return "$base/movie/${enc(username)}/${enc(password)}/$streamId.$ext"
    }

    fun seriesCategories(server: String, username: String, password: String): String =
        api(server, username, password, "get_series_categories")

    /** Shows, all or one category's. Never log or show it. */
    fun seriesList(server: String, username: String, password: String, categoryId: String? = null): String {
        val more = if (categoryId.isNullOrBlank()) "" else "&category_id=${enc(categoryId)}"
        return api(server, username, password, "get_series") + more
    }

    fun seriesInfo(server: String, username: String, password: String, seriesId: Long): String =
        api(server, username, password, "get_series_info") + "&series_id=$seriesId"

    /** An episode's play address: `{server}/series/{user}/{pass}/{id}.{ext}`, "mp4" when the panel gave no extension. */
    fun episode(server: String, username: String, password: String, episodeId: Long, extension: String? = null): String {
        val base = normalizeServer(server)
        val ext = extension.orEmpty().trim().trim('.').ifBlank { "mp4" }
        return "$base/series/${enc(username)}/${enc(password)}/$episodeId.$ext"
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    private val CATCHUP_STAMP: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd:HH-mm")
}

object XtreamJsonFormat {
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }
}

object XtreamMapper {
    /**
     * If the panel says archive is on but gives no duration, allow 3 days.
     * Panels that send a duration use that number.
     */
    const val ARCHIVE_DAYS_WHEN_UNSPECIFIED: Int = 3

    /** True when the body is a login reply: a JSON object with `user_info`. Never throws. */
    fun isLoginReply(body: String): Boolean = try {
        (XtreamJsonFormat.json.parseToJsonElement(body.trim()) as? JsonObject)?.get("user_info") is JsonObject
    } catch (error: Exception) {
        false
    }

    /** `server_info.timezone` from the login reply, only when it is a real zone. Never throws. */
    fun serverTimeZone(body: String): String? = try {
        val info = XtreamJsonFormat.json.parseToJsonElement(body.trim()).let { it as? JsonObject }
            ?.get("server_info") as? JsonObject
        val zone = (info?.get("timezone") as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
        if (zone.isNullOrEmpty()) null else zone.also { ZoneId.of(it) }
    } catch (error: Exception) {
        null
    }

    fun categories(body: String): List<XtreamCategory> {
        val trimmed = body.trim()
        if (trimmed.startsWith("{")) {
            throw IllegalArgumentException("The server did not return a category list. Check the login.")
        }
        return XtreamJsonFormat.json.decodeFromString(trimmed)
    }

    fun streams(body: String): List<XtreamLiveStream> {
        val trimmed = body.trim()
        if (trimmed.startsWith("{")) {
            throw IllegalArgumentException("The server did not return a channel list. Check the login.")
        }
        return XtreamJsonFormat.json.decodeFromString(trimmed)
    }

    fun toChannels(
        categoriesBody: String,
        streamsBody: String,
        server: String,
        username: String,
        password: String,
    ): List<ParsedChannel> {
        val groups = categories(categoriesBody).associate { it.categoryId to it.categoryName.ifBlank { "Ungrouped" } }
        return streams(streamsBody).mapIndexedNotNull { index, stream ->
            stream.toChannel(groups, server, username, password, index)
        }
    }
}

private fun XtreamLiveStream.toChannel(
    groups: Map<String, String>,
    server: String,
    username: String,
    password: String,
    index: Int,
): ParsedChannel? {
    if (streamId <= 0) return null
    val type = streamType.trim().lowercase()
    if (type.isNotEmpty() && type != "live") return null
    val group = groups[categoryId]?.takeIf { it.isNotBlank() } ?: "Ungrouped"
    val archive = when {
        tvArchive <= 0 -> 0
        tvArchiveDuration > 0 -> tvArchiveDuration
        else -> XtreamMapper.ARCHIVE_DAYS_WHEN_UNSPECIFIED
    }
    return ParsedChannel(
        identity = "xt:$streamId",
        name = name.ifBlank { "Channel $streamId" },
        number = if (num > 0) num else index + 1,
        tvgId = epgChannelId?.trim()?.ifBlank { null },
        tvgName = name.ifBlank { null },
        group = group,
        logo = streamIcon?.trim()?.ifBlank { null },
        streamUrl = XtreamUrls.live(server, username, password, streamId),
        xtreamStreamId = streamId,
        archiveDays = archive,
    )
}

@Serializable
data class XtreamCategory(
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("category_id")
    val categoryId: String = "",
    @SerialName("category_name")
    val categoryName: String = "",
)

@Serializable
data class XtreamLiveStream(
    @Serializable(with = FlexibleIntSerializer::class)
    val num: Int = 0,
    val name: String = "",
    @SerialName("stream_type")
    val streamType: String = "",
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("stream_id")
    val streamId: Int = 0,
    @SerialName("stream_icon")
    val streamIcon: String? = null,
    @SerialName("epg_channel_id")
    val epgChannelId: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("category_id")
    val categoryId: String = "",
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("tv_archive")
    val tvArchive: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("tv_archive_duration")
    val tvArchiveDuration: Int = 0,
)

object FlexibleIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleInt", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)

    override fun deserialize(decoder: Decoder): Int {
        val input = decoder as? JsonDecoder ?: return decoder.decodeInt()
        return when (val element = input.decodeJsonElement()) {
            is JsonNull -> 0
            is JsonPrimitive -> when {
                element.booleanOrNull != null -> if (element.booleanOrNull == true) 1 else 0
                element.intOrNull != null -> element.intOrNull ?: 0
                element.longOrNull != null -> (element.longOrNull ?: 0L).toInt()
                element.doubleOrNull != null -> (element.doubleOrNull ?: 0.0).toInt()
                element.isString -> element.content.toIntOrNull()
                    ?: element.content.toDoubleOrNull()?.toInt()
                    ?: if (element.content.equals("true", ignoreCase = true)) 1 else 0
                else -> 0
            }
            else -> 0
        }
    }
}

object FlexibleStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)

    override fun deserialize(decoder: Decoder): String {
        val input = decoder as? JsonDecoder ?: return decoder.decodeString()
        return when (val element = input.decodeJsonElement()) {
            is JsonNull -> ""
            is JsonPrimitive -> element.content
            else -> ""
        }
    }
}
