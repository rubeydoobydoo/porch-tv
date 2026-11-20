package tv.porch.app.domain

/** The channels showing a game: [channels] by channel number, and the first one's start and stop (guide offset already added). */
data class SportLink(val channels: List<Channel>, val startMs: Long, val stopMs: Long)

/** What OK does on a Sport row. */
enum class SportOk { Play, Remind, Unremind, None }

/**
 * Finds the channel showing a game by reading the guide: a programme on a Sport channel that starts within 30 minutes
 * of the game and names both teams, one each side of its "v". Only "A v B" games are linked; UFC, F1, golf and tennis
 * titles are too loose to match safely. A miss shows no channel, so it never plays the wrong one.
 */
object SportChannels {
    const val WINDOW_MS = 30 * 60_000L

    /** Words many clubs share, so on their own they do not name a team. */
    private val shared = setOf("united", "city", "town", "rovers", "athletic", "albion", "county", "club", "real", "state")
    private val separator = Regex("""\s+(?:v|vs|vs\.|@|[-–—])\s+""", RegexOption.IGNORE_CASE)
    private val wordSplit = Regex("[^\\p{L}\\p{N}]+")

    /** The two team names of an "A v B" game, or null. */
    fun sides(title: String): Pair<String, String>? {
        val parts = title.split(" v ")
        if (parts.size != 2) return null
        val (home, away) = parts.map { it.trim() }
        return if (home.isEmpty() || away.isEmpty()) null else home to away
    }

    /** A team's words read once: the whole name, then each word of 4+ letters that clubs do not share. */
    private class Team(name: String) {
        val whole = FollowTerms.words(name)
        val parts = name.split(wordSplit).filter { it.length >= 4 && it.lowercase() !in shared }.map(FollowTerms::words)

        fun hits(half: List<String>) = FollowTerms.matchesWords(whole, half) || parts.any { FollowTerms.matchesWords(it, half) }
    }

    /** A guide title's two halves around its "v", read once, or null when it has no "v". */
    private class Halves(val a: List<String>, val b: List<String>) {
        fun nameBoth(home: Team, away: Team) = (home.hits(a) && away.hits(b)) || (home.hits(b) && away.hits(a))
    }

    /**
     * A whitespace, then "v", "@" or a dash: [separator] can only match where this is true. Much cheaper than the regex.
     * Android's \s also takes Unicode spaces (a non-breaking space, say) where the desktop JVM's does not, so both count.
     */
    internal fun mayHaveSeparator(title: String): Boolean {
        for (i in 1 until title.length) {
            val c = title[i]
            if ((c == 'v' || c == 'V' || c == '@' || c == '-' || c == '–' || c == '—') && isRegexSpace(title[i - 1])) return true
        }
        return false
    }

    private fun isRegexSpace(c: Char) = c in " \t\n\u000B\u000C\r" || Character.isSpaceChar(c) || Character.isWhitespace(c)

    private fun halves(guideTitle: String): Halves? {
        if (!mayHaveSeparator(guideTitle)) return null
        val halves = guideTitle.split(separator, limit = 2)
        if (halves.size != 2) return null
        return Halves(FollowTerms.words(halves[0]), FollowTerms.words(halves[1]))
    }

    /** True when [guideTitle] reads "X v Y" (or vs, @, dash) with one team in each half, in either order. */
    fun namesBoth(home: String, away: String, guideTitle: String): Boolean =
        halves(guideTitle)?.nameBoth(Team(home), Team(away)) ?: false

