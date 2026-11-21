package tv.porch.app.domain

/** Search rows for one channel name held by two sources look the same; the source's name tells them apart. */
object SearchRepeats {
    /** The names (lower case) held by more than one source. [named] is (name, sourceId) pairs; one channel listed twice counts once. */
    fun repeated(named: List<Pair<String, Long>>): Set<String> =
        named.groupBy({ it.first.trim().lowercase() }, { it.second }).filterValues { it.distinct().size > 1 }.keys

    /** [meta] with " · source" added when [name] is one of the [repeated] names and the source has a name. */
    fun meta(meta: String, name: String, repeated: Set<String>, sourceName: String?): String =
        if (sourceName.isNullOrBlank() || name.trim().lowercase() !in repeated) meta
        else if (meta.isBlank()) sourceName else "$meta  ·  $sourceName"
}
