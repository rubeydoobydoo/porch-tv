package tv.porch.app.domain

/**
 * The "Porch" row on an Android TV launcher: what is on now across the viewer's channels.
 * A card carries a channel number from our own database and public pictures, never a stream, a login or a server.
 */
object LauncherRow {
    const val MAX_CARDS = 10
    private const val SCHEME_HOST = "porch://channel/"
    private val link = Regex("""^porch://channel/(\d{1,18})$""")

    /** One card. [image] is a safe http or https address; [endMs] is guide-shifted. */
    data class Card(val channelId: Long, val channelName: String, val title: String, val endMs: Long, val image: String)

    /**
     * Your channels, then Live sport (the same rows Home draws). Only what is on now, no filler, one card per channel
     * family, at most [MAX_CARDS], and only with a picture: the programme's, else the channel's logo.
     * [pictures] maps a programme id to its picture address. [secrets] maps a source id to text that must never
     * appear in an address we hand out (its username, password and server name).
     */
    fun pick(
        yours: List<LiveRows.Row>,
        sport: List<LiveRows.Row>,
        pictures: Map<Long, String>,
        now: Long,
        families: ChannelFamilies,
        secrets: Map<Long, List<String>>,
    ): List<Card> {
        val seen = HashSet<Long>()
        val cards = ArrayList<Card>()
        for (row in yours + sport) {
            if (cards.size >= MAX_CARDS) break
            val channel = row.channels.first()
            if (row.endMs <= now || row.startMs > now || LiveRows.isFiller(row.title, channel)) continue
            val family = families.copiesOf(channel.id).firstOrNull()?.id ?: channel.id
            if (family in seen) continue
            val banned = secrets[channel.sourceId].orEmpty()
            val image = listOf(pictures[row.programmeId], channel.logoUrl).firstNotNullOfOrNull { safeImage(it, banned) } ?: continue
            seen += family
            cards += Card(channel.id, channel.shownTitle, ProgrammeText.title(row.title), row.endMs, image)
        }
        return cards
    }

    /** The address when it is http or https and none of [banned] (case ignored, blanks skipped) appears in it, written out or percent-encoded. */
    fun safeImage(raw: String?, banned: List<String>): String? {
        val url = LogoAddress.clean(raw) ?: return null
        val forms = listOf(url, decoded(url))
        return url.takeUnless { banned.any { b -> b.isNotBlank() && forms.any { f -> f.contains(b.trim(), ignoreCase = true) } } }
    }

    /** The address with its %xx escapes read; a malformed escape leaves the address as it was. */
    private fun decoded(url: String): String = try {
        java.net.URLDecoder.decode(url.replace("+", "%2B"), "UTF-8")
    } catch (e: IllegalArgumentException) {
        url
    }

    fun linkFor(channelId: Long): String = "$SCHEME_HOST$channelId"

    /** "porch://channel/123" gives 123. Anything else gives null. */
    fun channelIdFrom(text: String?): Long? = text?.let { link.matchEntire(it)?.groupValues?.get(1)?.toLongOrNull() }

    /** Cards as one comparable text, so an unchanged row is not written again. */
    fun keyOf(cards: List<Card>): String = cards.joinToString("\n") { "${it.channelId}|${it.title}|${it.endMs}|${it.image}" }
}
