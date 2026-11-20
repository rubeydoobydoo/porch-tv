package tv.porch.app.domain

import java.time.Instant
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Sport tab now reads the guide once, off the main thread, and drops ended programmes each minute. Its links must be
 * the same as the code that froze the TV gave. [OldLinks] is that code, kept here only to compare against.
 */
class SportCandidatesTest {
    private val now = Instant.parse("2026-11-16T00:00:00Z").toEpochMilli()
    private val minute = 60_000L

    /** SportChannels.links as it was on main at 2d6f207, before the 16 Nov fix. Slow; do not use outside this test. */
    private object OldLinks {
        private val shared = setOf("united", "city", "town", "rovers", "athletic", "albion", "county", "club", "real", "state")
        private val separator = Regex("""\s+(?:v|vs|vs\.|@|[-–—])\s+""", RegexOption.IGNORE_CASE)
        private val wordSplit = Regex("[^\\p{L}\\p{N}]+")

        private fun hits(team: String, half: String): Boolean {
            if (FollowTerms.matches(team, half)) return true
            return team.split(wordSplit).filter { it.length >= 4 && it.lowercase() !in shared }.any { FollowTerms.matches(it, half) }
        }

        private fun namesBoth(home: String, away: String, guideTitle: String): Boolean {
            val halves = guideTitle.split(separator, limit = 2)
            if (halves.size != 2) return false
            val (a, b) = halves
            return (hits(home, a) && hits(away, b)) || (hits(home, b) && hits(away, a))
        }

        fun links(fixtures: List<SportFixture>, airings: List<Airing>, offsets: Map<Long, Int>, now: Long, kindOf: (Channel) -> Kind?): Map<String, SportLink> {
            class Slot(val airing: Airing, val start: Long, val stop: Long)
            val slots = airings.asSequence()
                .filter { kindOf(it.channel) == Kind.Sport }
                .map {
                    val offset = offsets[it.channel.sourceId] ?: 0
                    Slot(it, GuideClock.shift(it.programme.startUtc, offset), GuideClock.shift(it.programme.stopUtc, offset))
                }
                .filter { it.stop > now }
                .sortedBy { it.start }
                .toList()
            val starts = slots.map { it.start }
            val found = HashMap<String, SportLink>()
            for (fixture in fixtures) {
                val (home, away) = SportChannels.sides(fixture.title) ?: continue
                var at = starts.binarySearch(fixture.startMs - SportChannels.WINDOW_MS).let { if (it < 0) -it - 1 else it }
                while (at > 0 && starts[at - 1] >= fixture.startMs - SportChannels.WINDOW_MS) at--
                val matching = ArrayList<Slot>()
                while (at < slots.size && slots[at].start <= fixture.startMs + SportChannels.WINDOW_MS) {
                    if (namesBoth(home, away, slots[at].airing.programme.title)) matching += slots[at]
                    at++
                }
                if (matching.isEmpty()) continue
                val ordered = matching.distinctBy { it.airing.channel.id }.sortedWith(compareBy({ it.airing.channel.number }, { it.airing.channel.id }))
                found[SportRules.keyOf(fixture)] = SportLink(ordered.map { it.airing.channel }, ordered.first().start, ordered.first().stop)
            }
            return found
        }
    }

    private fun channel(id: Long, number: Int, source: Long = 1L, name: String = "Fake Sport $id") = Channel(
        id = id, sourceId = source, name = name, number = number, group = "Sport", favourite = false,
        streamUrl = "http://example.com/live/$id.ts", xtreamStreamId = null, archiveDays = 0,
        matchedXmltvId = "key-$id", lastPlayedAt = null,
    )

    private var nextId = 0L

    private fun airing(channel: Channel, title: String, startMs: Long, lengthMin: Long) = Airing(
        channel,
        Programme(nextId++, channel.sourceId, "key-${channel.id}", title, null, startMs, startMs + lengthMin * minute, null),
    )

