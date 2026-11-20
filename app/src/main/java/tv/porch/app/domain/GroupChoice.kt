package tv.porch.app.domain

/** One group on one source. Group names repeat across sources, so the source is part of the key. */
data class GroupKey(val sourceId: Long, val group: String)

/**
 * "My groups": the groups the viewer picked. The choice is per source: a source with nothing picked
 * shows everything. Favourites and channels you have played always show.
 */
object GroupChoice {
    /**
     * A reserved group name saved with a source's picks. With it, the source is restricted: only picked groups
     * show, even when no pick is left (all unpicked, or all gone after a refresh). No provider group contains
     * U+0001, so no channel is ever in this group.
     */
    const val RESTRICTED = "\u0001restricted"

    /** Whether the channel's own group passes the choice (its source has no picks, or the group is picked). */
    fun inChosenGroup(channel: Channel, chosen: Set<GroupKey>): Boolean =
        chosen.none { it.sourceId == channel.sourceId } || GroupKey(channel.sourceId, channel.group) in chosen

    fun filter(channels: List<Channel>, chosen: Set<GroupKey>): List<Channel> {
        if (chosen.isEmpty()) return channels
        val pickedSources = chosen.mapTo(HashSet()) { it.sourceId }
        return channels.filter {
            it.favourite ||
                it.lastPlayedAt != null ||
                it.sourceId !in pickedSources ||
                GroupKey(it.sourceId, it.group) in chosen
        }
    }

    /** The picks whose group still exists. A group that was renamed or removed on a refresh is ignored. The [RESTRICTED] row stays. */
    fun live(chosen: Set<GroupKey>, channels: List<Channel>): Set<GroupKey> {
        if (chosen.isEmpty()) return chosen
        val present = channels.mapTo(HashSet()) { GroupKey(it.sourceId, it.group) }
        return chosen.filterTo(HashSet()) { it.group == RESTRICTED || it in present }
    }
}
