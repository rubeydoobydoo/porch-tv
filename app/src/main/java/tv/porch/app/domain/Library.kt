package tv.porch.app.domain

import tv.porch.app.parse.XtreamUrls

/** A channel the UI can show. Built from a Room row after hidden groups and the language filter. */
data class Channel(
    val id: Long,
    val sourceId: Long,
    val name: String,
    val number: Int,
    val group: String,
    val favourite: Boolean,
    val streamUrl: String,
    val xtreamStreamId: Int?,
    val archiveDays: Int,
    val matchedXmltvId: String?,
    val lastPlayedAt: Long?,
    /** A clean http or https logo address, or null for the letter badge. */
    val logoUrl: String? = null,
    /** The readable name. Mappers fill it; blank means "use [name]". */
    val title: String = "",
    val tag: String? = null,
    val code: String? = null,
) {
    val shownTitle: String get() = title.ifBlank { name }

    /** Badge letters from the readable name, so "US: A&E" and "US: AMC" don't both read "U" plus a letter. */
    val badge: String get() = ChannelBadge.letters(shownTitle)
}

data class Programme(
    val id: Long,
    val sourceId: Long,
    val channelKey: String,
    val title: String,
    val description: String?,
    val startUtc: Long,
    val stopUtc: Long,
    val category: String?,
)

/** What Home's preview fetches once focus settles: the two things a list row doesn't carry. */
data class ProgrammeDetail(val id: Long, val description: String?, val iconUrl: String?)

data class Airing(
    val channel: Channel,
    val programme: Programme,
) {
    /** Key for a card in a row. HD and SD copies can share one programme, so key by channel. */
    val rowKey: Long get() = channel.id
}

data class AiringSlot(
    val channel: Channel,
    val programme: Programme?,
)

data class GroupShelf(
    val group: String,
    val all: List<AiringSlot>,
) {
    val shown: List<AiringSlot> get() = all.take(SHELF_CAP)
    val extra: Int get() = (all.size - SHELF_CAP).coerceAtLeast(0)
}

data class HomeShelves(
    val onNow: List<Airing>,
    val soon: List<Airing>,
    val favourites: List<Channel>,
    val recent: List<Channel>,
    val categories: List<GroupShelf>,
) {
    val onNowShown: List<Airing> get() = onNow.take(SHELF_CAP)
    val onNowExtra: Int get() = (onNow.size - SHELF_CAP).coerceAtLeast(0)
    val soonShown: List<Airing> get() = soon.take(SOON_CAP)
    val soonExtra: Int get() = (soon.size - SOON_CAP).coerceAtLeast(0)
}

const val SHELF_CAP: Int = 5
const val SOON_CAP: Int = 4
const val HOME_CATEGORY_CAP: Int = 6
const val SOON_WINDOW_MS: Long = 30L * 60L * 1000L

data class SourceDraft(
    val name: String = "",
    val kind: String = "m3u",
    val m3uUrl: String = "",
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val xmltvUrl: String = "",
)

/** Returns a cleaned draft, or throws [IllegalArgumentException] with a sentence the UI can show. */
fun SourceDraft.cleaned(): SourceDraft {
    val kindName = kind.trim().lowercase()
    if (kindName != "m3u" && kindName != "xtream") {
        throw IllegalArgumentException("Choose a playlist file or an Xtream login.")
    }
    var guide = xmltvUrl.trim()
    val playlist = m3uUrl.trim()
    val host = server.trim()
    if (kindName == "m3u") {
        if (!playlist.startsWith("http://") && !playlist.startsWith("https://")) {
            throw IllegalArgumentException("The playlist URL should start with http:// or https://.")
        }
        if (guide.isEmpty()) {
            throw IllegalArgumentException("A playlist needs a guide URL that starts with http:// or https://.")
        }
    } else if (host.isBlank() || username.isBlank() || password.isBlank()) {
        throw IllegalArgumentException("Xtream needs a server, a username and a password.")
    } else if (guide.isEmpty()) {
        // An Xtream panel serves its own guide. Never logged or shown.
        guide = XtreamUrls.xmltv(host, username.trim(), password)
    }
    if (!guide.startsWith("http://") && !guide.startsWith("https://")) {
        throw IllegalArgumentException("The guide URL should start with http:// or https://.")
    }
    val label = name.trim().ifBlank {
        val raw = if (kindName == "m3u") playlist else host
        raw.substringAfter("://").substringBefore('/').substringBefore(':').ifBlank { "My TV" }
    }
    return copy(
        name = label,
        kind = kindName,
        m3uUrl = playlist,
        server = host,
        username = username.trim(),
        password = password,
        xmltvUrl = guide,
    )
}
