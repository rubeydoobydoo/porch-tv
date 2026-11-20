package tv.porch.app.ui.series

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.porch.app.AppGraph
import tv.porch.app.data.ShowEntity
import tv.porch.app.data.ShowProgressEntity
import tv.porch.app.domain.Films
import tv.porch.app.domain.Shows
import tv.porch.app.parse.XtreamEpisode
import tv.porch.app.parse.XtreamShowInfo
import tv.porch.app.ui.components.Pill
import tv.porch.app.ui.components.backGoesBack
import tv.porch.app.ui.components.clickScale
import tv.porch.app.ui.components.tryFocus
import tv.porch.app.ui.movies.Poster
import tv.porch.app.ui.movies.ResumeBar
import tv.porch.app.ui.theme.PorchColors
import tv.porch.app.ui.theme.PorchRadius
import tv.porch.app.ui.theme.PorchSpace
import tv.porch.app.ui.theme.PorchType
import androidx.compose.ui.unit.dp

private val BarWidth = 300.dp

/** What the page has read: the episode list (null when it failed) and where the viewer is up to. */
private class PageRead(val info: XtreamShowInfo?, val progress: ShowProgressEntity?)

/**
 * The show page: a full-screen window over the screen that opened it. Back closes it ([onClose]). OK on an episode calls
 * [onPlay] with the whole show in play order, that episode, and where to start it, in milliseconds.
 */
