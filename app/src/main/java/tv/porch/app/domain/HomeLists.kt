package tv.porch.app.domain

/**
 * The four lists on Home. Each is a [Section] of [LiveRows.Row]s, the same rows the Live now screen draws, so one row
 * composable serves both. Times are guide-shifted. A row with no end time (0) is a channel the guide knows nothing about.
 */
object HomeLists {
    const val ROWS = 4
    const val RECENT_CAP = 8

    /**
     * [all] is the whole list (it is also the play order); [shown] is what fits on Home.
     * [more] is how many rows "See all" can show that Home does not. It counts only rows the full list would also have.
     */
    data class Section(val all: List<LiveRows.Row>, val more: Int = (all.size - ROWS).coerceAtLeast(0)) {
        val shown: List<LiveRows.Row> get() = all.take(ROWS)
    }

    data class Lists(val sport: Section, val favourites: Section, val recent: Section, val soon: Section, val yours: Section, val following: Section = Section(emptyList())) {
        /** Following replaces Starting soon only when it has something. */
        val thirdIsFollowing: Boolean get() = following.all.isNotEmpty()
        val third: Section get() = if (thirdIsFollowing) following else soon
    }

    fun build(
        channels: List<Channel>,
        onNow: List<Programme>,
        soon: List<Programme>,
        offsets: Map<Long, Int>,
        now: Long,
        families: ChannelFamilies = ChannelFamilies.None,
        following: List<Pair<Channel, Programme>> = emptyList(),
    ): Lists {
        // Each list folds what it could show, so a family's best copy overall can't hide the one that belongs here.
        // The lists that run on the guide fold only the matched copies: the best copy overall may have no guide id, and would take its family with it.
        val favourites = favouritesFolded(channels, onNow, offsets, families)
        val recent = recentFolded(channels, onNow, offsets, families)
        return Lists(
            sport = sport(ChannelFold.foldGuided(NowShelves.narrow(channels, NowChip.OfKind(Kind.Sport)), families), onNow, offsets, now),
            favourites = favourites,
            recent = recent,
            soon = startingSoon(ChannelFold.foldGuided(channels, families), soon, offsets, now),
            yours = yourChannels(favourites, recent, families),
            following = following(following.map { Airing(it.first, it.second) }, offsets, now),
        )
    }

    /** What is on now, then what is coming up, for what the viewer follows: one row per airing, no filler, by start. */
    fun following(airings: List<Airing>, offsets: Map<Long, Int>, now: Long): Section {
        val kept = airings
            .filterNot { LiveRows.isFiller(it.programme.title, it.channel) }
            .filter { GuideClock.shift(it.programme.stopUtc, offsets[it.channel.sourceId] ?: 0) > now }
        val rows = LiveRows.fold(kept, offsets).sortedWith(compareBy<LiveRows.Row> { if (it.startMs <= now) 0 else 1 }.thenBy { it.startMs })
            // One row per title: the airing on now, else the next.
            .distinctBy { it.title.trim().lowercase() }
        // No See all: it would open a list that knows nothing about follows.
        return Section(rows, more = 0)
    }

    /** The end of a row on now; otherwise the start as a time, "Tomorrow 7:00 am", or "Sat 9:30 pm". */
    fun followingTrailing(row: LiveRows.Row, now: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
        if (row.startMs <= now) return "ends ${formatTime(row.endMs, zone)}"
        val days = java.time.Instant.ofEpochMilli(row.startMs).atZone(zone).toLocalDate().toEpochDay() -
            java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()
        val time = formatTime(row.startMs, zone)
        return when {
            days <= 0L -> time
            days == 1L -> "Tomorrow $time"
            else -> "${formatGuideDay(row.startMs, zone)} $time"
        }
    }

    /** Home's "Your channels" on its own, for Search to show before anything is typed. The same steps [build] takes for it. */
    fun yoursOnNow(
        channels: List<Channel>,
        onNow: List<Programme>,
        offsets: Map<Long, Int>,
        families: ChannelFamilies = ChannelFamilies.None,
    ): Section = yourChannels(favouritesFolded(channels, onNow, offsets, families), recentFolded(channels, onNow, offsets, families), families)

    // The two lists "Your channels" is made of, shared by [build] and [yoursOnNow] so they can't drift.
    private fun favouritesFolded(channels: List<Channel>, onNow: List<Programme>, offsets: Map<Long, Int>, families: ChannelFamilies): Section =
        favouritesOnNow(ChannelFold.fold(channels.filter { it.favourite }, families), onNow, offsets)

