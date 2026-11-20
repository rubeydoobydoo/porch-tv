package tv.porch.app.domain

/** What the What's on now chips choose: everything, favourite channels, or one kind. */
sealed interface NowChip {
    data object All : NowChip
    data object Favourites : NowChip
    data class OfKind(val kind: Kind) : NowChip
}

/** The three What's on now shelves: what is live, what starts in the next hour, what starts tonight. */
object NowShelves {
    data class Lists(val live: List<Airing>, val hour: List<Airing>, val tonight: List<Airing>)

    /**
     * [channels] are already narrowed by My groups and guide coverage. The chip then narrows them for all three shelves.
     * Each shelf lists a channel once, with the programme that is on (live) or starts first (later shelves, and only if it has not started yet; [now] is that cut).
     * Live is ordered favourites, then by number. The later shelves run in time order ([offsets] are the guide
     * offsets by source, so the start is the shifted one), ties favourites first, then by number.
     */
    fun build(
        channels: List<Channel>,
        live: List<Programme>,
        hour: List<Programme>,
        tonight: List<Programme>,
        chip: NowChip,
        offsets: Map<Long, Int> = emptyMap(),
        now: Long = Long.MIN_VALUE,
        kindOf: (Channel) -> Kind? = Kinds::of,
    ): Lists {
        val listed = narrow(channels, chip, kindOf)
        return Lists(shelf(listed, live, latest = true, offsets, now), shelf(listed, hour, latest = false, offsets, now), shelf(listed, tonight, latest = false, offsets, now))
    }

    /**
     * Every programme that has not started yet, on each channel, in time order (ties favourites first, then by number).
     * The shelves keep one per channel; the Sport list wants all of them, because a channel's next show may not be its next match.
     */
    fun upcoming(channels: List<Channel>, programmes: List<Programme>, offsets: Map<Long, Int>, now: Long): List<Airing> {
        val byKey = programmes.filter { GuideClock.shift(it.startUtc, offsets[it.sourceId] ?: 0) >= now }.groupBy { it.sourceId to it.channelKey }
        return channels.flatMap { channel ->
            val key = channel.matchedXmltvId ?: return@flatMap emptyList()
            byKey[channel.sourceId to key].orEmpty().map { Airing(channel, it) }
        }.sortedWith(
            compareBy<Airing> { GuideClock.shift(it.programme.startUtc, offsets[it.channel.sourceId] ?: 0) }
                .thenBy { if (it.channel.favourite) 0 else 1 }
                .thenBy { it.channel.number },
        )
    }

    fun narrow(channels: List<Channel>, chip: NowChip, kindOf: (Channel) -> Kind? = Kinds::of): List<Channel> = when (chip) {
        NowChip.All -> channels
        NowChip.Favourites -> channels.filter { it.favourite }
        is NowChip.OfKind -> Kinds.filter(channels, chip.kind, kindOf)
    }

    private fun shelf(channels: List<Channel>, programmes: List<Programme>, latest: Boolean, offsets: Map<Long, Int>, now: Long): List<Airing> {
        // A later shelf lists what has not started yet (Tonight is only re-queried hourly).
        val usable = if (latest) programmes else programmes.filter { GuideClock.shift(it.startUtc, offsets[it.sourceId] ?: 0) >= now }
        val byChannel = usable.groupBy { it.sourceId to it.channelKey }
            .mapValues { (_, list) -> if (latest) list.maxBy { it.startUtc } else list.minBy { it.startUtc } }
        val airings = channels.mapNotNull { channel ->
            val key = channel.matchedXmltvId ?: return@mapNotNull null
            byChannel[channel.sourceId to key]?.let { Airing(channel, it) }
        }
        if (!latest) {
            return airings.sortedWith(
                compareBy<Airing> { GuideClock.shift(it.programme.startUtc, offsets[it.channel.sourceId] ?: 0) }
                    .thenBy { if (it.channel.favourite) 0 else 1 }
                    .thenBy { it.channel.number },
            )
        }
        val ordered = ChannelOrder.order(airings.map { it.channel }, onNowIds = airings.mapTo(HashSet()) { it.channel.id })
        val byId = airings.associateBy { it.channel.id }
        return ordered.mapNotNull { byId[it.id] }
    }
}
