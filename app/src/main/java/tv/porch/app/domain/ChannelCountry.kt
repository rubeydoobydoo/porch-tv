package tv.porch.app.domain

/** The countries that get a chip in the full guide. [label] is the chip's name. */
enum class Country(val code: String, val label: String) {
    AU("AU", "Australia"),
    US("US", "US"),
    UK("UK", "UK"),
    NZ("NZ", "NZ"),
    CA("CA", "Canada"),
    IE("IE", "Ireland"),
}

/** Which country a channel is from, read off its group's leading label ("AU| Sport"). Pure. */
object ChannelCountry {
    // After the TV's own country: US, UK, NZ, then the rest.
    private val defaultOrder = listOf(Country.US, Country.UK, Country.NZ, Country.AU, Country.CA, Country.IE)

    // A provider has a few hundred groups for tens of thousands of channels, so each group is read once.
    private val cache = HashMap<String, Country?>()

    fun of(group: String): Country? {
        synchronized(cache) { if (cache.containsKey(group)) return cache[group] }
        val found = fromLabel(ChannelNames.group(group).tag)
        synchronized(cache) {
            if (cache.size > 10_000) cache.clear()
            cache[group] = found
        }
        return found
    }

    fun of(channel: Channel): Country? = of(channel.group)

    /** A label such as "AU", "GB" or "USA". A language label ("EN") is not a country. */
    private fun fromLabel(label: String?): Country? = when (val tag = label?.uppercase()) {
        null -> null
        "GB" -> Country.UK
        "USA" -> Country.US
        else -> Country.entries.firstOrNull { it.code == tag }
    }

    /** The TV's own country (from `Locale.getDefault().country`; GB counts as UK) first, then US, UK, NZ and the rest. */
    fun order(tvCountry: String): List<Country> {
        val own = fromLabel(tvCountry)
        return listOfNotNull(own) + defaultOrder.filter { it != own }
    }

    /** The countries these channels are from, in [order]. */
    fun present(channels: List<Channel>, tvCountry: String): List<Country> {
        val seen = channels.mapNotNullTo(HashSet()) { of(it) }
        return order(tvCountry).filter { it in seen }
    }

    /** The All list: favourites, then countries in [order] (channels from no listed country last), then number inside each. */
    fun sortAll(channels: List<Channel>, tvCountry: String): List<Channel> {
        val rank = order(tvCountry).withIndex().associate { it.value to it.index }
        return channels.sortedWith(
            compareBy<Channel> { if (it.favourite) 0 else 1 }
                .thenBy { of(it)?.let(rank::get) ?: Int.MAX_VALUE }
                .thenBy { it.number }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.shownTitle },
        )
    }
}
