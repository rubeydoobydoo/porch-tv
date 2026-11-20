package tv.porch.app.player

import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** What to play, held in memory because a zap list can be thousands of channel ids. */
data class CatchupRequest(
    val startUtc: Long,
    val stopUtc: Long,
    val title: String,
)

data class PlaybackRequest(
    val channelIds: List<Long>,
    val index: Int,
    val catchup: CatchupRequest? = null,
    /** Another copy of the channel at [index] to play in its place, for now. */
    val copyId: Long? = null,
)

/** A new channel for the player already on screen: it plays it as a zap would, on the same player. */
data class TuneRequest(
    val channelIds: List<Long>,
    val index: Int,
)

class PlaybackBus {
    private val items = ConcurrentHashMap<Long, PlaybackRequest>()
    private val sequence = AtomicLong(1)

    // One tune at a time: a newer one replaces one not yet taken. Kept until a player takes it, so one posted while the player is busy is still played.
    private val pendingTune = MutableStateFlow<TuneRequest?>(null)

    /** True while a player is on screen. Background guide updates wait for this to clear. */
    @Volatile
    var watching = false

    fun put(request: PlaybackRequest): Long {
        // A new player starts from its own request, so a tune meant for the one before is dropped.
        pendingTune.value = null
        val token = sequence.getAndIncrement()
        items[token] = request
        return token
    }

    fun get(token: Long): PlaybackRequest? = items[token]

    fun tune(request: TuneRequest) {
        pendingTune.value = request
    }

    /** Hands each tune to [handle] once; whoever takes it first has it, so a later collector never replays it. */
    suspend fun collectTunes(handle: suspend (TuneRequest) -> Unit) {
        pendingTune.collect { request ->
            if (request != null && pendingTune.compareAndSet(request, null)) handle(request)
        }
    }
}
