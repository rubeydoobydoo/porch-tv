package tv.porch.app.parse

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.porch.app.domain.SportRules

class EspnResultsTest {
    private val now = Instant.parse("2026-11-16T12:00:00Z").toEpochMilli()
    private fun res(name: String) = javaClass.classLoader!!.getResourceAsStream(name)!!.readBytes().decodeToString()
    private fun league(path: String) = SportRules.leagues.first { it.path == path }

    @Test
    fun a_team_result_has_a_title_and_a_hidden_score_and_unplayed_or_scoreless_games_give_nothing() {
        val parsed = EspnResults.parse(res("espn-results-team-day.json"), league("football/nfl"), now)
        assertEquals(1, parsed.results.size)
        val r = parsed.results[0]
        assertEquals("North Town v South City", r.title)
        assertEquals("North Town 3, South City 1", r.reveal)
        assertEquals("NFL", r.chip)
        assertEquals("day", parsed.calendar.type)
    }

    @Test
    fun a_ufc_result_is_the_main_event_winner() {
        val r = EspnResults.parse(res("espn-results-ufc.json"), league("mma/ufc"), now).results.single()
        assertEquals("Example Fight Night", r.title)
        assertEquals("Main event: Alex Example beat Sam Sample", r.reveal)
    }

    @Test
    fun an_f1_result_is_the_race_winner_and_ignores_qualifying() {
        val r = EspnResults.parse(res("espn-results-f1.json"), league("racing/f1"), now).results.single()
        assertEquals("Race winner: Pat Driver", r.reveal)
        assertEquals(Instant.parse("2026-11-15T06:00:00Z").toEpochMilli(), r.playedMs)
    }

    @Test
    fun a_golf_result_waits_for_the_tournament_to_finish() {
        val results = EspnResults.parse(res("espn-results-golf.json"), league("golf/pga"), now).results
        assertEquals(1, results.size)
        assertEquals("Winner: Robin Putter (-21)", results[0].reveal)
    }

    @Test
    fun a_golf_tournament_marked_finished_on_its_last_day_waits_until_that_day_is_over() {
        // 16 Nov 12:00 UTC is 16 Nov in US Eastern, the same ESPN day as the end date.
        val text = """{"events": [{"id": "5003", "name": "Example Invitational", "date": "2026-11-13T08:00Z", "endDate": "2026-11-16T08:00Z",
            "status": {"type": {"state": "post"}},
            "competitions": [{"competitors": [{"order": 1, "score": "-9", "athlete": {"displayName": "Early Putter"}}]}]}]}"""
        assertTrue(EspnResults.parse(text, league("golf/pga"), now).results.isEmpty())
        val nextDay = Instant.parse("2026-11-17T12:00:00Z").toEpochMilli()
        assertEquals("Winner: Early Putter (-9)", EspnResults.parse(text, league("golf/pga"), nextDay).results.single().reveal)
    }

    @Test
    fun a_tennis_result_has_the_singles_finals_only() {
        val r = EspnResults.parse(res("espn-results-tennis.json"), league("tennis/atp"), now).results.single()
        assertEquals("Men's singles: Champ Man. Women's singles: Champ Woman", r.reveal)
        assertTrue("Doubles Pair" !in r.reveal)
    }

    @Test(expected = Exception::class)
    fun a_root_that_is_not_an_object_throws() {
        EspnResults.parse("[]", league("mma/ufc"), now)
    }

    @Test(expected = Exception::class)
    fun unreadable_json_throws() {
        EspnResults.parse("not json", league("mma/ufc"), now)
    }
}