    private fun recentFolded(channels: List<Channel>, onNow: List<Programme>, offsets: Map<Long, Int>, families: ChannelFamilies): Section =
        recent(ChannelFold.fold(channels.filter { it.lastPlayedAt != null }, families), onNow, offsets)

    /** Matches on now across the sport channels, folded, favourites first. */
    fun sport(
        channels: List<Channel>,
        onNow: List<Programme>,
        offsets: Map<Long, Int>,
        now: Long,
        kindOf: (Channel) -> Kind? = Kinds::of,
    ): Section {
        val live = NowShelves.build(channels, onNow, emptyList(), emptyList(), NowChip.OfKind(Kind.Sport), offsets, now, kindOf).live
        val matches = SportMatch.screen(live, emptyList(), offsets, kindOf).live.rows.filter { it.match }
        return Section(matches.sortedBy { if (it.channels.first().favourite) 0 else 1 })
    }

    /** Every favourite channel, in channel order, with what is on it. A favourite the guide knows nothing about still gets a row. */
    fun favouritesOnNow(channels: List<Channel>, onNow: List<Programme>, offsets: Map<Long, Int>): Section {
        val index = latestByChannel(onNow)
        val rows = channels.filter { it.favourite }.sortedBy { it.number }.map { rowFor(it, index, offsets) }
        // See all is What's on now on the Favourites chip, which lists only channels with a real programme on.
        return Section(rows, more = rows.drop(ROWS).count { it.endMs > 0L })
    }

    /** Favourites (in [favouriteTiles] order), then what was played recently, each channel once: copies of one channel count as one. */
    fun yourChannels(favourites: Section, recent: Section, families: ChannelFamilies = ChannelFamilies.None): Section {
        val rows = (favouriteTiles(favourites) + recent.all).distinctBy { row ->
            val id = row.channels.first().id
            families.copiesOf(id).firstOrNull()?.id ?: id
        }
        // See all is What's on now on the Favourites chip, which lists only favourites with a real programme on.
        return Section(rows, more = rows.drop(ROWS).count { it.channels.first().favourite && it.endMs > 0L })
    }

    /** Every favourite, those with a programme on now first, each group in channel order. */
    fun favouriteTiles(favourites: Section): List<LiveRows.Row> = favourites.all.sortedBy { if (it.endMs > 0L) 0 else 1 }

    /** Channels played before, newest first, with what is on them now. */
    fun recent(channels: List<Channel>, onNow: List<Programme>, offsets: Map<Long, Int>): Section {
        val index = latestByChannel(onNow)
        val played = channels.filter { it.lastPlayedAt != null }.sortedByDescending { it.lastPlayedAt }.take(RECENT_CAP)
        return Section(played.map { rowFor(it, index, offsets) })
    }

    /** The next [SOON_WINDOW_MS], one programme per channel, no filler, favourites first and then by start. */
    fun startingSoon(channels: List<Channel>, soon: List<Programme>, offsets: Map<Long, Int>, now: Long): Section {
        val airings = NowShelves.upcoming(channels, soon, offsets, now)
            .filter { GuideClock.shift(it.programme.startUtc, offsets[it.channel.sourceId] ?: 0) <= now + SOON_WINDOW_MS }
            .filterNot { LiveRows.isFiller(it.programme.title, it.channel) }
            .distinctBy { it.channel.id }
        val rows = LiveRows.fold(airings, offsets)
        return Section(rows.sortedBy { if (it.channels.first().favourite) 0 else 1 })
    }

    private fun latestByChannel(programmes: List<Programme>): Map<Pair<Long, String>, Programme> =
        programmes.groupBy { it.sourceId to it.channelKey }.mapValues { (_, list) -> list.maxBy { it.startUtc } }

    /** The programme on the channel, or the channel's name with no times when there is none (or only filler). */
    private fun rowFor(channel: Channel, index: Map<Pair<Long, String>, Programme>, offsets: Map<Long, Int>): LiveRows.Row {
        val programme = channel.matchedXmltvId?.let { index[channel.sourceId to it] }
            ?.takeUnless { LiveRows.isFiller(it.title, channel) }
            ?: return LiveRows.Row(channel.shownTitle, 0L, 0L, listOf(channel))
        val offset = offsets[channel.sourceId] ?: 0
        return LiveRows.Row(
            title = programme.title.trim(),
            startMs = GuideClock.shift(programme.startUtc, offset),
            endMs = GuideClock.shift(programme.stopUtc, offset),
            channels = listOf(channel),
            programmeId = programme.id,
        )
    }
}