    @Test
    fun links_match_the_old_code_on_many_made_up_guides_as_programmes_end() {
        val places = listOf("Harbour", "Bay", "Dune", "Ridge", "Cove", "Mesa", "Delta", "North")
        val nicknames = listOf("Hawks", "Rovers", "Lions", "United", "City", "Comets", "Falcons")
        val teams = places.flatMap { p -> nicknames.map { "$p $it" } }
        // Ways a guide writes a game: plain, prefixed, swapped, other separators, close spellings, apostrophes, one team only.
        val styles: List<(String, String) -> String> = listOf(
            { a, b -> "$a v $b" },
            { a, b -> "Fake League: $a vs $b" },
            { a, b -> "$b v $a" },
            { a, b -> "$a @ $b" },
            { a, b -> "$a - $b (Live)" },
            { a, b -> "$a – $b" },
            { a, b -> "${a.dropLast(1)} VS. $b" },
            { a, b -> "${a.replace("Harbour", "Harbor")} v $b" },
            { a, b -> "${a.replace("Hawks", "Hawk's")} v $b" },
            { a, _ -> "$a Live" },
            { a, b -> "$a\tv\t$b" },
            { _, _ -> "Sports Tonight" },
        )
        val sportOnly: (Channel) -> Kind? = { if (it.name.startsWith("Fake Sport")) Kind.Sport else Kind.News }
        var compared = 0
        var linked = 0
        for (seed in 1..40) {
            val random = Random(seed)
            val channels = (1L..12L).map { id ->
                // Shared channel numbers, a second source with its own offset, and a news channel that never links.
                if (id == 12L) channel(id, 3, name = "Fake News") else channel(id, random.nextInt(1, 6), source = 1L + id % 2)
            }
            val fixtures = (0 until 15).map { i ->
                val pair = teams.shuffled(random).take(2)
                // Some ids repeat, so two fixtures share a key.
                SportFixture("g${random.nextInt(10)}", "${pair[0]} v ${pair[1]}", "Fake League", "Football", now + random.nextLong(-120, 600) * minute)
            } + SportFixture("ufc", "Fake Fight Night", "UFC", "UFC", now)
            val airings = ArrayList<Airing>()
            for (fixture in fixtures) {
                val (a, b) = SportChannels.sides(fixture.title) ?: continue
                repeat(random.nextInt(0, 5)) {
                    val title = styles.random(random)(a, b)
                    airings += airing(channels.random(random), title, fixture.startMs + random.nextLong(-40, 40) * minute, random.nextLong(20, 180))
                }
            }
            repeat(40) {
                val pair = teams.shuffled(random).take(2)
                airings += airing(channels.random(random), styles.random(random)(pair[0], pair[1]), now + random.nextLong(-200, 700) * minute, random.nextLong(30, 120))
            }
            airings.shuffle(random)
            val offsets = mapOf(1L to 0, 2L to random.nextInt(-90, 90))
            val once = SportChannels.candidates(fixtures, airings, offsets, sportOnly)
            for (at in listOf(-150L, -30L, 0L, 45L, 200L, 500L, 900L).map { now + it * minute }) {
                val old = OldLinks.links(fixtures, airings, offsets, at, sportOnly)
                assertEquals("seed $seed at ${(at - now) / minute} min", old, once.at(at))
                assertEquals("seed $seed at ${(at - now) / minute} min", old, SportChannels.links(fixtures, airings, offsets, at, sportOnly))
                compared++
                linked += old.size
            }
        }
        // The guides really link games, so the comparison is not of two empty maps.
        assertEquals(280, compared)
        assertTrue("only $linked links", linked > 500)
    }

    @Test
    fun the_quick_separator_check_lets_every_kind_of_space_through() {
        // Android's regex counts Unicode spaces as \s, so the shortcut must never reject a title the regex would split.
        val spaces = (0..0xFFFF).map { it.toChar() }.filter { Character.isWhitespace(it) || Character.isSpaceChar(it) || it.isWhitespace() }
        assertTrue(spaces.size > 20)
        for (space in spaces) {
            for (mark in listOf("v", "V", "vs", "@", "-", "–", "—")) {
                assertTrue("U+%04X then $mark".format(space.code), SportChannels.mayHaveSeparator("Harbour Hawks${space}$mark${space}Bay Rovers"))
            }
        }
        assertTrue(!SportChannels.mayHaveSeparator("Live Evening Review"))
    }
}
