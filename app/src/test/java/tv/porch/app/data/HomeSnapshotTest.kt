package tv.porch.app.data

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tv.porch.app.domain.Channel
import tv.porch.app.domain.HomeLists
import tv.porch.app.domain.LiveRows

class HomeSnapshotTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun channel(id: Long, name: String, logo: String? = null) = Channel(
        id = id, sourceId = 1, name = name, number = id.toInt(), group = "News", favourite = false,
        streamUrl = "http://example.com/$id.ts", xtreamStreamId = null, archiveDays = 0, matchedXmltvId = null,
        lastPlayedAt = null, logoUrl = logo,
    )

    private fun row(id: Long, name: String, title: String, start: Long, end: Long, match: Boolean = false, logo: String? = null) =
        LiveRows.Row(title, start, end, listOf(channel(id, name, logo)), match = match, programmeId = id * 10)

    @Test
    fun savedHomeComesBackWithEndedRowsDroppedAndABrokenFileGivesNothing() {
        val file = File(folder.root, "home.json")
        val snapshot = HomeSnapshot(file, CoroutineScope(Dispatchers.Unconfined))
        val lists = HomeLists.Lists(
            sport = HomeLists.Section(listOf(row(1, "Harbour Sport", "Harbour FC v Bay Town", 1_000, 5_000, match = true, logo = "http://example.com/a.png"))),
            favourites = HomeLists.Section(emptyList()),
            recent = HomeLists.Section(emptyList()),
            soon = HomeLists.Section(listOf(
                row(3, "Harbour Two", "Started already", 2_000, 9_000),
                row(4, "Harbour Three", "Later show", 6_000, 9_000),
            )),
            yours = HomeLists.Section(listOf(
                row(5, "Harbour News", "Ended news", 500, 3_000),
                row(6, "Quiet Channel", "Quiet Channel", 0, 0),
            )),
        )

        snapshot.write(SavedHome.from(lists))
        val back = snapshot.read()
        assertNotNull(back)
        val alive = back!!.live(now = 4_000)!!

        val match = alive.sport.all.single()
        assertEquals("Harbour FC v Bay Town", match.title)
        assertEquals(1L, match.channels.first().id)
        assertEquals("Harbour Sport", match.channels.first().name)
        assertEquals("http://example.com/a.png", match.channels.first().logoUrl)
        assertEquals(true, match.match)
        assertEquals(listOf("Quiet Channel"), alive.yours.all.map { it.title })
        assertEquals(listOf("Later show"), alive.soon.all.map { it.title })

        file.writeText("{\"v\":1,\"sport\":{\"rows\":[")
        assertNull(snapshot.read())
        snapshot.delete()
        assertFalse(file.exists())
        assertNull(snapshot.read())
    }
}
