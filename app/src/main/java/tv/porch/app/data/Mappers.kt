package tv.porch.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import tv.porch.app.domain.Channel
import tv.porch.app.domain.ChannelNames
import tv.porch.app.domain.DisplayName
import tv.porch.app.domain.LanguageTags
import tv.porch.app.domain.LogoAddress
import tv.porch.app.domain.Programme
import tv.porch.app.parse.ParsedChannel
import tv.porch.app.parse.ParsedProgramme

fun ChannelFields.toModel(): Channel {
    val group = groupOverride?.takeIf { it.isNotBlank() } ?: groupName
    // Stored at import; a row from before that has divider == null and is cleaned here.
    val shown = if (divider != null) DisplayName(shownTitle ?: name, shownTag, shownCode) else ChannelNames.display(name)
    return Channel(
        id = id,
        sourceId = sourceId,
        name = name,
        number = number,
        group = group,
        favourite = favourite,
        streamUrl = streamUrl,
        xtreamStreamId = xtreamStreamId,
        archiveDays = archiveDays,
        matchedXmltvId = matchedXmltvId,
        lastPlayedAt = lastPlayedAt,
        logoUrl = LogoAddress.clean(logoUrl),
        title = shown.title,
        tag = shown.tag,
        code = shown.code,
    )
}

fun ProgrammeEntity.toModel(): Programme = Programme(
    id = id,
    sourceId = sourceId,
    channelKey = channelKey,
    title = title,
    description = description,
    startUtc = startUtc,
    stopUtc = stopUtc,
    category = category,
)

fun ParsedProgramme.toEntity(sourceId: Long, generation: Long): ProgrammeEntity = ProgrammeEntity(
    sourceId = sourceId,
    generation = generation,
    channelKey = channelKey,
    title = title,
    description = description,
    startUtc = startUtc,
    stopUtc = stopUtc,
    category = category,
    iconUrl = iconUrl,
)

fun ParsedChannel.toEntity(sourceId: Long, previous: ChannelEntity?, matchedXmltvId: String?): ChannelEntity {
    val shown = ChannelNames.display(name)
    return ChannelEntity(
        sourceId = sourceId,
        identity = identity,
        name = name,
        number = number,
        tvgId = tvgId,
        tvgName = tvgName,
        groupName = group,
        groupOverride = previous?.groupOverride,
        logoUrl = logo,
        streamUrl = streamUrl,
        xtreamStreamId = xtreamStreamId,
        archiveDays = archiveDays,
        favourite = previous?.favourite ?: false,
        matchedXmltvId = matchedXmltvId,
        lastPlayedAt = previous?.lastPlayedAt,
        shownTitle = shown.title,
        shownTag = shown.tag,
        shownCode = shown.code,
        divider = ChannelNames.isDivider(name),
    )
}

fun List<ChannelFields>.visible(
    hidden: Set<Pair<Long, String>>,
    languages: Map<Long, String?>,
): List<Channel> {
    return mapNotNull { entity ->
        if (entity.divider ?: ChannelNames.isDivider(entity.name)) return@mapNotNull null
        val group = entity.groupOverride?.takeIf { it.isNotBlank() } ?: entity.groupName
        if ((entity.sourceId to group) in hidden) return@mapNotNull null
        val filter = languages[entity.sourceId]
        if (!filter.isNullOrBlank() && !LanguageTags.allows(group, filter)) return@mapNotNull null
        entity.toModel()
    }
}

/**
 * The same list as [visible], with the work split across the CPU's cores. Cleaning a name is the slow part,
 * and every channel is independent, so the parts are done side by side and joined in order.
 */
suspend fun List<ChannelFields>.visibleInParallel(
    hidden: Set<Pair<Long, String>>,
    languages: Map<Long, String?>,
    parts: Int = Runtime.getRuntime().availableProcessors(),
): List<Channel> {
    val count = minOf(parts, size / MIN_PART)
    if (count < 2) return visible(hidden, languages)
    val size = (this.size + count - 1) / count
    return coroutineScope {
        chunked(size).map { part -> async(Dispatchers.Default) { part.visible(hidden, languages) } }.awaitAll().flatten()
    }
}

private const val MIN_PART = 2_000
