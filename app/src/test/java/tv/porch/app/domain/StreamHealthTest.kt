package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.porch.app.domain.StreamHealth.Drop
import tv.porch.app.domain.StreamHealth.State

class StreamHealthTest {
    @Test
    fun aDropIsAnErrorACleanEndOnLiveOrALongStall() {
        val cases = listOf(
            // state, error arrived, live, buffering for (ms) -> expected
            Row(State.READY, true, true, 0L, Drop.ERROR),
            Row(State.ENDED, false, true, 0L, Drop.ENDED),
            Row(State.ENDED, false, false, 0L, null), // catch-up reaching its end is normal
            Row(State.BUFFERING, false, true, 14_000L, null),
            Row(State.BUFFERING, false, true, 15_000L, Drop.STALLED), // the limit itself counts
            Row(State.BUFFERING, false, true, 16_000L, Drop.STALLED),
            Row(State.BUFFERING, false, false, 16_000L, null), // a slow catch-up must not restart from the top
            Row(State.READY, false, true, 0L, null),
        )
        for (c in cases) {
            assertEquals(c.toString(), c.expected, StreamHealth.drop(c.state, c.error, c.live, c.bufferingMs))
        }
    }

    private data class Row(val state: State, val error: Boolean, val live: Boolean, val bufferingMs: Long, val expected: Drop?)
}
