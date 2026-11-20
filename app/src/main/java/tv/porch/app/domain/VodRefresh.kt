package tv.porch.app.domain

/** What a finished Movies or Series category download may do to the stored rows. */
data class CategoryRefresh(
    /** Delete the category's rows the download did not list. */
    val prune: Boolean,
    /** Stamp the category's fetched-at key, so it is not downloaded again for a day. */
    val markFetched: Boolean,
)

/**
 * Rules for refreshing Movies and Series. A failed, cancelled or cut-off download never reaches these: it throws
 * first, so nothing is pruned and nothing is marked fresh.
 */
object VodRefresh {
    /**
     * [rows] is how many items the whole download wrote; [wasList] says the reply was a JSON array (an error
     * object or an empty body is not). An empty list is suspect, like an empty playlist, so it prunes nothing,
     * but it still counts as fetched so the chip does not download it on every visit.
     */
    fun afterCategory(rows: Int, wasList: Boolean): CategoryRefresh =
        CategoryRefresh(prune = rows > 0 && wasList, markFetched = wasList)

    /** A category list replaces the stored one only when it has something. */
    fun replaceCategories(freshCount: Int): Boolean = freshCount > 0

    /**
     * Continue tiles from progress entries (newest first) and the items still listed. An entry whose item the
     * provider dropped is hidden, not deleted, so it comes back with its place if the item is listed again.
     */
    fun <P, T> continueTiles(points: List<P>, listed: Map<Long, T>, id: (P) -> Long, max: Int): List<Pair<P, T>> =
        points.asSequence().mapNotNull { point -> listed[id(point)]?.let { point to it } }.take(max).toList()

    /**
     * The categories a downloaded title is filed under. A category download lists it under the category that was
     * asked for, even when the item names another one (a panel can list a title in several categories and still
     * name only its first), and under its own named category too. The full list ([asked] null) knows only the
     * named one. A title always gets at least one listing, even a blank one, so it is never left with none.
     */
    fun listedUnder(asked: String?, own: String): List<String> {
        val named = own.takeIf { it.isNotBlank() }
        return when {
            asked == null -> listOf(named ?: "")
            named == null || named == asked -> listOf(asked)
            else -> listOf(asked, named)
        }
    }
}
