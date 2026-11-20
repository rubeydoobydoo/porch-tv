package tv.porch.app.domain

/** One order for every list: favourites, then channels with something on now, then the rest. */
object ChannelOrder {
    /** Within each band by provider number, ties by title. [onNowIds] are channel ids. Stable. */
    fun order(channels: List<Channel>, onNowIds: Set<Long>): List<Channel> =
        channels.sortedWith(
            compareBy<Channel> { if (it.favourite) 0 else if (it.id in onNowIds) 1 else 2 }
                .thenBy { it.number }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.shownTitle },
        )
}
