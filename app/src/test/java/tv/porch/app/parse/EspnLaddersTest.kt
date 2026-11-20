package tv.porch.app.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.porch.app.domain.SportRules

class EspnLaddersTest {
    private fun res(name: String) = javaClass.classLoader!!.getResourceAsStream(name)!!.readBytes().decodeToString()
    private fun league(path: String) = SportRules.leagues.first { it.path == path }

    @Test
    fun a_table_is_sorted_by_rank_with_points_and_played() {
        val ladder = EspnLadders.standings(res("espn-standings-table.json"), league("australian-football/afl")).single()
        assertNull(ladder.group)
        assertEquals(listOf("First City", "Second Rovers", "Third Town", "Fourth Park"), ladder.rows.map { it.name })
        assertEquals(listOf(1, 2, 3, 4), ladder.rows.map { it.position })
        assertEquals("20 pts · 8 played", ladder.rows[0].stats)
    }

    @Test
    fun conferences_become_groups_and_seed_is_the_position() {
        val ladders = EspnLadders.standings(res("espn-standings-conferences.json"), league("basketball/nba"))
        assertEquals(listOf("Eastern Conference", "Western Conference"), ladders.map { it.group })
        assertEquals(listOf("East One", "East Two"), ladders[0].rows.map { it.name })
        assertEquals("Record 8-1", ladders[0].rows[0].stats)
    }

    @Test
    fun f1_has_a_driver_table_and_a_constructor_table() {
        val ladders = EspnLadders.standings(res("espn-standings-f1.json"), league("racing/f1"))
        assertEquals(listOf("Driver Standings", "Constructor Standings"), ladders.map { it.group })
        assertEquals("120 pts", ladders[0].rows[0].stats)
    }

    @Test
    fun tennis_rankings_keep_the_top_twenty_with_whole_number_points() {
        val ladder = EspnLadders.rankings(res("espn-rankings-tennis.json"), league("tennis/atp")).single()
        assertEquals(20, ladder.rows.size)
        assertEquals("Player 01", ladder.rows[0].name)
        assertEquals("9900 pts", ladder.rows[0].stats)
        assertEquals(20, ladder.rows.last().position)
    }

    @Test
    fun unnamed_or_same_named_tables_and_a_repeated_team_still_give_unique_rows() {
        fun entry(name: String, seed: Int) =
            """{"team": {"displayName": "$name"}, "stats": [{"name": "playoffSeed", "value": $seed}]}"""
        val text = """{"children": [
            {"standings": {"entries": [${entry("North One", 1)}, ${entry("North One", 1)}, ${entry("North Two", 2)}]}},
            {"standings": {"entries": [${entry("South One", 1)}]}},
            {"name": "Same", "standings": {"entries": [${entry("Same One", 1)}]}},
            {"name": "Same", "standings": {"entries": [${entry("Same Two", 1)}]}}
        ]}"""
        val ladders = EspnLadders.standings(text, league("basketball/nba"))
        assertEquals(4, ladders.map { it.group }.distinct().size)
        assertEquals(listOf("North One", "North Two"), ladders[0].rows.map { it.name })

        val clash = """{"children": [
            {"name": "Table 2", "standings": {"entries": [${entry("East One", 1)}]}},
            {"standings": {"entries": [${entry("West One", 1)}]}}
        ]}"""
        assertEquals(2, EspnLadders.standings(clash, league("basketball/nba")).map { it.group }.distinct().size)
    }

    @Test(expected = Exception::class)
    fun a_standings_root_that_is_not_an_object_throws() {
        EspnLadders.standings("[]", league("basketball/nba"))
    }

    @Test(expected = Exception::class)
    fun a_rankings_root_that_is_not_an_object_throws() {
        EspnLadders.rankings("[]", league("tennis/atp"))
    }
}
