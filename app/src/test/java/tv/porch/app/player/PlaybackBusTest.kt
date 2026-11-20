package tv.porch.app.player

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackBusTest {
    @Test
    fun aTuneReachesTheOpenPlayerOnceAndALaterPlayerGetsNothing() = runBlocking<Unit> {
        val bus = PlaybackBus()
        val first = mutableListOf<TuneRequest>()
        val request = TuneRequest(channelIds = listOf(7L, 8L, 9L), index = 1)
        val open = launch(start = CoroutineStart.UNDISPATCHED) { bus.collectTunes { first += it } }
        bus.tune(request)
        yield()
        open.cancel()
        assertEquals(listOf(request), first)

        // Taken once: a player that starts collecting afterwards gets nothing.
        val second = mutableListOf<TuneRequest>()
        val later = launch(start = CoroutineStart.UNDISPATCHED) { bus.collectTunes { second += it } }
        yield()
        later.cancel()
        assertEquals(emptyList<TuneRequest>(), second)
    }
}