@Composable
internal fun ShowPage(
    graph: AppGraph,
    show: ShowEntity,
    onPlay: (List<XtreamEpisode>, XtreamEpisode, Long) -> Unit,
    onClose: () -> Unit,
    backWording: String = "Back returns to Series",
) {
    val read by produceState<PageRead?>(null, show.sourceId, show.seriesId) {
        val progress = runCatching { graph.shows.progress(show.sourceId, show.seriesId) }.getOrNull()
        val info = runCatching { graph.shows.info(show.sourceId, show.seriesId) }.getOrNull()
        value = PageRead(info, progress)
    }
    val seasons = read?.info?.seasons.orEmpty()
    val progress = read?.progress
    var season by remember { mutableStateOf<Int?>(null) }
    val chosen = season ?: if (seasons.isEmpty()) null else Shows.startSeason(seasons, progress?.episodeId)
    val episodes = seasons.firstOrNull { it.number == chosen }?.episodes.orEmpty()
    val all = remember(seasons) { seasons.flatMap { it.episodes } }
    val listState = rememberLazyListState()
    val chip = remember { FocusRequester() }
    val upTo = remember { FocusRequester() }
    val firstRow = remember { FocusRequester() }
    val upToIndex = episodes.indexOfFirst { it.id == progress?.episodeId }

    // Focus lands on the episode you are up to, else the first one, once the list is read.
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(read != null, episodes.isNotEmpty()) {
        if (placed || read == null || episodes.isEmpty()) return@LaunchedEffect
        placed = true
        if (upToIndex > 0) listState.scrollToItem(upToIndex)
        withFrameNanos { }
        if (upToIndex >= 0) upTo.tryFocus() else firstRow.tryFocus()
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize()
                .onPreviewKeyEvent { event ->
                    // The Back that closed the page must not also reach the screen below when it comes back up.
                    event.key == Key.Back && event.type == KeyEventType.KeyUp
                }
                .backGoesBack().background(PorchColors.Bg).padding(PorchSpace.Margin),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(horizontalArrangement = Arrangement.spacedBy(PorchSpace.Margin)) {
                    Poster(show.name, read?.info?.coverUrl ?: show.coverUrl, PorchSpace.LoopPosterWidth, PorchSpace.LoopPosterHeight)
                    Column(Modifier.weight(1f)) {
                        val info = read?.info
                        val meta = Shows.meta(
                            info?.genre ?: show.genre, info?.year ?: show.year, seasons.size, info?.rating ?: show.rating,
                        )
                        if (meta.isNotEmpty()) {
                            Text(meta, style = PorchType.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(PorchSpace.HeroTitleAir))
                        }
                        Text(show.name, style = PorchType.ScreenTitle, color = PorchColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val plot = (info?.plot ?: show.plot)?.trim()
                        if (!plot.isNullOrEmpty()) {
                            Spacer(Modifier.height(PorchSpace.TileGap))
                            Text(plot, style = PorchType.Body, color = PorchColors.TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(Modifier.height(PorchSpace.TileGap))
                when {
                    read == null -> Text("Loading episodes…", style = PorchType.Meta)
                    seasons.isEmpty() -> Text("Couldn't load the episodes. Try again later.", style = PorchType.Meta)
                    else -> {
                        // Scrolls so a long-running show's later seasons are reachable; focus brings each chip into view.
                        Row(horizontalArrangement = Arrangement.spacedBy(PorchSpace.FilterGap), modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = PorchSpace.FocusRoom)) {
                            seasons.forEach { item ->
                                val selected = item.number == chosen
                                Pill(
                                    "Season ${item.number}",
                                    selected = selected,
                                    chip = true,
                                    modifier = (if (selected) Modifier.focusRequester(chip) else Modifier)
                                        .onFocusChanged { if (it.hasFocus) season = item.number },
                                ) { season = item.number }
                            }
                        }
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(PorchSpace.SettingsRowGap),
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        ) {
                            items(episodes.size, key = { episodes[it].id }) { index ->
                                val episode = episodes[index]
                                val mine = progress?.takeIf { it.episodeId == episode.id }
                                var modifier: Modifier = Modifier
                                if (index == 0) modifier = modifier.focusRequester(firstRow).focusProperties { up = chip }
                                if (mine != null) modifier = modifier.focusRequester(upTo)
                                EpisodeRow(episode, mine, modifier) {
                                    onPlay(all, episode, mine?.positionMs?.takeIf { it > 0L } ?: 0L)
                                }
                            }
                        }
                        Spacer(Modifier.height(PorchSpace.Margin))
                    }
                }
            }
            Text(
                "OK plays   ·   $backWording",
                style = PorchType.Hint,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}

/** One episode: "3  Title", its length, and for the one you are up to how far in (or "Up next") with the resume bar. */
@Composable
private fun EpisodeRow(episode: XtreamEpisode, mine: ShowProgressEntity?, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(PorchRadius.Row)
    var focused by remember { mutableStateOf(false) }
    val length = episode.durationSecs?.takeIf { it > 0 }?.let { Films.minutes(it * 1000L) }
    val note = mine?.let { Shows.episodeNote(it.positionMs) }
    val line = listOfNotNull(length, note).joinToString(" · ")
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(PorchSpace.SearchRow).onFocusChanged { focused = it.hasFocus },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = PorchColors.Surface,
            contentColor = PorchColors.Text,
            focusedContainerColor = PorchColors.Focus,
            focusedContentColor = PorchColors.OnFocus,
            pressedContainerColor = PorchColors.Focus,
            pressedContentColor = PorchColors.OnFocus,
        ),
        border = ClickableSurfaceDefaults.border(),
        scale = clickScale(),
        glow = ClickableSurfaceDefaults.glow(),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = PorchSpace.TileGap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PorchSpace.TileGap),
        ) {
            Text("${episode.number}  ${episode.title}", style = PorchType.TileTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (line.isNotEmpty()) {
                Text(line, style = PorchType.Meta, color = if (focused) PorchColors.OnFocus else PorchColors.Muted, maxLines = 1)
            }
            if (mine != null) {
                Spacer(Modifier.weight(1f))
                val total = mine.durationMs.takeIf { it > 0L } ?: ((episode.durationSecs ?: 0) * 1000L)
                ResumeBar(if (total > 0L) mine.positionMs.toFloat() / total else 0f, BarWidth)
            }
        }
    }
}
