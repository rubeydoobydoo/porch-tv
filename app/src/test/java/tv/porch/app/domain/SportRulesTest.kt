package tv.porch.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.porch.app.parse.EspnCalendar

class SportRulesTest {
    private val sydney = ZoneId.of("Australia/Sydney")
    // Monday 16 Nov 2026, 11:00 in Sydney; still Sunday 15 Nov in New York.
    private val now = Instant.parse("2026-11-16T00:00:00Z").toEpochMilli()
    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()
    private fun game(id: String, title: String, iso: String, live: Boolean = false) =
        SportFixture(id, title, "Premier League", "Football", at(iso), inPlay = live)
    private fun tournament(id: String, title: String, from: String, to: String) =
        SportFixture(id, title, "PGA Tour", "Golf", LocalDate.parse(from).atStartOfDay(SportRules.espnZone).toInstant().toEpochMilli(), from, to)

    @Test
    fun a_day_calendar_asks_only_days_inside_the_window() {
        val calendar = EspnCalendar("day", listOf("2026-11-14", "2026-11-15", "2026-11-21", "2026-11-29", "2026-11-30").map(LocalDate::parse), null)
        assertEquals(listOf("2026-11-15", "2026-11-21", "2026-11-29"), SportRules.daysToAsk(calendar, now).map { it.toString() })
    }

    @Test
    fun a_list_calendar_whose_season_ended_asks_nothing_and_a_running_one_asks_every_day() {
        val ended = EspnCalendar("list", emptyList(), LocalDate.parse("2026-09-01"))
        assertTrue(SportRules.daysToAsk(ended, now).isEmpty())
        val running = EspnCalendar("list", emptyList(), LocalDate.parse("2027-01-10"))
        val days = SportRules.daysToAsk(running, now)
        assertEquals(15, days.size)
        assertEquals("2026-11-15", days.first().toString())
        assertEquals("2026-11-29", days.last().toString())
    }

    @Test
    fun a_range_league_asks_once_with_the_range_and_a_team_league_by_day() {
        val ufc = SportRules.leagues.first { it.chip == "UFC" }
        assertEquals(
            "https://site.api.espn.com/apis/site/v2/sports/mma/ufc/scoreboard?dates=20261115-20261129",
            SportRules.rangeUrl(ufc, now),
        )
        val nba = SportRules.leagues.first { it.chip == "NBA" }
        assertEquals(
            "https://site.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard?dates=20261121",
            SportRules.dayUrl(nba, LocalDate.parse("2026-11-21")),
        )
    }

    @Test
    fun days_are_headed_today_tomorrow_then_the_date_and_rows_read_in_the_tv_zone() {
        val live = game("a", "Live Lions v Home Hawks", "2026-11-15T23:00:00Z", live = true)
        val today = game("b", "North Town v South City", "2026-11-16T08:30:00Z")
        val tomorrow = game("c", "East Rovers v West Wanderers", "2026-11-17T08:30:00Z")
        val later = game("d", "Late Town v Last City", "2026-11-21T08:30:00Z")
        val days = SportRules.days(listOf(later, tomorrow, today, live), now, sydney)
        assertEquals(listOf("Today", "Tomorrow", "Sat 21 Nov"), days.map { it.heading })
        assertEquals(listOf("Live Lions v Home Hawks", "North Town v South City"), days[0].rows.map { it.title })
        assertEquals("7:30 pm", days[0].rows[1].time)
        assertEquals("Premier League", days[0].rows[1].league)
        assertTrue(days[0].rows[0].live)
        assertEquals(false, days[0].rows[1].live)
    }

    @Test
    fun a_multi_day_event_shows_its_dates_then_on_now_while_it_runs() {
        val coming = tournament("g", "Sample Classic", "2026-11-19", "2026-11-22")
        val running = tournament("h", "Example Open", "2026-11-12", "2026-11-18")
        val crossing = tournament("i", "Other Cup", "2026-11-29", "2026-12-06")
        val days = SportRules.days(listOf(coming, running, crossing), now, sydney)
        assertEquals(listOf("Today", "Thu 19 Nov", "Sun 29 Nov"), days.map { it.heading })
        assertEquals("On now", days[0].rows.single().time)
        assertEquals("Thu 19 to Sun 22 Nov", days[1].rows.single().time)
        assertEquals("Sun 29 Nov to Sun 6 Dec", days[2].rows.single().time)
    }

    @Test
    fun the_window_drops_finished_far_off_and_ended_fixtures() {
        val past = game("p", "Old Town v Old City", "2026-11-14T08:30:00Z")
        val far = game("f", "Far Town v Far City", "2026-12-20T08:30:00Z")
        val ended = tournament("e", "Done Open", "2026-11-05", "2026-11-08")
        assertTrue(SportRules.days(listOf(past, far, ended), now, sydney).isEmpty())
    }

    @Test
    fun an_empty_chip_says_so() {
        assertEquals("No AFL games in the next 14 days.", SportRules.emptyText("AFL"))
        assertEquals("No games in the next 14 days.", SportRules.emptyText("All"))
        assertEquals("No Golf events in the next 14 days.", SportRules.emptyText("Golf"))
    }

    @Test
    fun the_refresh_note_names_the_failed_chips_or_the_age() {
        val threeHours = now - 3 * 3_600_000L
        assertEquals("Couldn't refresh NFL and Golf.", SportRules.refreshNote(listOf("NFL", "Golf"), false, threeHours, now))
        assertEquals("Couldn't refresh AFL, NFL and Golf.", SportRules.refreshNote(listOf("AFL", "NFL", "Golf"), false, threeHours, now))
        assertEquals("Couldn't refresh. Showing the schedule from 3 h ago.", SportRules.refreshNote(listOf("NFL"), true, threeHours, now))
        assertEquals("Couldn't refresh. Showing the schedule from 10 min ago.", SportRules.refreshNote(listOf("NFL"), true, now - 600_000L, now))
        assertEquals("Couldn't refresh. Showing the schedule from 3 days ago.", SportRules.refreshNote(listOf("NFL"), true, now - 3 * 86_400_000L, now))
        assertNull(SportRules.refreshNote(emptyList(), false, threeHours, now))
    }

    @Test
    fun a_fixture_still_shows_for_three_hours_after_its_start_even_if_not_marked_live() {
        val justStarted = game("j", "Just Town v Just City", "2026-11-15T23:30:00Z")
        val longGone = game("k", "Gone Town v Gone City", "2026-11-15T19:30:00Z")
        val rows = SportRules.days(listOf(justStarted, longGone), now, sydney).flatMap { it.rows }
        assertEquals(listOf("Just Town v Just City"), rows.map { it.title })
        assertEquals(false, rows.single().live)
        assertEquals("10:30 am", rows.single().time)
    }

    @Test
    fun a_live_game_that_started_before_today_groups_under_today() {
        // Started 11 pm Sunday in Sydney, still on at 2 am Monday.
        val overnight = SportFixture("o", "Night Town v Night City", "Premier League", "Football", at("2026-11-15T12:00:00Z"), inPlay = true)
        val days = SportRules.days(listOf(overnight), at("2026-11-15T15:00:00Z"), sydney)
        assertEquals(listOf("Today"), days.map { it.heading })
    }
}
