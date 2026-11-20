package tv.porch.app.player

import tv.porch.app.data.Film
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** The film to play and where to start it: 0 for the beginning, or the resume point. */
data class FilmRequest(val film: Film, val startAtMs: Long)

/** Holds a film for the Film route, by token, like [PlaybackBus] does for live channels. */
class FilmPlayback {
    private val items = ConcurrentHashMap<Long, FilmRequest>()
    private val sequence = AtomicLong(1)

    fun put(request: FilmRequest): Long {
        val token = sequence.getAndIncrement()
        items[token] = request
        return token
    }

    fun get(token: Long): FilmRequest? = items[token]

    fun remove(token: Long) {
        items.remove(token)
    }
}
