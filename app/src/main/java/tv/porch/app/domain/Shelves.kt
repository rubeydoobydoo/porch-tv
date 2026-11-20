package tv.porch.app.domain

object Shelves {
    fun home(
        channels: List<Channel>,
        onNow: List<Programme>,
        soon: List<Programme>,
        offsets: Map<Long, Int>,
    ): HomeShelves {
        val nowIndex = index(onNow)
        val soonIndex = index(soon)
        val airingNow = channels.mapNotNull { channel ->
            val programme = programmeFor(channel, nowIndex) ?: return@mapNotNull null
            Airing(channel, programme)
        }.sortedWith(compareByDescending<Airing> { it.channel.favourite }.thenBy { it.channel.number })

        val starting = channels.mapNotNull { channel ->
            val programme = programmeFor(channel, soonIndex) ?: return@mapNotNull null
            Airing(channel, programme)
        }.sortedBy { airing ->
            GuideClock.shift(airing.programme.startUtc, offsets[airing.channel.sourceId] ?: 0)
        }

        val favourites = channels.filter { it.favourite }.sortedBy { it.number }
        val recent = channels
            .filter { it.lastPlayedAt != null }
            .sortedByDescending { it.lastPlayedAt }
            .take(8)

        val categories = channels.groupBy { it.group }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, List<Channel>>> { it.value.size }.thenBy { it.key })
            .take(HOME_CATEGORY_CAP)
            .map { (group, members) ->
                GroupShelf(group, slots(members, nowIndex))
            }

        return HomeShelves(
            onNow = airingNow,
            soon = starting,
            favourites = favourites,
            recent = recent,
            categories = categories,
        )
    }

    fun groupShelves(
        channels: List<Channel>,
        programmes: List<Programme>,
    ): List<GroupShelf> {
        val indexed = index(programmes)
        return channels.groupBy { it.group }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, List<Channel>>> { it.value.size }.thenBy { it.key })
            .map { (group, members) -> GroupShelf(group, slots(members, indexed)) }
    }

    private fun slots(members: List<Channel>, programmes: Map<Pair<Long, String>, Programme>): List<AiringSlot> {
        val onNow = members.filter { programmeFor(it, programmes) != null }.mapTo(HashSet()) { it.id }
        return ChannelOrder.order(members, onNow)
            .map { channel -> AiringSlot(channel, programmeFor(channel, programmes)) }
    }

    private fun programmeFor(channel: Channel, programmes: Map<Pair<Long, String>, Programme>): Programme? {
        val key = channel.matchedXmltvId ?: return null
        return programmes[channel.sourceId to key]
    }

    private fun index(programmes: List<Programme>): Map<Pair<Long, String>, Programme> {
        return programmes.groupBy { it.sourceId to it.channelKey }
            .mapValues { (_, list) -> list.maxBy { it.startUtc } }
    }
}
