package tv.porch.app.player

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import tv.porch.app.data.ShowEntity
import tv.porch.app.parse.XtreamEpisode

/** The episode to play, the whole show in play order (so a finished one can move on), and where to start. */
data class EpisodeRequest(
    val show: ShowEntity,
    val episodes: List<XtreamEpisode>,
    val episode: XtreamEpisode,
    val startAtMs: Long,
)

/** Holds an episode for the Episode route, by token, like [FilmPlayback] does for films. */
class EpisodePlayback {
    private val items = ConcurrentHashMap<Long, EpisodeRequest>()
    private val sequence = AtomicLong(1)

    fun put(request: EpisodeRequest): Long {
        val token = sequence.getAndIncrement()
        items[token] = request
        return token
    }

    fun get(token: Long): EpisodeRequest? = items[token]

    fun remove(token: Long) {
        items.remove(token)
    }
}
