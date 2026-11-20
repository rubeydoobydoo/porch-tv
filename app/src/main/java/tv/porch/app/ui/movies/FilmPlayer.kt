package tv.porch.app.ui.movies

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import tv.porch.app.AppGraph

/** Plays one film. The resume point is saved every 30 s while playing, on pause, on stop and on leaving. */
@Composable
fun FilmPlayer(graph: AppGraph, token: Long, onExit: () -> Unit) {
    DisposableEffect(token) { onDispose { graph.filmPlayback.remove(token) } }
    val request = remember(token) { graph.filmPlayback.get(token) }
    val film = request?.film
    VodPlayer(
        title = film?.name,
        startAtMs = request?.startAtMs ?: 0L,
        failedText = "This film won't play right now.",
        url = {
            val source = film?.let { graph.repository.source(it.sourceId) }
            source?.let { graph.movies.playUrl(it, film) }
        },
        save = { position, duration -> film?.let { graph.movies.saveResume(it.sourceId, it.streamId, position, duration) } },
        onExit = onExit,
    )
}
