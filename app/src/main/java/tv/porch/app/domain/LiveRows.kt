package tv.porch.app.domain

/** What the Live now screen lists: one row per programme, with its channels folded in, and filler left out. */
object LiveRows {
    /** One programme at one start time, on one or more channels. Times are guide-shifted. */
    data class Row(
        val title: String,
        val startMs: Long,
        val endMs: Long,
        /** Favourite first, then lowest channel number. Never empty. */
        val channels: List<Channel>,
        /** Set by the Sport list: this is a match, not just another programme on a sport channel. */
        val match: Boolean = false,
        /** The programme on [channels]' first channel, to fetch its description and picture. 0 means there is none. */
        val programmeId: Long = 0L,
    ) {
        val key: String get() = "${normal(title)}@$startMs"
    }

    data class Live(val rows: List<Row>, val hidden: Int, val commonEnd: Long?)

    /** The three sections of the screen. */
    data class Screen(val live: Live, val hour: List<Row>, val tonight: List<Row>)

    /** Titles that say nothing, compared ignoring case and spacing. A title the same as its channel's name counts too. */
    val filler: Set<String> = setOf(
        "paid programming", "paid program", "teleshopping", "local programming", "programming", "program", "fin des programmes", "end of programmes", "shop", "to be announced", "tba", "no information", "no data", "off air", "infomercial",
    )

    fun isFiller(title: String, channel: Channel): Boolean {
        val t = normal(title)
        return t in filler || t == normal(channel.shownTitle)
    }

    fun build(lists: NowShelves.Lists, offsets: Map<Long, Int>): Screen = Screen(
        live = live(lists.live, offsets),
        hour = later(lists.hour, offsets),
        tonight = later(lists.tonight, offsets),
    )

    /**
     * [live] is what is on now, one airing per channel, in list order. Filler is dropped and counted. That also covers a
     * channel whose filler is about to give way to a real programme: Next hour shows the real one.
     */
    fun live(live: List<Airing>, offsets: Map<Long, Int>): Live {
        val kept = live.filterNot { isFiller(it.programme.title, it.channel) }
        val rows = fold(kept, offsets)
        return Live(rows, live.size - kept.size, commonEnd(rows))
    }

    /** Next hour and Tonight: the same folding and filler rule, in time order as given. */
    fun later(airings: List<Airing>, offsets: Map<Long, Int>): List<Row> =
        fold(airings.filterNot { isFiller(it.programme.title, it.channel) }, offsets)

    fun fold(airings: List<Airing>, offsets: Map<Long, Int>): List<Row> {
        val groups = LinkedHashMap<String, MutableList<Airing>>()
        for (airing in airings) {
            val start = GuideClock.shift(airing.programme.startUtc, offsets[airing.channel.sourceId] ?: 0)
            groups.getOrPut("${normal(airing.programme.title)}@$start") { ArrayList(2) }.add(airing)
        }
        return groups.values.map { group ->
            val channels = group.map { it.channel }.distinctBy { it.id }.sortedWith(compareBy<Channel> { if (it.favourite) 0 else 1 }.thenBy { it.number })
            val first = group.first { it.channel.id == channels.first().id }
            val offset = offsets[first.channel.sourceId] ?: 0
            Row(
                title = first.programme.title.trim(),
                startMs = GuideClock.shift(first.programme.startUtc, offset),
                endMs = GuideClock.shift(first.programme.stopUtc, offset),
                channels = channels,
                programmeId = first.programme.id,
            )
        }
    }

    /** The end time most rows share, to the minute. Ties go to the earliest. Null with no rows. */
    fun commonEnd(rows: List<Row>): Long? {
        val byMinute = rows.groupBy { it.endMs / 60_000L }
        val best = byMinute.entries.sortedWith(compareByDescending<Map.Entry<Long, List<Row>>> { it.value.size }.thenBy { it.key })
            .firstOrNull() ?: return null
        return best.value.minOf { it.endMs }
    }

    /** A row shows its own end time only when it is more than a few minutes from the common one. */
    fun endWorthShowing(endMs: Long, commonEnd: Long?): Boolean =
        commonEnd == null || kotlin.math.abs(endMs - commonEnd) > 5 * 60_000L

    private val livePrefix = Regex("""^\s*live\s*[:|-]\s*""", RegexOption.IGNORE_CASE)

    private val dateSuffix = Regex("""\s+-\s+(mon|tues?|wed(nes)?|thu(rs)?|fri|sat(ur)?|sun)(day)?\s+\d{1,2}\s+[a-z]+\s+\d{4}\s*$""", RegexOption.IGNORE_CASE)

    /** What a row shows. It already says LIVE and the day, so a guide's own "LIVE:" in front and date behind the title are noise. */
    fun shownTitle(title: String): String = title.replace(livePrefix, "").replace(dateSuffix, "")

    private val spaces = Regex("""\s+""")
    private fun normal(text: String): String = text.trim().replace(spaces, " ").lowercase(java.util.Locale.ROOT)
}
