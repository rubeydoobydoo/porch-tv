package tv.porch.app.domain

/** One chip in the full guide's single row. [key] is what the guide filters on. */
data class GuideChip(val key: String, val label: String)

/** The full guide's chips: Favourites, countries, a few kinds, then All. Pure. */
object GuideChips {
    const val FAV = "fav"
    const val ALL = "all"
    const val COUNTRY_PREFIX = "cc:"
    const val KIND_PREFIX = "kind:"

    /** The kinds that get a chip, in the order they show. */
    private val chipKinds = listOf(Kind.Sport, Kind.News, Kind.Movies, Kind.Kids)

    fun countryKey(country: Country) = COUNTRY_PREFIX + country.code
    fun kindKey(kind: Kind) = KIND_PREFIX + kind.name
    fun isCountry(key: String) = key.startsWith(COUNTRY_PREFIX)
    fun isKind(key: String) = key.startsWith(KIND_PREFIX)

    /**
     * Favourites and All always show. A country or kind shows only when one of [channels] is in it, so pass the
     * channels the guide lists under All (already narrowed by My groups and the guide switch).
     */
    fun build(channels: List<Channel>, tvCountry: String, kindOf: (Channel) -> Kind? = Kinds::of): List<GuideChip> {
        val kinds = Kinds.present(channels, kindOf)
        return buildList {
            add(GuideChip(FAV, "Favourites"))
            ChannelCountry.present(channels, tvCountry).forEach { add(GuideChip(countryKey(it), it.label)) }
            chipKinds.filter { it in kinds }.forEach { add(GuideChip(kindKey(it), it.label)) }
            add(GuideChip(ALL, "All"))
        }
    }

    /** All is country order; every other chip is favourites, then channels with something on now, then number. */
    fun order(channels: List<Channel>, key: String, tvCountry: String, onNowIds: Set<Long>): List<Channel> =
        if (key == ALL) ChannelCountry.sortAll(channels, tvCountry) else ChannelOrder.order(channels, onNowIds)
}
