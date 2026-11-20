package tv.porch.app.domain

import java.time.Instant
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A big provider's guide (hundreds of Sport channels, two weeks of programmes) must link quickly. On 16 Nov the Sport tab
 * froze the living room TV for over 5 seconds working this out. Names are made up; nothing here is a real channel.
 */
class SportChannelsSpeedTest {
    val now = Instant.parse("2026-11-16T00:00:00Z").toEpochMilli()
    private val hour = 3_600_000L

    private val places = listOf(
        "Harbour", "Bay", "Dune", "Ridge", "Cove", "Mesa", "Delta", "Summit", "Glen", "Marsh",
        "Crest", "Haven", "Brook", "Cliff", "Moor", "Vale", "Port", "Fern", "Ash", "Pine",
    )
    private val nicknames = listOf(
        "Hawks", "Rovers", "Lions", "Comets", "Sharks", "Falcons", "Wolves", "Giants", "Pirates", "Rangers",
        "Storm", "Thunder", "Owls", "Bears", "Jets",
    )
    private val teams = places.flatMap { place -> nicknames.map { "$place $it" } }

    private fun channel(id: Long) = Channel(
        id = id, sourceId = 1L, name = "Fake Sport $id", number = id.toInt(), group = "Sport", favourite = false,
        streamUrl = "http://example.com/live/$id.ts", xtreamStreamId = null, archiveDays = 0,
        matchedXmltvId = "key-$id", lastPlayedAt = null,
    )

    /** [channelCount] Sport channels with one-hour programmes from 3 hours ago to 15 days on, and 300 games each shown on 3 of them. */
    fun guide(channelCount: Long): Pair<List<SportFixture>, List<Airing>> {
        val random = Random(7)
        val channels = (1L..channelCount).map(::channel)
        // 300 games, one every hour on the hour across 12 days.
        val fixtures = (0 until 300).map { i ->
            val pair = teams.shuffled(random).take(2)
            SportFixture("g$i", "${pair[0]} v ${pair[1]}", "Fake League", "Football", now + (i + 2) * hour)
        }
        val byHour = fixtures.associateBy { (it.startMs - now) / hour }
        val airings = ArrayList<Airing>()
        var id = 0L
        for (channel in channels) {
            for (slot in -3L until 15 * 24L) {
                val start = now + slot * hour
                val game = byHour[slot]?.takeIf { channel.id <= 3 }
                val title = when {
                    game != null -> "Fake League: ${game.title}"
                    slot % 5 == 0L -> "Sports Tonight"
                    // Other games, with names that never fit a fixture, so each game links to exactly its 3 channels.
                    else -> "Replay: Squad ${random.nextInt(1000)} vs Squad ${random.nextInt(1000)}"
                }
                airings += Airing(channel, Programme(id++, 1L, "key-${channel.id}", title, null, start, start + hour, null))
            }
        }
        return fixtures to airings
    }

    @Test
    fun a_big_guide_links_in_well_under_a_second() {
        // 600 channels make about 218,000 airings.
        val (fixtures, airings) = guide(600)
        // Best of three, after a warm-up, so the timing is the steady cost and not class loading or a stray GC.
        SportChannels.links(fixtures.take(5), airings, emptyMap(), now)
        var found = emptyMap<String, SportLink>()
        val tookMs = (1..3).minOf {
            val started = System.nanoTime()
            found = SportChannels.links(fixtures, airings, emptyMap(), now)
            (System.nanoTime() - started) / 1_000_000
        }
        println("SportChannels.links: ${airings.size} airings, ${fixtures.size} games, best of 3 $tookMs ms")

        assertEquals(fixtures.size, found.size)
        assertTrue(found.values.all { it.channels.size == 3 })
        // A guard only: a desktop JVM is many times faster than the Chromecast (before the fix, about 0.5 s here).
        assertTrue("took $tookMs ms", tookMs < 1_000)
    }
}
