package tv.porch.app.domain

/** A channel's group, its name, and whether it is already known to be a divider (null: work it out from the name). */
data class GroupedName(val group: String, val name: String, val divider: Boolean? = null)

/**
 * One row on the Channels screen: its label ("EN", "AU", or "Other" for untagged groups), the [name] it shows
 * (a full name where one is known, otherwise the label), its groups and their channels.
 */
data class LanguageRow(val label: String, val groups: List<String>, val channels: Int, val name: String = label)

/** What one tap changes in the saved rows of one source. Hidden rows are only ever removed. */
data class ChannelsChange(
    val removeHidden: Set<String> = emptySet(),
    val addPicks: Set<String> = emptySet(),
    val removePicks: Set<String> = emptySet(),
)

/**
 * One state per group: showing or hidden. [chosen] is a source's saved pick rows, which may include
 * [GroupChoice.RESTRICTED]. A source with any row is restricted: only picked groups show, even if no pick
 * is left. A source with no rows shows every group that is not hidden.
 */
object ChannelsChoice {
    fun showing(group: String, chosen: Set<String>, hidden: Set<String>): Boolean =
        group !in hidden && (chosen.isEmpty() || group in chosen)

    /** The picks that count now. Without the reserved row, picks whose groups have gone are ignored, as on Home. */
    fun effective(chosen: Set<String>, present: Collection<String>): Set<String> =
        if (GroupChoice.RESTRICTED in chosen) chosen else chosen.filterTo(HashSet()) { it in present }

    /** Channels per group, dividers left out. A group of only dividers is not listed. */
    fun groupCounts(rows: Iterable<GroupedName>): Map<String, Int> {
        val counts = LinkedHashMap<String, Int>()
        for (row in rows) {
            if (row.divider ?: ChannelNames.isDivider(row.name)) continue
            counts[row.group] = (counts[row.group] ?: 0) + 1
        }
        return counts
    }

    /** Channels in showing groups. */
    fun keeping(counts: Map<String, Int>, chosen: Set<String>, hidden: Set<String>): Int =
        counts.entries.sumOf { (group, count) -> if (showing(group, chosen, hidden)) count else 0 }

    /**
     * The rows to change to show ([show] true) or hide ([show] false) [targets]. Null [show] means switch: all
     * go hidden if any is showing, otherwise all go showing. [allGroups] is only read when an unrestricted
     * source first hides something.
     */
    fun change(
        targets: Collection<String>,
        show: Boolean?,
        chosen: Set<String>,
        hidden: Set<String>,
        allGroups: () -> Collection<String>,
    ): ChannelsChange {
        val restricted = chosen.isNotEmpty()
        val mark = if (GroupChoice.RESTRICTED in chosen) emptySet() else setOf(GroupChoice.RESTRICTED)
        if (show ?: targets.none { showing(it, chosen, hidden) }) {
            return ChannelsChange(
                removeHidden = targets.filterTo(HashSet()) { it in hidden },
                addPicks = if (restricted) targets.filterTo(HashSet()) { it !in chosen } + mark else emptySet(),
            )
        }
        if (restricted) return ChannelsChange(removePicks = targets.filterTo(HashSet()) { it in chosen }, addPicks = mark)
        // From "everything" to a pick list: keep what shows now, less the targets.
        val skip = targets.toSet()
        return ChannelsChange(addPicks = allGroups().filterTo(HashSet()) { it !in hidden && it !in skip } + GroupChoice.RESTRICTED)
    }

    /** An old single-language filter as picks: the groups it allowed. Picks it rejected are dropped. */
    fun convertFilter(groups: Collection<String>, filter: String, chosen: Set<String>): ChannelsChange {
        val mark = if (GroupChoice.RESTRICTED in chosen) emptySet() else setOf(GroupChoice.RESTRICTED)
        val rejected = chosen.filterTo(HashSet()) { it != GroupChoice.RESTRICTED && !LanguageTags.allows(it, filter) }
        // With no pick left, Home showed everything the filter allowed, so that is what stays.
        val allowed = if ((chosen - rejected - GroupChoice.RESTRICTED).isEmpty()) groups.filterTo(HashSet()) { LanguageTags.allows(it, filter) } else emptySet()
        return ChannelsChange(removePicks = rejected, addPicks = allowed + mark)
    }

    /**
     * Groups by tag: the TV's own country first (GB counts as UK), then US, UK and NZ as the guide does, then A to Z,
     * untagged groups ("Other") last. Each list of groups is in name order.
     */
    fun languages(counts: Map<String, Int>, tvCountry: String = ""): List<LanguageRow> {
        val own = tvCountry.uppercase().let { if (it == "GB") "UK" else it }
        val rank = listOf(own, "US", "UK", "NZ").filter { it.isNotEmpty() }.distinct().withIndex().associate { it.value to it.index }
        return counts.keys.sortedWith(String.CASE_INSENSITIVE_ORDER).groupBy { LanguageTags.tagOf(it) }
            .toSortedMap(compareBy<String?>({ it == null }, { it?.let(rank::get) ?: Int.MAX_VALUE }, { it }))
            .map { (tag, groups) -> LanguageRow(tag ?: "Other", groups, groups.sumOf { counts[it] ?: 0 }, tag?.let(LanguageTags::nameOf) ?: "Other") }
    }
}
