package tv.porch.app.domain

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SportChannelsTest {
    private val now = Instant.parse("2026-11-16T00:00:00Z").toEpochMilli()
    private val minute = 60_000L
    private val kickoff = now + 120 * minute
    private val zone = ZoneId.of("Australia/Sydney")

    private fun game(id: String, title: String, startMs: Long = kickoff, league: String = "Premier League") =
        SportFixture(id, title, league, "Football", startMs)

    private fun channel(id: Long, number: Int, name: String) = Channel(
        id = id, sourceId = 1L, name = name, number = number, group = "General", favourite = false,
        streamUrl = "http://example.com/live/$id.ts", xtreamStreamId = null, archiveDays = 0,
        matchedXmltvId = "key-$id", lastPlayedAt = null,
    )

    private val sport1 = channel(1, 1, "Harbour Sport 1")
    private val sport2 = channel(2, 2, "Harbour Sport 2")
    private val movies = channel(3, 3, "Harbour Movies")

    private fun airing(channel: Channel, title: String, startMs: Long = kickoff, lengthMin: Long = 120) = Airing(
        channel,
        Programme(channel.id * 1000 + startMs / minute % 1000, 1L, "key-${channel.id}", title, null, startMs, startMs + lengthMin * minute, null),
    )

    private fun links(fixtures: List<SportFixture>, airings: List<Airing>, offsets: Map<Long, Int> = emptyMap()) =
        SportChannels.links(fixtures, airings, offsets, now)

    @Test
    fun a_game_links_to_the_sport_channel_that_names_both_teams_near_its_start() {
        val fixture = game("1", "Harbour Hawks v Bay Rovers")
        val found = links(
            listOf(fixture),
            listOf(
                airing(sport2, "Premier League: Harbour Hawks v Bay Rovers", kickoff + 20 * minute),
                airing(sport1, "Harbour Hawks v Bay Rovers", kickoff - 25 * minute),
            ),
        )[SportRules.keyOf(fixture)]
        assertNotNull(found)
        // Both channels, by channel number; the first one's times are the link's.
        assertEquals(listOf(1L, 2L), found!!.channels.map { it.id })
        assertEquals(kickoff - 25 * minute, found.startMs)
    }

    @Test
    fun a_game_does_not_link_on_one_team_the_wrong_channel_the_wrong_time_or_a_finished_programme() {
        val fixture = game("1", "Harbour Hawks v Bay Rovers")
        fun linked(vararg airings: Airing) = links(listOf(fixture), airings.toList()).isNotEmpty()
        assertFalse("one team only", linked(airing(sport1, "Harbour Hawks v Dune Lions")))
        assertFalse("not a sport channel", linked(airing(movies, "Harbour Hawks v Bay Rovers")))
        assertFalse("31 minutes late", linked(airing(sport1, "Harbour Hawks v Bay Rovers", kickoff + 31 * minute)))
        assertFalse("both teams, no versus", linked(airing(sport1, "Harbour Hawks Bay Rovers preview")))
        assertFalse("both teams in one half", linked(airing(sport1, "Harbour Hawks and Bay Rovers v Dune Lions")))
        assertFalse("already over", links(listOf(game("2", "Harbour Hawks v Bay Rovers", now - 200 * minute)), listOf(airing(sport1, "Harbour Hawks v Bay Rovers", now - 200 * minute, 90))).isNotEmpty())
        assertTrue("the same title does link", linked(airing(sport1, "Harbour Hawks v Bay Rovers")))
    }

    @Test
    fun the_guides_order_a_distinctive_word_and_the_guide_offset_are_all_read() {
        val fixture = game("1", "Harbour Hawks United v Bay Rovers")
        // Guide order is swapped, a team is spelled short, and the guide runs 60 minutes behind real time.
        val behind = airing(sport1, "Bay Rovers v Hawks", kickoff - 60 * minute)
        assertTrue(links(listOf(fixture), listOf(behind), mapOf(1L to 60)).isNotEmpty())
        // Without the offset the programme is an hour early: no link.
        assertTrue(links(listOf(fixture), listOf(behind)).isEmpty())
        // A word every club shares ("United") alone is not a team.
        assertTrue(links(listOf(fixture), listOf(airing(sport1, "Dune United v Bay Rovers"))).isEmpty())
    }

    @Test
    fun a_followed_name_or_league_marks_the_game_and_it_comes_first_on_its_day() {
        val early = game("1", "Alpha Owls v Beta Foxes", kickoff)
        val late = game("2", "Harbour Hawks v Bay Rovers", kickoff + 60 * minute)
        val order = { follows: List<String> ->
            SportRules.days(listOf(early, late), now, zone, follows).flatMap { it.rows }.map { it.title to it.followed }
        }
        assertEquals(listOf("Alpha Owls v Beta Foxes" to false, "Harbour Hawks v Bay Rovers" to false), order(emptyList()))
        assertEquals(listOf("Harbour Hawks v Bay Rovers" to true, "Alpha Owls v Beta Foxes" to false), order(listOf("bay rovers")))
        // A followed league marks every game in it.
        assertEquals(listOf(true, true), order(listOf("Premier League")).map { it.second })
        // Part of a word is not a follow.
        assertNull(order(listOf("Bay Rov")).firstOrNull { it.second })
    }

    @Test
    fun ok_plays_a_game_on_or_about_to_start_reminds_a_later_one_and_does_nothing_without_a_channel() {
        val link = SportLink(listOf(sport1), kickoff, kickoff + 120 * minute)
        assertEquals(SportOk.None, SportRules.okFor(null, now, false))
        assertEquals(SportOk.Remind, SportRules.okFor(link, now, false))
        assertEquals(SportOk.Unremind, SportRules.okFor(link, now, true))
        // Within the reminder lead time (5 minutes) it plays, and so does a game already on.
        assertEquals(SportOk.Play, SportRules.okFor(link, kickoff - 5 * minute, false))
        assertEquals(SportOk.Play, SportRules.okFor(link, kickoff + 30 * minute, true))
        assertEquals("OK plays", SportRules.okHint(SportOk.Play))
        assertEquals("OK sets a reminder", SportRules.okHint(SportOk.Remind))
        assertEquals("OK removes the reminder", SportRules.okHint(SportOk.Unremind))
        assertEquals("No channel in your guide for this game.", SportRules.okHint(SportOk.None))
    }
}
