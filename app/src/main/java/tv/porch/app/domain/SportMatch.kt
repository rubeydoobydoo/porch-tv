package tv.porch.app.domain

import java.util.Locale

/**
 * Finds the matches among a guide's programmes. The guide has no categories, so this reads the channel (is it Sport, by
 * [Kinds]) and the title (an "X v Y", or a known league at the start). Word lists are plain sets, easy to read and change.
 */
object SportMatch {
    /** A title starting with one of these, on a sport channel, is a match. Lower case. */
    val leagues: Set<String> = setOf(
        "ufc", "nfl", "nba", "nhl", "mlb", "epl", "premier league", "champions league", "la liga", "serie a", "bundesliga",
        "afl", "nrl", "a-league", "super rugby", "six nations", "formula 1", "f1", "motogp", "nascar", "wimbledon", "us open",
        "australian open", "roland garros", "the ashes", "big bash", "ipl", "pga", "boxing",
    )

    /** A title with any of these as whole words or phrases is never a match (a replay, a studio show). */
    val excluded: Set<String> = setOf(
        "replay", "highlights", "classic", "preview", "review", "talk", "news", "magazine", "best of", "rewind", "classics",
    )

    // "X v Y", "X vs Y", "X @ Y": a whole-word separator with text on both sides.
    private val versus = Regex("""\S\s+(?:vs?\.?|@)\s+\S""", RegexOption.IGNORE_CASE)
    // "Team - Team": one to four words each side, the way a fixture is written.
    private val dashed = Regex("""^\S+(?:\s\S+){0,3}\s+[-–]\s+\S+(?:\s\S+){0,3}$""")
    private val spaces = Regex("""\s+""")
    private val excludedPattern = Regex(
        excluded.joinToString("|", prefix = """(?<![\p{L}\p{N}])(?:""", postfix = """)(?![\p{L}\p{N}])""") { phrase -> phrase.split(' ').joinToString("""\s+""") { Regex.escape(it) } },
        RegexOption.IGNORE_CASE,
    )

    fun isMatch(title: String, channel: Channel, kindOf: (Channel) -> Kind? = Kinds::of): Boolean {
        if (excludedPattern.containsMatchIn(title)) return false
        if (kindOf(channel) != Kind.Sport) return false
        return versus.containsMatchIn(title) || dashed.containsMatchIn(title.trim()) || startsWithLeague(title)
    }

    private fun startsWithLeague(title: String): Boolean {
        val t = title.trim().replace(spaces, " ").lowercase(Locale.ROOT)
        return leagues.any { league ->
            t.startsWith(league) && (t.length == league.length || !t[league.length].isLetterOrDigit())
        }
    }

    /**
     * The Sport list. [live] is what is on now across the sport channels; matches come first, the rest keep today's order.
     * [upcoming] is every programme starting in the next three hours; only matches are listed. Both fold like Live now.
     */
    fun screen(
        live: List<Airing>,
        upcoming: List<Airing>,
        offsets: Map<Long, Int>,
        kindOf: (Channel) -> Kind? = Kinds::of,
    ): LiveRows.Screen {
        val now = LiveRows.live(live, offsets)
        val rows = mark(now.rows, kindOf)
        val ordered = rows.filter { it.match } + rows.filterNot { it.match }
        val next = mark(LiveRows.later(upcoming.filter { isMatch(it.programme.title, it.channel, kindOf) }, offsets), kindOf)
        return LiveRows.Screen(now.copy(rows = ordered), next, emptyList())
    }

    private fun mark(rows: List<LiveRows.Row>, kindOf: (Channel) -> Kind?) =
        rows.map { row -> row.copy(match = row.channels.any { isMatch(row.title, it, kindOf) }) }
}
