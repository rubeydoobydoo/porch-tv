package tv.porch.app.data

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SportScheduleTest {
    @get:Rule val folder = TemporaryFolder()
    private val start = Instant.parse("2026-11-16T00:00:00Z").toEpochMilli()
    private fun res(name: String) = javaClass.classLoader!!.getResourceAsStream(name)!!.readBytes()

    private class Fake(val answers: MutableMap<String, ByteArray>) : HttpFetcher {
        val asked = mutableListOf<String>()
        override fun open(url: String): InputStream {
            asked += url
            val body = answers.entries.firstOrNull { url.contains(it.key) }?.value ?: throw HttpStatusException(500)
            return ByteArrayInputStream(body)
        }
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    /** Every league answers: ranges with a small reply, team leagues with a finished season. */
    private fun everything() = Fake(mutableMapOf(
        "mma/ufc" to res("espn-ufc.json"),
        "racing/f1" to res("espn-f1.json"),
        "golf/" to res("espn-golf.json"),
        "tennis/" to res("espn-tennis.json"),
        "australian-football" to res("espn-list-ended.json"),
        "rugby-league" to res("espn-list-ended.json"),
        "football/nfl" to res("espn-list-ended.json"),
        "soccer/uefa" to res("espn-list-ended.json"),
        "soccer/eng.1" to res("espn-list-ended.json"),
        "soccer/aus.1" to res("espn-list-ended.json"),
        "basketball/nba" to res("espn-list-ended.json"),
    ))

    @Test
    fun a_fresh_copy_asks_nothing_and_a_stale_one_asks_again() {
        val file = File(folder.root, "sport_schedule.json")
        val fake = everything()
        var clock = start
        val schedule = SportSchedule(fake, file) { clock }
        runBlocking { schedule.refresh() }
        val firstRound = fake.asked.size
        assertTrue(firstRound > 0)
        clock = start + 30 * 60_000L
        runBlocking { schedule.refresh() }
        assertEquals(firstRound, fake.asked.size)
        clock = start + 61 * 60_000L
        runBlocking { schedule.refresh() }
        assertTrue(fake.asked.size > firstRound)
    }

    @Test
    fun range_leagues_ask_once_with_the_window_and_a_gzipped_reply_is_read() {
        val fake = everything()
        fake.answers["mma/ufc"] = gzip(res("espn-ufc.json"))
        val state = runBlocking { SportSchedule(fake, File(folder.root, "s.json")) { start }.refresh() }
        assertEquals(1, fake.asked.count { it.contains("mma/ufc") })
        assertTrue(fake.asked.any { it.endsWith("mma/ufc/scoreboard?dates=20261115-20261129") })
        assertTrue(state.fixtures.any { it.title == "Example Fight Night" })
    }

    @Test
    fun a_failing_league_keeps_its_old_fixtures_and_is_named() {
        val file = File(folder.root, "sport_schedule.json")
        val fake = everything()
        var clock = start
        val schedule = SportSchedule(fake, file) { clock }
        runBlocking { schedule.refresh() }
        fake.answers.remove("mma/ufc")
        fake.answers.remove("racing/f1")
        clock = start + 2 * 3_600_000L
        val state = runBlocking { schedule.refresh() }
        assertEquals(listOf("UFC", "F1"), state.failed)
        assertTrue(state.fixtures.any { it.title == "Example Fight Night" })
        assertTrue(state.fixtures.any { it.title == "Example GP · Race" })
        // A new schedule object reads the saved file.
        val again = SportSchedule(everything().also { it.answers.clear() }, file) { clock }
        assertTrue(again.saved()!!.fixtures.any { it.title == "Example Fight Night" })
    }

    @Test
    fun every_league_failing_with_no_copy_has_nothing_to_show() {
        val fake = Fake(mutableMapOf())
        val state = runBlocking { SportSchedule(fake, File(folder.root, "s.json")) { start }.refresh() }
        assertTrue(state.fixtures.isEmpty())
        assertTrue(state.allFailed)
        assertEquals(null, state.updatedAt)
    }

    @Test
    fun a_running_list_league_is_asked_day_by_day() {
        val fake = everything()
        fake.answers["football/nfl"] = res("espn-list-open.json")
        val state = runBlocking { SportSchedule(fake, File(folder.root, "s.json")) { start }.refresh() }
        val nfl = fake.asked.filter { it.contains("football/nfl") }
        assertEquals(16, nfl.size)
        assertTrue(nfl.any { it.endsWith("football/nfl/scoreboard?dates=20261115") })
        assertTrue(nfl.any { it.endsWith("football/nfl/scoreboard?dates=20261129") })
        assertEquals(1, state.fixtures.count { it.title == "North Town v South City" })
    }

    @Test
    fun every_league_failing_with_a_copy_shows_the_copy_and_its_age() {
        val file = File(folder.root, "sport_schedule.json")
        val fake = everything()
        var clock = start
        val schedule = SportSchedule(fake, file) { clock }
        runBlocking { schedule.refresh() }
        fake.answers.clear()
        clock = start + 3 * 3_600_000L
        val state = runBlocking { schedule.refresh() }
        assertTrue(state.allFailed)
        assertTrue(state.fixtures.any { it.title == "Example Fight Night" })
        assertEquals(
            "Couldn't refresh. Showing the schedule from 3 h ago.",
            tv.porch.app.domain.SportRules.refreshNote(state.failed, state.allFailed, state.updatedAt, clock),
        )
    }

    @Test
    fun each_league_is_published_and_saved_as_it_finishes() {
        val file = File(folder.root, "sport_schedule.json")
        // Only the first league (AFL) answers; every other one fails.
        val fake = Fake(mutableMapOf("australian-football" to res("espn-team-day.json")))
        val seen = mutableListOf<tv.porch.app.data.SportSchedule.State>()
        val final = runBlocking { SportSchedule(fake, file) { start }.refresh(onProgress = { seen += it }) }
        assertTrue(seen.first().fixtures.any { it.chip == "AFL" })
        assertTrue(seen.first().failed.isEmpty())
        assertTrue(seen.last().failed.isNotEmpty())
        assertTrue(final.fixtures.any { it.chip == "AFL" })
        assertTrue(SportSchedule(Fake(mutableMapOf()), file) { start }.saved()!!.fixtures.any { it.chip == "AFL" })
    }

    @Test
    fun one_failing_day_does_not_fail_the_league() {
        val fake = everything()
        fake.answers["football/nfl"] = res("espn-list-open.json")
        // Every day but the plain reply answers 500.
        val flaky = object : HttpFetcher {
            override fun open(url: String): InputStream =
                if (url.contains("football/nfl") && url.contains("dates=")) throw HttpStatusException(500) else fake.open(url)
        }
        val state = runBlocking { SportSchedule(flaky, File(folder.root, "s.json")) { start }.refresh() }
        assertTrue("NFL" !in state.failed)
        assertTrue(state.fixtures.any { it.title == "North Town v South City" })
    }

    @Test
    fun a_failed_day_keeps_the_old_copys_games_for_that_day_only() {
        val file = File(folder.root, "sport_schedule.json")
        val fake = everything()
        fake.answers["australian-football"] = res("espn-team-day.json")
        var clock = start
        runBlocking { SportSchedule(fake, file) { clock }.refresh() }
        // Next round: the plain reply lists two days with no games. 15 Nov loads (empty), 21 Nov fails.
        val empty = """{"leagues":[{"calendarType":"day","calendar":["2026-11-15T08:00Z","2026-11-21T08:00Z"]}],"events":[]}""".toByteArray()
        val flaky = object : HttpFetcher {
            override fun open(url: String): InputStream = when {
                url.contains("australian-football") && url.endsWith("dates=20261121") -> throw HttpStatusException(500)
                url.contains("australian-football") -> ByteArrayInputStream(empty)
                else -> fake.open(url)
            }
        }
        clock = start + 2 * 3_600_000L
        val state = runBlocking { SportSchedule(flaky, file) { clock }.refresh(force = true) }
        assertTrue("the failed day keeps its old game", state.fixtures.any { it.title == "North Town v South City" })
        assertTrue("a day that loaded empty drops its old game", state.fixtures.none { it.title == "Home Hawks v Live Lions" })
        assertTrue("AFL" !in state.failed)
    }

    @Test
    fun a_league_whose_every_day_fails_and_has_no_fixtures_counts_as_failed() {
        val fake = everything()
        fake.answers["football/nfl"] = res("espn-list-open-empty.json")
        val flaky = object : HttpFetcher {
            override fun open(url: String): InputStream =
                if (url.contains("football/nfl") && url.contains("dates=")) throw HttpStatusException(500) else fake.open(url)
        }
        val state = runBlocking { SportSchedule(flaky, File(folder.root, "s.json")) { start }.refresh() }
        assertEquals(listOf("NFL"), state.failed)
    }

    @Test
    fun a_corrupt_saved_file_reads_as_no_copy() {
        val file = File(folder.root, "sport_schedule.json")
        file.writeText("{not json")
        val schedule = SportSchedule(Fake(mutableMapOf()), file) { start }
        assertEquals(null, schedule.saved())
        val state = runBlocking { schedule.refresh() }
        assertTrue(state.fixtures.isEmpty())
        assertTrue(state.allFailed)
    }
}
