package tv.porch.app.domain

/** Close-spelling search over film names held in memory. */
object FilmSearch {
    const val LIMIT = 24

    /** Positions in [names] of up to [limit] names that match [needle] closely. */
    fun find(names: List<String>, needle: String, limit: Int = LIMIT): List<Int> {
        if (!FuzzyText.worthTrying(needle)) return emptyList()
        val found = ArrayList<Int>()
        for (i in names.indices) {
            if (FuzzyText.matches(needle, names[i])) {
                found.add(i)
                if (found.size >= limit) break
            }
        }
        return found
    }
}