    /**
     * Every guide programme that could show each team game in [fixtures], before the clock is applied. This is the slow
     * part, so it runs off the main thread and again only when the schedule or the guide changes; [Candidates.at] then
     * drops programmes that have ended. [airings] are guide programmes; [offsets] are each source's guide offset in minutes.
     */
    fun candidates(
        fixtures: List<SportFixture>,
        airings: List<Airing>,
        offsets: Map<Long, Int>,
        kindOf: (Channel) -> Kind? = Kinds::of,
    ): Candidates {
        class Game(val fixture: SportFixture, val home: Team, val away: Team)
        val games = fixtures.mapNotNull { f -> sides(f.title)?.let { (h, a) -> Game(f, Team(h), Team(a)) } }
        if (games.isEmpty()) return Candidates(emptyMap())
        val gameStarts = games.map { it.fixture.startMs }.sorted().toLongArray()
        // A programme no game is near is never compared, so it is not kept or sorted. Most of a two-week guide goes here.
        fun nearAGame(start: Long): Boolean {
            var at = gameStarts.binarySearch(start - WINDOW_MS).let { if (it < 0) -it - 1 else it }
            while (at > 0 && gameStarts[at - 1] >= start - WINDOW_MS) at--
            return at < gameStarts.size && gameStarts[at] <= start + WINDOW_MS
        }
        // Each channel's kind is asked once, not once per programme.
        val isSport = java.util.IdentityHashMap<Channel, Boolean>()
        val slots = ArrayList<Slot>()
        for (airing in airings) {
            val offset = offsets[airing.channel.sourceId] ?: 0
            val start = GuideClock.shift(airing.programme.startUtc, offset)
            if (!nearAGame(start)) continue
            if (!isSport.getOrPut(airing.channel) { kindOf(airing.channel) == Kind.Sport }) continue
            slots += Slot(airing.channel, airing.programme.title, start, GuideClock.shift(airing.programme.stopUtc, offset))
        }
        slots.sortBy { it.start }
        val starts = slots.map { it.start }
        // A title is read once however many games sit near it.
        val read = HashMap<Slot, Halves?>()
        val found = HashMap<String, MutableList<List<Slot>>>()
        for (game in games) {
            val fixture = game.fixture
            val home = game.home
            val away = game.away
            var at = starts.binarySearch(fixture.startMs - WINDOW_MS).let { if (it < 0) -it - 1 else it }
            while (at > 0 && starts[at - 1] >= fixture.startMs - WINDOW_MS) at--
            val matching = ArrayList<Slot>()
            while (at < slots.size && slots[at].start <= fixture.startMs + WINDOW_MS) {
                val slot = slots[at]
                // getOrPut would not keep a null, so a title that does not split is remembered this way.
                val split = if (slot in read) read[slot] else halves(slot.title).also { read[slot] = it }
                if (split?.nameBoth(home, away) == true) matching += slot
                at++
            }
            if (matching.isNotEmpty()) found.getOrPut(SportRules.keyOf(fixture)) { ArrayList() } += matching
        }
        return Candidates(found)
    }

    /** One guide programme on a Sport channel, guide offset added. Compared by identity, so two equal rows stay two. */
    internal class Slot(val channel: Channel, val title: String, val start: Long, val stop: Long)

    /**
     * Each game's possible programmes in start order, by [SportRules.keyOf]. Cheap to turn into links at any minute. Two
     * fixtures with one key keep a list each, in fixture order; the last one with a programme still on wins.
     */
    class Candidates internal constructor(private val byGame: Map<String, List<List<Slot>>>) {
        /** The links at [now]: programmes that have ended are left out, and a game with none left has no link. */
        fun at(now: Long): Map<String, SportLink> {
            val found = HashMap<String, SportLink>()
            for ((key, lists) in byGame) {
                val live = lists.asReversed().asSequence().map { slots -> slots.filter { it.stop > now } }.firstOrNull { it.isNotEmpty() } ?: continue
                val ordered = live.distinctBy { it.channel.id }.sortedWith(compareBy({ it.channel.number }, { it.channel.id }))
                found[key] = SportLink(ordered.map { it.channel }, ordered.first().start, ordered.first().stop)
            }
            return found
        }
    }

    /**
     * Links for the team games in [fixtures], by [SportRules.keyOf]. [airings] are guide programmes; [offsets] are each
     * source's guide offset in minutes. Programmes that have already ended are ignored.
     */
    fun links(
        fixtures: List<SportFixture>,
        airings: List<Airing>,
        offsets: Map<Long, Int>,
        now: Long,
        kindOf: (Channel) -> Kind? = Kinds::of,
    ): Map<String, SportLink> = candidates(fixtures, airings, offsets, kindOf).at(now)
}
