package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgrammeCardFollowTest {
    private val now = 1_000_000_000L
    private val minute = 60_000L

    private fun onNow(follow: ProgrammeCard.FollowPill?) =
        ProgrammeCard.actions(now - 10 * minute, now + 50 * minute, now, 0, false, "Harbour One", follow = follow)

    @Test
    fun followPillComesAfterWatchAndIsTickedWhenFollowed() {
        val offered = onNow(ProgrammeCard.FollowPill(listOf("Matildas", "Matildas v England"), following = null))
        assertEquals(ProgrammeCard.Kind.WatchLive, offered.first().kind)
        val pill = offered.last()
        assertEquals(ProgrammeCard.Kind.Follow, pill.kind)
        assertEquals("Follow Matildas", pill.label)
        assertFalse(pill.chosen)
        val followed = onNow(ProgrammeCard.FollowPill(listOf("Matildas", "Matildas v England"), following = "Matildas")).last()
        assertEquals("Following Matildas", followed.label)
        assertTrue(followed.chosen)
        assertNull(onNow(null).firstOrNull { it.kind == ProgrammeCard.Kind.Follow })
        // The title sits right above the pill, so a whole-title follow needs no name, and a long name is cut short to fit the row.
        assertEquals("Follow", onNow(ProgrammeCard.FollowPill(listOf("Tennis Finals Tonight"), following = null)).last().label)
        assertEquals("Following", onNow(ProgrammeCard.FollowPill(listOf("Tennis Finals Tonight"), following = "Tennis Finals Tonight")).last().label)
        assertEquals("Follow Northern Harbour…", onNow(ProgrammeCard.FollowPill(listOf("Northern Harbour Rovers", "Northern Harbour Rovers v Westfield"), following = null)).last().label)
    }
}
