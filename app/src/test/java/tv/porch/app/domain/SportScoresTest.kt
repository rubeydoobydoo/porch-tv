package tv.porch.app.domain

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.porch.app.parse.EspnCalendar

class SportScoresTest {
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-11-16T12:00:00Z").toEpochMilli()
    private fun at(s: String) = Instant.parse(s).toEpochMilli()
    private fun result(id: String, title: String, played: String, reveal: String = "SECRET $id", league: String = "NFL") =
        SportResult(id, title, league, "NFL", at(played), reveal)

    private val results = listOf(
        result("1", "Alpha v Beta", "2026-11-16T05:00:00Z"),
        result("2", "Gamma v Delta", "2026-11-15T05:00:00Z"),
        result("3", "Old v Older", "2026-11-05T05:00:00Z"),
        result("4", "Later v Latest", "2026-11-14T05:00:00Z"),
    )

    @Test
    fun results_are_last_seven_days_newest_day_first_with_today_and_yesterday() {
        val sections = SportScoreRules.resultSections(results, now, zone, emptyList(), emptySet())
        assertEquals(listOf("Today", "Yesterday"), sections.take(2).map { it.heading })
        assertEquals(3, sections.size)
        assertEquals(listOf("1"), sections[0].rows.map { it.key.substringAfterLast(':') })
        assertTrue(sections.flatMap { it.rows }.none { it.title.startsWith("Old") })
    }

    @Test
    fun hidden_rows_never_carry_the_spoiler_and_revealing_one_shows_only_that_row() {
        val secret = SportScoreRules.resultSections(results, now, zone, emptyList(), emptySet()).flatMap { it.rows }
        assertTrue(secret.all { it.shown == "Hidden" && !it.title.contains("SECRET") && !it.meta.contains("SECRET") })

        val key = SportScoreRules.resultKey(results[0])
        val shown = SportScoreRules.resultSections(results, now, zone, emptyList(), setOf(key)).flatMap { it.rows }
        assertEquals(listOf("SECRET 1"), shown.filter { it.revealed }.map { it.shown })
        assertTrue(shown.filter { !it.revealed }.all { it.shown == "Hidden" })
    }

    @Test
    fun ladder_rows_are_always_shown_in_position_order_and_followed_teams_do_not_move() {
        val ladder = Ladder("NFL", "NFL", "East", listOf(LadderRow("Cats", 3, "9 pts"), LadderRow("apples", 1, "12 pts"), LadderRow("Bears", 2, "")))
        val section = SportScoreRules.ladderSections(listOf(ladder), listOf("Cats")).single()
        assertEquals("NFL · East", section.heading)
        assertEquals(listOf("apples", "Bears", "Cats"), section.rows.map { it.title })
        assertEquals(listOf(1, 2, 3), section.rows.map { it.position })
        assertEquals(listOf("12 pts", "", "9 pts"), section.rows.map { it.shown })
        assertEquals(listOf(false, false, true), section.rows.map { it.followed })
        assertTrue(section.rows.all { it.revealed })
    }

    @Test
    fun ordinals_read_right() {
        val expected = mapOf(1 to "1st", 2 to "2nd", 3 to "3rd", 4 to "4th", 11 to "11th", 12 to "12th", 13 to "13th", 21 to "21st", 22 to "22nd", 101 to "101st", 111 to "111th")
        expected.forEach { (n, word) -> assertEquals(word, SportScoreRules.ordinal(n)) }
    }

    @Test
    fun ladder_sources_and_words() {
        val atp = SportRules.leagues.first { it.chip == "Tennis" }
        val ufc = SportRules.leagues.first { it.chip == "UFC" }
        val nfl = SportRules.leagues.first { it.chip == "NFL" }
        assertEquals(LadderSource.Rankings, SportScoreRules.ladderSource(atp))
        assertEquals(LadderSource.None, SportScoreRules.ladderSource(ufc))
        assertEquals(LadderSource.Standings, SportScoreRules.ladderSource(nfl))
        assertEquals("https://site.api.espn.com/apis/v2/sports/football/nfl/standings", SportScoreRules.standingsUrl(nfl))
        assertEquals("No ladder for UFC.", SportScoreRules.emptyLadders("UFC"))
        assertEquals("No NFL results in the last 7 days.", SportScoreRules.emptyResults("NFL"))
        assertEquals("Results from ESPN · updated 10 min ago", SportScoreRules.updatedText("Results", now - 600_000L, now))
    }

    @Test
    fun the_range_url_and_past_days_cover_the_last_seven_days() {
        val ufc = SportRules.leagues.first { it.chip == "UFC" }
        assertTrue(SportScoreRules.resultRangeUrl(ufc, now).endsWith("?dates=20261109-20261116"))
        val calendar = EspnCalendar("day", listOf(java.time.LocalDate.parse("2026-11-01"), java.time.LocalDate.parse("2026-11-14"), java.time.LocalDate.parse("2026-11-21")), null)
        assertEquals(listOf(java.time.LocalDate.parse("2026-11-14")), SportScoreRules.pastDaysToAsk(calendar, now))
        assertFalse(SportScoreRules.pastDaysToAsk(EspnCalendar("list", emptyList(), java.time.LocalDate.parse("2026-10-01")), now).isNotEmpty())
    }

    @Test
    fun a_followed_result_comes_first_on_its_day() {
        val two = listOf(
            result("a", "Alpha v Beta", "2026-11-16T09:00:00Z"),
            result("b", "Gamma v Delta", "2026-11-16T05:00:00Z"),
        )
        val rows = SportScoreRules.resultSections(two, now, zone, listOf("Gamma"), emptySet()).single().rows
        assertEquals(listOf("Gamma v Delta", "Alpha v Beta"), rows.map { it.title })
        assertTrue(rows[0].followed)
    }

    @Test
    fun the_same_team_in_two_groups_has_two_keys_and_both_show_their_own_stats() {
        val east = Ladder("NBA", "NBA", "East", listOf(LadderRow("Same Name", 1, "EASTSTATS")))
        val west = Ladder("NBA", "NBA", "West", listOf(LadderRow("Same Name", 2, "WESTSTATS")))
        val eastKey = SportScoreRules.ladderKey(east, east.rows[0])
        assertFalse(eastKey == SportScoreRules.ladderKey(west, west.rows[0]))
        val rows = SportScoreRules.ladderSections(listOf(east, west), emptyList()).flatMap { it.rows }
        assertEquals(listOf("EASTSTATS", "WESTSTATS"), rows.map { it.shown })
    }

    @Test
    fun the_refresh_note_names_the_failed_leagues_or_says_how_old_the_copy_is() {
        val threeHours = now - 3 * 3_600_000L
        assertEquals(null, SportScoreRules.refreshNote("results", emptyList(), false, threeHours, now))
        assertEquals("Couldn't refresh NFL.", SportScoreRules.refreshNote("results", listOf("NFL"), false, threeHours, now))
        assertEquals("Couldn't refresh AFL, NFL and Golf.", SportScoreRules.refreshNote("ladders", listOf("AFL", "NFL", "Golf"), false, threeHours, now))
        assertEquals("Couldn't refresh. Showing the ladders from 3 h ago.", SportScoreRules.refreshNote("ladders", listOf("NFL"), true, threeHours, now))
        assertEquals("Couldn't refresh NFL.", SportScoreRules.refreshNote("results", listOf("NFL"), true, null, now))
    }
}
