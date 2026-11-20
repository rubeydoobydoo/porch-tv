package tv.porch.app.domain

/** Which chip the full guide sits on. Filters are "fav", "all", or a group name. */
object GuideFilterRule {
    /**
     * Opening on Favourites with none is an empty page, so an unpicked "fav" becomes "all" once channels are
     * showing. A viewer's own Favourites pick is left alone, even if they remove the last favourite.
     */
    fun resolve(filter: String, viewerPicked: Boolean, groups: List<String>, channelCount: Int, hasFavourites: Boolean): String = when {
        filter == "fav" -> if (!viewerPicked && channelCount > 0 && !hasFavourites) "all" else "fav"
        filter == "all" || filter in groups -> filter
        hasFavourites -> "fav"
        // The first chip is Favourites, which may be empty, so the All chip is preferred when there is one.
        else -> "all".takeIf { it in groups } ?: groups.firstOrNull() ?: "all"
    }
}
