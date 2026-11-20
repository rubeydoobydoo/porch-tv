package tv.porch.app.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import java.util.Locale
import tv.porch.app.AppGraph
import tv.porch.app.data.Film
import tv.porch.app.data.ResumePointEntity
import tv.porch.app.domain.Films
import tv.porch.app.ui.components.Pill
import tv.porch.app.ui.components.backGoesBack
import tv.porch.app.ui.components.tryFocus
import tv.porch.app.ui.theme.PorchColors
import tv.porch.app.ui.theme.PorchSpace
import tv.porch.app.ui.theme.PorchType

private val PosterWidth = 260.dp
private val PosterHeight = 366.dp
private val BarWidth = 300.dp

/** The resume point read from the database, or none. Null until the read is done. */
private class ResumeRead(val point: ResumePointEntity?)

/**
 * The film details sheet: a full-screen window over the screen that opened it. Back closes it ([onClose]) and the screen
 * below keeps its focus. OK on a pill calls [onPlay] with where to start, in milliseconds.
 */
@Composable
internal fun FilmSheet(
    graph: AppGraph,
    film: Film,
    onPlay: (Long) -> Unit,
    onClose: () -> Unit,
    backWording: String = "Back returns to Movies",
) {
    // The list row's data shows at once; the plot and length fill in when the details arrive.
    val shown by produceState(film, film.sourceId, film.streamId) {
        value = runCatching { graph.movies.details(film.sourceId, film.streamId) }.getOrNull() ?: film
    }
    val resume by produceState<ResumeRead?>(null, film.sourceId, film.streamId) {
        value = ResumeRead(runCatching { graph.movies.resumePoint(film.sourceId, film.streamId) }.getOrNull())
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(resume != null) {
        if (resume != null) {
            withFrameNanos { }
            first.tryFocus()
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize()
                .onPreviewKeyEvent { event ->
                    // The Back that closed the sheet must not also reach the screen below when it comes back up.
                    event.key == Key.Back && event.type == KeyEventType.KeyUp
                }
                .backGoesBack().background(PorchColors.Bg).padding(PorchSpace.Margin),
        ) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(PorchSpace.Margin),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilmPoster(shown, PosterWidth, PosterHeight)
                Column(Modifier.weight(1f)) {
                    val point = resume?.point
                    val text = Films.sheet(point?.positionMs, point?.durationMs, shown.durationSecs)
                    val meta = listOfNotNull(
                        shown.genre?.takeIf { it.isNotBlank() },
                        shown.year?.takeIf { it > 0 }?.toString(),
                        text.length,
                        shown.rating?.takeIf { it > 0.0 }?.let { String.format(Locale.ROOT, "Rated %.1f", it) },
                    ).joinToString(" · ")
                    if (meta.isNotEmpty()) {
                        Text(meta, style = PorchType.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(PorchSpace.HeroTitleAir))
                    }
                    Text(shown.name, style = PorchType.ScreenTitle, color = PorchColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val plot = shown.plot?.trim()
                    if (!plot.isNullOrEmpty()) {
                        Spacer(Modifier.height(PorchSpace.TileGap))
                        Text(plot, style = PorchType.Body, color = PorchColors.TextSecondary, maxLines = 7, overflow = TextOverflow.Ellipsis)
                    }
                    if (point != null && text.resumeLine != null) {
                        Spacer(Modifier.height(PorchSpace.TileGap))
                        ResumeBar(Films.progress(point.positionMs, point.durationMs), BarWidth)
                        Spacer(Modifier.height(PorchSpace.HeroTitleAir))
                        Text(text.resumeLine, style = PorchType.Meta, maxLines = 1)
                    }
                    if (resume != null) {
                        Spacer(Modifier.height(PorchSpace.TileGap))
                        Row(horizontalArrangement = Arrangement.spacedBy(PorchSpace.TileGap)) {
                            text.pills.forEachIndexed { index, label ->
                                Pill(label, modifier = if (index == 0) Modifier.focusRequester(first) else Modifier) {
                                    // The first pill resumes when the film is started; every other one starts from 0.
                                    onPlay(if (index == 0 && point != null && text.pills.size > 1) point.positionMs else 0L)
                                }
                            }
                        }
                    }
                }
            }
            Text(
                "OK plays   ·   Left and Right skip 10 s while it plays   ·   $backWording",
                style = PorchType.Hint,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}
