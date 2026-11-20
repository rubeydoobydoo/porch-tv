package tv.porch.app.domain

import java.util.Locale

/**
 * The 24/7 tab: channels that play one show on a loop. They live in groups whose names start with "24/7", carry a logo
 * and never have guide data, so no other list shows them. Pure; the screen builds these off the main thread.
 */
object LoopChannels {
    /** The chip at the front that shows every genre. */
    const val ALL = "All"
    private const val OTHER = "Other"

    // The same small raised letters ChannelNames drops ("RAW 60fps" written in superscript).
    private val raised = Regex("""[ʰ-˿ᴬ-ᶿ⁰-₟]""")
    private val labelWords = Regex("""(?<![\p{L}\p{N}])(?:vip|raw)(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)
    private val loopLabel = Regex("""^\s*24\s*/\s*7(?![\p{L}\p{N}])""")
    private val frameRate = Regex("""\s*(?<![\p{L}\p{N}])60(?:\s*fps)?\s*$""", RegexOption.IGNORE_CASE)
    private val spaces = Regex("""\s+""")
    private val edges = Regex("""^[\s|:\-–—]+|[\s|:\-–—]+$""")

    // Lower-case inside an all-caps name, unless first: "King of the Hill".
    private val smallWords = setOf("a", "an", "and", "as", "at", "but", "by", "for", "from", "in", "of", "on", "or", "the", "to", "vs", "with")

    fun inLoopGroup(group: String): Boolean = group.trimStart().startsWith("24/7")

    /** A loop channel is in a 24/7 group and is not a section divider. */
    fun isLoop(channel: Channel): Boolean = inLoopGroup(channel.group) && !ChannelNames.isDivider(channel.name)

    /**
     * The loop channels the viewer's picks allow. Picks narrow as on every other screen (favourites and played channels
     * always show; hidden groups are already gone). The guide-coverage rule is skipped: these never have guide data.
     */
    fun listed(channels: List<Channel>, chosen: Set<GroupKey>): List<Channel> =
        GroupChoice.filter(channels, GroupChoice.live(chosen, channels)).filter(::isLoop)

    /** A group name as a genre: "24/7 COMEDY VIP" is "Comedy", "24/7 KIDS/FAMILY VIP" is "Kids/Family". */
    fun genreOf(group: String): String {
        genreCache[group]?.let { return it }
        if (genreCache.size >= GENRE_CACHE_MAX) genreCache.clear()
        return computeGenre(group).also { genreCache[group] = it }
    }

    // Many channels share a group, and every rebuild asks per channel, so each group name is worked out once.
    private val genreCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private const val GENRE_CACHE_MAX = 2_000

    private fun computeGenre(group: String): String {
        var text = raised.replace(group, "")
        text = loopLabel.replace(text, "")
        text = labelWords.replace(text, " ")
        text = spaces.replace(text, " ").trim()
        text = frameRate.replace(text, "")
        text = edges.replace(spaces.replace(text, " "), "").trim()
        if (text.isEmpty()) return OTHER
        return ChannelNames.display(text).title.ifBlank { OTHER }
    }

    /** "All", then each genre the channels have, the one with most channels first and ties A to Z. */
    fun genres(channels: List<Channel>): List<String> {
        val counts = HashMap<String, Int>()
        for (channel in channels) counts.merge(genreOf(channel.group), 1, Int::plus)
        val sorted = counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.key })
        return listOf(ALL) + sorted.map { it.key }
    }

    fun inGenre(channels: List<Channel>, genre: String): List<Channel> =
        if (genre == ALL) channels else channels.filter { genreOf(it.group) == genre }

    /** The show a loop channel plays: the provider label and "24/7" gone, an all-caps name in title case. */
    fun showName(channel: Channel): String {
        val title = ChannelNames.display(channel.name).title
        if (channel.name.any { it.isLowerCase() }) return ShowLookup.joinSpacedLetters(title)
        val words = title.split(' ')
        return ShowLookup.joinSpacedLetters(words.mapIndexed { i, word ->
            val lower = word.lowercase(Locale.US)
            if (i > 0 && lower in smallWords && !words[i - 1].endsWith(":")) lower else word
        }.joinToString(" "))
    }

    /** Favourites first, then A to Z by show name. */
    fun ordered(channels: List<Channel>): List<Channel> =
        channels.map { it to showName(it) }
            .sortedWith(
                compareByDescending<Pair<Channel, String>> { it.first.favourite }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.second }
                    .thenBy { it.first.id },
            )
            .map { it.first }

    /** A logo address this many channels share is the provider's generic one, not the show's. */
    const val SHARED_LOGO_AT = 5

    /** The logo addresses that [SHARED_LOGO_AT] or more of the channels use. Worked out once per list. */
    fun sharedLogos(channels: List<Channel>): Set<String> {
        val counts = HashMap<String, Int>()
        for (channel in channels) LogoAddress.clean(channel.logoUrl)?.let { counts.merge(it, 1, Int::plus) }
        return counts.filterValues { it >= SHARED_LOGO_AT }.keys
    }

    /** The channel's own logo address, or null when it has none or the address is a shared, generic one. */
    fun ownLogo(channel: Channel, shared: Set<String>): String? =
        LogoAddress.clean(channel.logoUrl)?.takeIf { it !in shared }
}
