package tv.porch.app.ui.series

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import tv.porch.app.AppGraph
import tv.porch.app.domain.Shows
import tv.porch.app.ui.movies.VodPlayer

/** Plays one episode on the film player. Its progress goes to the show's Continue tile, and a finished episode moves it on. */
@Composable
fun EpisodePlayer(graph: AppGraph, token: Long, onExit: () -> Unit) {
    DisposableEffect(token) { onDispose { graph.episodePlayback.remove(token) } }
    val request = remember(token) { graph.episodePlayback.get(token) }
    val episode = request?.episode
    VodPlayer(
        title = request?.let { Shows.playerTitle(it.show.name, it.episode.season, it.episode.number, it.episode.title) },
        startAtMs = request?.startAtMs ?: 0L,
        failedText = "This episode won't play right now.",
        url = {
            val source = request?.let { graph.repository.source(it.show.sourceId) }
            source?.let { graph.shows.playUrl(it, episode!!.id, episode.ext) }
        },
        save = { position, duration ->
            request?.let { graph.shows.saveWatch(it.show.sourceId, it.show.seriesId, it.episodes, it.episode, position, duration) }
        },
        onExit = onExit,
    )
}
