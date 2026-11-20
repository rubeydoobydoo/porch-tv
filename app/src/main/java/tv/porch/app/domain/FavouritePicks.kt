package tv.porch.app.domain

/** The short list a new install offers to favourite: well-carried channels with a guide and a logo, a few of each kind. */
object FavouritePicks {
    const val PER_KIND = 6
    private val shown = listOf(Kind.Sport, Kind.News, Kind.Movies, Kind.Kids, Kind.Docs, Kind.Music)

    private val loop = Regex("""24\s*/?\s*7""")

    data class Section(val kind: Kind, val channels: List<Channel>)

    fun sections(channels: List<Channel>, kindOf: (Channel) -> Kind?): List<Section> {
        // A name carried many times over is a channel people want, so the most-copied names come first, each offered once.
        val usable = channels.filter { it.matchedXmltvId != null && it.logoUrl != null && !loop.containsMatchIn(it.shownTitle) }
            .groupBy { it.shownTitle.lowercase() }.values
            .sortedWith(compareBy({ -it.size }, { it.first().shownTitle.lowercase() }))
            .map { it.first() }
        return shown.mapNotNull { kind ->
            usable.filter { kindOf(it) == kind }.take(PER_KIND).takeIf { it.isNotEmpty() }?.let { Section(kind, it) }
        }
    }
}
