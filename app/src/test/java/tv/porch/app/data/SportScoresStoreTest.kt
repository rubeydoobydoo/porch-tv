package tv.porch.app.data

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SportScoresStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val start = Instant.parse("2026-11-16T12:00:00Z").toEpochMilli()
    private fun res(name: String) = javaClass.classLoader!!.getResourceAsStream(name)!!.readBytes()

    private class Fake(val answers: MutableMap<String, ByteArray>) : HttpFetcher {
        val asked = mutableListOf<String>()
        var failIf: (String) -> Boolean = { false }
        override fun open(url: String): InputStream {
            asked += url
            if (failIf(url)) throw HttpStatusException(500)
            val body = answers.entries.firstOrNull { url.contains(it.key) }?.value ?: throw HttpStatusException(500)
            return ByteArrayInputStream(body)
        }
    }

    private fun resultsFake() = Fake(mutableMapOf(
        "mma/ufc" to res("espn-results-ufc.json"),
        "racing/f1" to res("espn-results-f1.json"),
        "golf/" to res("espn-results-golf.json"),
        "tennis/" to res("espn-results-tennis.json"),
        "football/nfl" to res("espn-results-team-day.json"),
        "australian-football" to res("espn-list-ended.json"),
        "rugby-league" to res("espn-list-ended.json"),
        "soccer/" to res("espn-list-ended.json"),
        "basketball/nba" to res("espn-list-ended.json"),
    ))

    private fun laddersFake() = Fake(mutableMapOf(
        "tennis/atp/rankings" to res("espn-rankings-tennis.json"),
        "tennis/wta/rankings" to res("espn-rankings-tennis.json"),
        "racing/f1/standings" to res("espn-standings-f1.json"),
        "basketball/nba/standings" to res("espn-standings-conferences.json"),
        "standings" to res("espn-standings-table.json"),
    ))

    private fun scores(fake: Fake, clock: () -> Long) =
        SportScores(fake, File(folder.root, "r.json"), File(folder.root, "l.json"), clock)

    @Test
    fun a_fresh_copy_asks_nothing() {
        val fake = resultsFake()
        val s = scores(fake) { start }
        val first = runBlocking { s.refreshResults() }
        assertTrue(first.results.isNotEmpty())
        fake.asked.clear()
        runBlocking { scores(fake) { start + 30 * 60_000L }.refreshResults() }
        assertTrue(fake.asked.isEmpty())
    }

    @Test
    fun a_failing_league_keeps_its_old_copy_and_is_named() {
        val fake = resultsFake()
        runBlocking { scores(fake) { start }.refreshResults() }
        fake.answers.remove("mma/ufc")
        val after = runBlocking { scores(fake) { start + 2 * 3_600_000L }.refreshResults() }
        assertEquals(listOf("UFC"), after.failed)
        assertTrue(after.results.any { it.chip == "UFC" })
    }

    @Test
    fun a_ladder_reply_with_no_tables_counts_as_failed_and_ufc_and_golf_are_never_asked() {
        val fake = laddersFake()
        fake.answers["basketball/nba/standings"] = "{}".toByteArray()
        val ladders = runBlocking { scores(fake) { start }.refreshLadders() }
        assertEquals(listOf("NBA"), ladders.failed)
        assertTrue(fake.asked.none { it.contains("mma/ufc") || it.contains("golf/") })
        assertTrue(fake.asked.none { it.contains("scoreboard") })
        assertTrue(fake.asked.all { it.contains("standings") || it.contains("rankings") })
    }

    @Test
    fun a_zero_table_reply_after_a_good_one_keeps_the_old_ladders_and_names_the_league() {
        val fake = laddersFake()
        val store = scores(fake) { start }
        runBlocking { store.refreshLadders() }
        fake.answers["basketball/nba/standings"] = "{}".toByteArray()
        val after = runBlocking { scores(fake) { start + 3_600_000L }.refreshLadders(force = true) }
        assertEquals(listOf("NBA"), after.failed)
        assertTrue(after.ladders.any { it.chip == "NBA" })
    }

    @Test
    fun a_failed_team_day_keeps_that_days_results_from_the_old_copy() {
        val fake = resultsFake()
        runBlocking { scores(fake) { start }.refreshResults() }
        // The plain reply now has no games; the 14 Nov day fails. The old copy's game from that day stays.
        fake.answers["football/nfl"] = """{"leagues":[{"calendarType":"day","calendar":["2026-11-14T08:00Z","2026-11-15T08:00Z"]}],"events":[]}""".toByteArray()
        fake.failIf = { it.contains("football/nfl") && it.contains("dates=20261114") }
        val after = runBlocking { scores(fake) { start + 3_600_000L }.refreshResults(force = true) }
        assertTrue(after.failed.isEmpty())
        assertTrue(after.results.any { it.chip == "NFL" && it.title == "North Town v South City" })
    }

    @Test
    fun a_saved_result_over_eight_days_old_is_dropped_on_save() {
        val fake = resultsFake()
        runBlocking { scores(fake) { start }.refreshResults() }
        val later = runBlocking { scores(fake) { start + 9 * 86_400_000L }.refreshResults(force = true) }
        assertTrue(later.results.none { it.chip == "UFC" })
        assertTrue(scores(fake) { start }.savedResults()!!.results.none { it.chip == "UFC" })
    }

    @Test
    fun a_failed_plain_reply_asks_every_day_and_fails_only_when_they_all_fail() {
        val fake = resultsFake()
        fake.failIf = { it.contains("football/nfl") && !it.contains("dates=") }
        val ok = runBlocking { scores(fake) { start }.refreshResults() }
        assertTrue(ok.failed.isEmpty())
        assertTrue(ok.results.any { it.chip == "NFL" })
        assertTrue(fake.asked.count { it.contains("football/nfl") && it.contains("dates=") } >= 8)

        val dead = resultsFake()
        dead.failIf = { it.contains("football/nfl") }
        val bad = runBlocking { scores(dead) { start }.refreshResults(force = true) }
        assertEquals(listOf("NFL"), bad.failed)
    }

    @Test
    fun results_only_ask_scoreboards() {
        val fake = resultsFake()
        runBlocking { scores(fake) { start }.refreshResults() }
        assertTrue(fake.asked.all { it.contains("scoreboard") })
    }

    @Test
    fun results_never_ask_for_standings() {
        val fake = resultsFake()
        runBlocking { scores(fake) { start }.refreshResults() }
        assertTrue(fake.asked.none { it.contains("standings") || it.contains("rankings") })
    }
}
