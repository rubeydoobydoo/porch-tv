package tv.porch.app.domain

/** Channels with no guide data hide from All and Favourites unless the viewer asks to see them. A picked group shows them all. */
object GuideCoverage {
    /** Matched to the guide counts as covered, even if the guide has nothing for it right now. */
    fun withoutGuide(channel: Channel): Boolean = channel.matchedXmltvId == null

    /**
     * [sourcesWithoutGuideUrl] are sources that have no guide at all. Their channels cannot be matched,
     * so they are never hidden.
     */
    fun filter(
        channels: List<Channel>,
        showWithoutGuide: Boolean,
        sourcesWithoutGuideUrl: Set<Long> = emptySet(),
    ): List<Channel> {
        if (showWithoutGuide) return channels
        return channels.filter { !withoutGuide(it) || it.sourceId in sourcesWithoutGuideUrl }
    }

    /** Every chip but a named group hides unmatched channels. A group the viewer picked shows all of its channels. */
    fun hidesUnmatched(filter: String): Boolean =
        filter == "all" || filter == "fav" || GuideChips.isCountry(filter) || GuideChips.isKind(filter)

    fun forFilter(
        channels: List<Channel>,
        filter: String,
        showWithoutGuide: Boolean,
        noGuideSources: Set<Long> = emptySet(),
    ): List<Channel> = if (hidesUnmatched(filter)) filter(channels, showWithoutGuide, noGuideSources) else channels

    /** The channels of one chip: "fav", "all", a country or kind chip (see [GuideChips]), or a group name. */
    fun view(channels: List<Channel>, filter: String, kindOf: (Channel) -> Kind? = Kinds::of): List<Channel> = when {
        filter == "fav" -> channels.filter { it.favourite }
        filter == "all" -> channels
        GuideChips.isCountry(filter) -> {
            val code = filter.removePrefix(GuideChips.COUNTRY_PREFIX)
            channels.filter { ChannelCountry.of(it)?.code == code }
        }
        GuideChips.isKind(filter) -> {
            val name = filter.removePrefix(GuideChips.KIND_PREFIX)
            channels.filter { kindOf(it)?.name == name }
        }
        else -> channels.filter { it.group == filter }
    }

    /** What the guide lists for a chip: one source's channels, narrowed by My groups, the guide switch and the chip. */
    fun listed(
        onSource: List<Channel>,
        chosen: Set<GroupKey>,
        filter: String,
        showWithoutGuide: Boolean,
        noGuideSources: Set<Long> = emptySet(),
        kindOf: (Channel) -> Kind? = Kinds::of,
    ): List<Channel> = view(forFilter(GroupChoice.filter(onSource, chosen), filter, showWithoutGuide, noGuideSources), filter, kindOf)

    /** Chip order: groups with guide data first, then groups without, each A to Z. Only the first few chips show. */
    fun chipGroups(all: List<String>, withGuide: Set<String>): List<String> {
        val (guided, rest) = all.distinct().sorted().partition { it in withGuide }
        return guided + rest
    }
}
