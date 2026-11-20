package tv.porch.app.domain

/** The "Your channels" row shown while the guide has nothing on air yet. [all] is the full order, for Up/Down in the player. */
data class ChannelShelf(val all: List<Channel>) {
    val shown: List<Channel> get() = all.take(SHELF_CAP)
    val total: Int get() = all.size
    val extra: Int get() = (all.size - SHELF_CAP).coerceAtLeast(0)
}

object HomeRows {
    /** Favourites first, then the rest, each in channel-number order. */
    fun yourChannels(channels: List<Channel>): ChannelShelf {
        val (favourites, rest) = channels.partition { it.favourite }
        return ChannelShelf(favourites.sortedBy { it.number } + rest.sortedBy { it.number })
    }
}
