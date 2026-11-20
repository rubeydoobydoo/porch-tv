package tv.porch.app.parse

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.porch.app.domain.SportFixture
import tv.porch.app.domain.SportRules

class EspnScoreboardTest {
    private fun res(name: String) = javaClass.classLoader!!.getResourceAsStream(name)!!.readBytes().decodeToString()
    private fun league(chip: String, label: String) = SportRules.leagues.first { it.chip == chip && it.label == label }

    @Test
    fun team_game_reads_home_v_away_and_a_finished_one_is_dropped() {
        val parsed = EspnScoreboard.parse(res("espn-team-day.json"), league("NBA", "NBA"))
        val ids = parsed.fixtures.map { it.id }
        assertEquals(listOf("1001", "1003"), ids)
        val game = parsed.fixtures.first { it.id == "1001" }
        assertEquals("North Town v South City", game.title)
        assertEquals(Instant.parse("2026-11-21T08:30:00Z").toEpochMilli(), game.startMs)
        assertFalse(game.inPlay)
        assertTrue(parsed.fixtures.first { it.id == "1003" }.inPlay)
    }

    @Test
    fun a_fixture_has_no_score_anywhere() {
        val parsed = EspnScoreboard.parse(res("espn-team-day.json"), league("NBA", "NBA"))
        assertTrue(SportFixture::class.java.declaredFields.none { it.name.contains("score", true) || it.name.contains("winner", true) })
        parsed.fixtures.forEach { fixture ->
            val text = fixture.toString().lowercase()
            assertFalse(text, "score" in text || "winner" in text || "linescore" in text)
        }
    }

    @Test
    fun f1_keeps_qualifying_and_race_and_drops_practice() {
        val parsed = EspnScoreboard.parse(res("espn-f1.json"), league("F1", "Formula 1"))
        assertEquals(listOf("Example GP · Qualifying", "Example GP · Race"), parsed.fixtures.map { it.title })
        assertEquals(Instant.parse("2026-11-21T13:00:00Z").toEpochMilli(), parsed.fixtures[0].startMs)
    }

    @Test
    fun golf_and_tennis_become_one_multi_day_fixture_in_new_york_dates() {
        val golf = EspnScoreboard.parse(res("espn-golf.json"), league("Golf", "PGA Tour")).fixtures.single()
        assertEquals("Sample Classic", golf.title)
        assertEquals("2026-11-19", golf.startDay)
        assertEquals("2026-11-22", golf.endDay)
        val tennis = EspnScoreboard.parse(res("espn-tennis.json"), league("Tennis", "ATP")).fixtures.single()
        assertEquals("2026-11-23", tennis.startDay)
        // 04:59Z on the 30th is still the 29th in New York.
        assertEquals("2026-11-29", tennis.endDay)
    }

    @Test
    fun a_tournament_marked_finished_still_shows_by_its_dates() {
        // ESPN marked a running tennis event "post" (16 Nov 2026); its end date decides instead.
        val reply = """{"events":[{"id":"4002","name":"Sample Finals","date":"2026-11-15T05:00Z",
            "endDate":"2026-11-23T04:59Z","status":{"type":{"state":"post"}}}]}"""
        val tennis = EspnScoreboard.parse(reply, league("Tennis", "ATP")).fixtures.single()
        assertEquals("2026-11-15", tennis.startDay)
        assertEquals("2026-11-22", tennis.endDay)
    }

    @Test
    fun ufc_is_one_fixture_per_event() {
        val parsed = EspnScoreboard.parse(res("espn-ufc.json"), league("UFC", "UFC"))
        assertEquals(listOf("Example Fight Night"), parsed.fixtures.map { it.title })
        assertEquals(Instant.parse("2026-11-22T00:00:00Z").toEpochMilli(), parsed.fixtures.single().startMs)
    }

    @Test
    fun the_calendar_is_read_for_day_and_list_leagues() {
        val day = EspnScoreboard.parse(res("espn-team-day.json"), league("NBA", "NBA")).calendar
        assertEquals("day", day.type)
        assertEquals(5, day.days.size)
        val ended = EspnScoreboard.parse(res("espn-list-ended.json"), league("NFL", "NFL")).calendar
        assertEquals("list", ended.type)
        assertEquals("2026-09-01", ended.lastEnd.toString())
    }

    @Test
    fun no_score_value_from_any_sport_reaches_a_fixture() {
        val cases = listOf(
            Triple("espn-team-day.json", league("NBA", "NBA"), listOf("9876", "5432", "8765", "4321", "7654", "3210")),
            Triple("espn-f1.json", league("F1", "Formula 1"), listOf("6543")),
            Triple("espn-golf.json", league("Golf", "PGA Tour"), listOf("8642")),
            Triple("espn-tennis.json", league("Tennis", "ATP"), listOf("1357")),
            Triple("espn-ufc.json", league("UFC", "UFC"), listOf("2468")),
        )
        for ((file, sport, values) in cases) {
            assertTrue(file, EspnScoreboard.parse(res(file), sport).fixtures.isNotEmpty())
            val text = EspnScoreboard.parse(res(file), sport).fixtures.joinToString { it.toString() + it.title }
            values.forEach { assertFalse("$file leaked $it", it in text) }
        }
    }
}
