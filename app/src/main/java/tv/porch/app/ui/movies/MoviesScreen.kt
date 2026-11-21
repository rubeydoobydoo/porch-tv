package tv.porch.app.ui.movies

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Text
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.porch.app.domain.Films
import tv.porch.app.AppGraph
import tv.porch.app.data.Film
import tv.porch.app.domain.MovieShelf
import tv.porch.app.domain.MoviesFocus
import tv.porch.app.domain.Tabs
import tv.porch.app.domain.formatHeaderClockLong
import tv.porch.app.ui.VmFactory
import tv.porch.app.ui.components.OkKeycap
import tv.porch.app.ui.components.Pill
import tv.porch.app.ui.components.TopBar
import tv.porch.app.ui.components.rememberNow
import tv.porch.app.ui.components.tryFocus
import tv.porch.app.ui.theme.PorchColors
import tv.porch.app.ui.theme.PorchRadius
import tv.porch.app.ui.theme.PorchSpace
import tv.porch.app.ui.theme.PorchType

data class ContinueItem(val film: Film, val progress: Float)

data class MoviesUi(
    val loading: Boolean = true,
    val noSource: Boolean = false,
    val sourceId: Long = 0L,
    val shelves: List<MovieShelf> = emptyList(),
    val filmsLoading: Boolean = true,
    val films: List<Film> = emptyList(),
    val failed: Boolean = false,
    val continueWatching: List<ContinueItem> = emptyList(),
    /** True once the Continue lookup has answered, even if it was empty or failed. */
    val continueLoaded: Boolean = false,
)

@OptIn(FlowPreview::class)
class MoviesViewModel(private val graph: AppGraph) : ViewModel() {
    /** The chip the viewer is on. Moves with focus, so the tick follows at once. */
    val selected = MutableStateFlow<String?>(null)
    private val state = MutableStateFlow(MoviesUi())
    val ui: StateFlow<MoviesUi> = state.asStateFlow()

    private var loading = false

    init {
        reload()
        viewModelScope.launch {
            // Focus crossing the chips must not reload on every step: the grid waits 200 ms for focus to settle.
            var first = true
            selected.filterNotNull().debounce { if (first) 0L else 200L }.distinctUntilChanged().collectLatest { title ->
                first = false
                loadFilms(title)
            }
        }
    }

    /** Loads the source and its shelves. Never two at once; the screen calls it again when the first try came back empty. */
    fun reload() {
        if (loading) return
        loading = true
        viewModelScope.launch {
            try {
                val source = runCatching { xtreamSource(graph) }.getOrNull()
                if (source == null) {
                    state.value = MoviesUi(loading = false, noSource = true)
                    return@launch
                }
                val shelves = runCatching { graph.movies.shelves(source.id) }.getOrDefault(emptyList())
                state.update { it.copy(loading = false, noSource = false, sourceId = source.id, shelves = shelves) }
                if (shelves.isEmpty()) state.update { it.copy(filmsLoading = false, failed = true) }
                else {
                    if (selected.value == null) selected.value = shelves.first().title
                    else loadFilms(selected.value!!)
                }
                refreshContinue()
            } finally {
                loading = false
            }
        }
    }

    private suspend fun loadFilms(title: String) {
        val now = state.value
        val shelf = now.shelves.firstOrNull { it.title == title } ?: return
        state.update { it.copy(filmsLoading = true, failed = false) }
        val loaded = runCatching { graph.movies.films(now.sourceId, shelf) }
        // The download may have dropped a film the provider withdrew, so Continue is read again in the same update.
        val continued = continueItems(now.sourceId)
        state.update {
            it.copy(
                filmsLoading = false, films = loaded.getOrDefault(emptyList()),
                failed = loaded.isFailure || loaded.getOrNull().isNullOrEmpty(), continueWatching = continued,
            )
        }
    }

    private suspend fun continueItems(id: Long): List<ContinueItem> = runCatching {
        graph.movies.continueWatching(id).mapNotNull { film ->
            val point = graph.movies.resumePoint(id, film.streamId) ?: return@mapNotNull null
            ContinueItem(film, Films.progress(point.positionMs, point.durationMs))
        }
    }.getOrDefault(emptyList())

    /** Called each time the tab opens, so a film just watched shows (or leaves) the shelf. */
    fun refreshContinue() {
        val id = state.value.sourceId
        if (id == 0L) return
        state.update { it.copy(continueLoaded = false) }
        viewModelScope.launch {
            val items = continueItems(id)
            state.update { it.copy(continueWatching = items, continueLoaded = true) }
        }
    }

    /** The film the viewer opened last, and whether from Continue watching. Kept here because this screen is rebuilt when Back comes from the player. */
    var returnTo: Pair<Long, Boolean>? = null

    fun select(title: String) {
        selected.value = title
    }

    fun syncTitles() {
        val id = state.value.sourceId
        if (id == 0L) return
        // Fire and forget: the screen never waits for it.
        viewModelScope.launch { runCatching { graph.movies.syncTitles(id) } }
    }
}

private const val COLUMNS = 7

@Composable
fun MoviesScreen(
    graph: AppGraph,
    onTab: (String) -> Unit,
    onPlay: (Film, Long) -> Unit,
) {
    val model: MoviesViewModel = viewModel(factory = VmFactory { MoviesViewModel(graph) })
    val ui by model.ui.collectAsStateWithLifecycle()
    val picked by model.selected.collectAsStateWithLifecycle()
    val selectedChip = remember { FocusRequester() }
    val selectedTab = remember { FocusRequester() }
    val firstTile = remember { FocusRequester() }
    val firstContinue = remember { FocusRequester() }
    val returnFocus = remember { FocusRequester() }
    val chipState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val continueState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var gridFocused by remember { mutableStateOf(false) }
    var tileFocused by remember { mutableStateOf(false) }
    var inChrome by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<Film?>(null) }

    // Draw first, then work: the top bar is up before the full list is asked for.
    LaunchedEffect(ui.sourceId) {
        withFrameNanos { }
        if (ui.noSource || ui.shelves.isEmpty()) model.reload()
        model.refreshContinue()
        model.syncTitles()
    }

    val shelf = if (ui.shelves.any { it.title == picked }) picked else ui.shelves.firstOrNull()?.title
    val shelfRow = ui.continueWatching.isNotEmpty()
    val chipsIndex = if (shelfRow) 1 else 0

    // Focus starts on the first poster once films arrive, never pulling it from the chips or the tabs.
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(ui.filmsLoading, ui.films.isEmpty(), ui.continueLoaded) {
        if (placed || ui.filmsLoading || !ui.continueLoaded) return@LaunchedEffect
        withFrameNanos { }
        val back = model.returnTo
        if (!inChrome && !gridFocused) {
            // Back from a film lands on that film's poster, not the first one.
            val backIndex = back?.let { (id, fromContinue) ->
                if (fromContinue) ui.continueWatching.indexOfFirst { it.film.streamId == id }
                else ui.films.indexOfFirst { it.streamId == id }
            } ?: -1
            val landedBack = if (back != null && backIndex >= 0) {
                if (back.second) {
                    // The Continue row is the grid's first item; bring it into view, then scroll the row to the poster.
                    if (gridState.layoutInfo.visibleItemsInfo.none { it.key == "continue" }) gridState.scrollToItem(0)
                    continueState.scrollToItem(backIndex)
                } else if (gridState.layoutInfo.visibleItemsInfo.none { it.key == ui.films[backIndex].streamId }) {
                    gridState.scrollToItem(chipsIndex + 1 + backIndex)
                }
                withFrameNanos { }
                returnFocus.tryFocus()
            } else {
                false
            }
            model.returnTo = null
            val start = MoviesFocus.start(ui.continueWatching.size, ui.films.size)
            val landed = landedBack || when (start) {
                MoviesFocus.Start.Continue -> firstContinue.tryFocus().also {
                    if (it && gridState.firstVisibleItemIndex != 0) gridState.scrollToItem(0)
                }
                MoviesFocus.Start.Grid -> firstTile.tryFocus()
                MoviesFocus.Start.Chips -> false
            }
            if (!landed) focusSelectedChip(chipState, ui.shelves, shelf, selectedChip)
        }
        placed = true
    }
    // A new chip starts at the top of its grid, with the chips still in view.
    val shownShelf = remember { arrayOf(shelf) }
    LaunchedEffect(shelf) {
        if (shownShelf[0] != shelf) {
            shownShelf[0] = shelf
            gridState.scrollToItem(chipsIndex)
        }
    }
    // The grid is far from the chips, so Back is the way up to them. A second Back leaves the screen.
    BackHandler(enabled = tileFocused) {
        scope.launch {
            gridState.scrollToItem(chipsIndex)
            withFrameNanos { }
            focusSelectedChip(chipState, ui.shelves, shelf, selectedChip)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = "Movies",
            clock = formatHeaderClockLong(rememberNow()),
            tabs = Tabs.labels,
            current = "Movies",
            onTab = onTab,
            modifier = Modifier.onFocusChanged { if (it.hasFocus) inChrome = true },
            selectedTab = selectedTab,
        )
        if (ui.noSource) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = PorchSpace.Margin), contentAlignment = Alignment.Center) {
                Text("Films need an Xtream Codes login.", style = PorchType.Body, color = PorchColors.Muted)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(COLUMNS),
                state = gridState,
                contentPadding = PaddingValues(horizontal = PorchSpace.Margin - TilePad, vertical = PorchSpace.FocusRoom),
                horizontalArrangement = Arrangement.spacedBy(PorchSpace.LoopPosterGap),
                verticalArrangement = Arrangement.spacedBy(PorchSpace.HomeTileRowGap),
                modifier = Modifier.weight(1f).onFocusChanged {
                    gridFocused = it.hasFocus
                    if (it.hasFocus) inChrome = false
                },
            ) {
                if (shelfRow) {
                    item(span = { GridItemSpan(COLUMNS) }, key = "continue") {
                        Column {
                            Text("Continue watching", style = PorchType.Section, modifier = Modifier.padding(horizontal = TilePad))
                            Spacer(Modifier.height(PorchSpace.ListGap))
                            LazyRow(state = continueState, horizontalArrangement = Arrangement.spacedBy(PorchSpace.LoopPosterGap)) {
                                items(ui.continueWatching.size, key = { ui.continueWatching[it].film.streamId }) { index ->
                                    val item = ui.continueWatching[index]
                                    FilmTile(
                                        item.film, item.progress,
                                        (if (index == 0) Modifier.focusRequester(firstContinue) else Modifier)
                                            .then(if (model.returnTo?.let { it.second && it.first == item.film.streamId } == true) Modifier.focusRequester(returnFocus) else Modifier)
                                            .focusProperties { up = selectedTab },
                                    ) {
                                        model.returnTo = item.film.streamId to true
                                        open = item.film
                                    }
                                }
                            }
                        }
                    }
                }
                item(span = { GridItemSpan(COLUMNS) }, key = "chips") {
                    Column {
                        ChipRow(
                            ui.shelves.map { it.title }, shelf, chipState, selectedChip, selectedTab, upToTabs = !shelfRow,
                            onSelect = { model.select(it) }, onFocus = { inChrome = true },
                        )
                        when {
                            ui.filmsLoading -> Unit
                            ui.failed -> Text(
                                "Couldn't load these films. Try again later.",
                                style = PorchType.Meta,
                                modifier = Modifier.padding(horizontal = TilePad, vertical = PorchSpace.TileGap),
                            )
                        }
                    }
                }
                if (ui.filmsLoading) {
                    items(COLUMNS * 2, key = { "block$it" }) {
                        Box(Modifier.padding(TilePad)) { PosterBlock() }
                    }
                } else {
                    items(ui.films.size, key = { ui.films[it].streamId }) { index ->
                        var modifier: Modifier = Modifier
                        if (index == 0) modifier = modifier.focusRequester(firstTile)
                        if (index < COLUMNS) modifier = modifier.focusProperties {
                            // Only aim at the chip when it is composed; otherwise let focus find its own way.
                            val chipsShown = gridState.layoutInfo.visibleItemsInfo.any { it.key == "chips" }
                            val at = ui.shelves.indexOfFirst { it.title == shelf }
                            val chipShown = chipState.layoutInfo.visibleItemsInfo.any { it.index == at }
                            up = if (chipsShown && chipShown) selectedChip else FocusRequester.Default
                        }
                        if (model.returnTo?.let { !it.second && it.first == ui.films[index].streamId } == true) modifier = modifier.focusRequester(returnFocus)
                        FilmTile(ui.films[index], null, modifier.onFocusChanged { tileFocused = it.hasFocus }) {
                            model.returnTo = ui.films[index].streamId to false
                            open = ui.films[index]
                        }
                    }
                }
            }
        }
        HintLine()
    }

    open?.let { film ->
        FilmSheet(
            graph = graph,
            film = film,
            onPlay = { startAtMs ->
                open = null
                onPlay(film, startAtMs)
            },
            onClose = {
                open = null
                model.returnTo = null
            },
        )
    }
}

/** A solid block where a poster will be. */
@Composable
internal fun PosterBlock() {
    Box(Modifier.size(PorchSpace.LoopPosterWidth, PorchSpace.LoopPosterHeight).background(PorchColors.Surface, RoundedCornerShape(PorchRadius.Tile)))
}

/** The shelves as chips. Selection follows focus, like a tab row. */
@Composable
internal fun ChipRow(
    titles: List<String>,
    current: String?,
    state: LazyListState,
    selectedChip: FocusRequester,
    selectedTab: FocusRequester,
    upToTabs: Boolean,
    onSelect: (String) -> Unit,
    onFocus: () -> Unit,
) {
    LazyRow(
        state = state,
        modifier = Modifier.padding(vertical = PorchSpace.FocusRoom),
        contentPadding = PaddingValues(horizontal = PorchSpace.Margin - TilePad),
        horizontalArrangement = Arrangement.spacedBy(PorchSpace.FilterGap),
    ) {
        items(titles.size, key = { titles[it] }) { index ->
            val title = titles[index]
            val selected = title == current
            Pill(
                title,
                selected = selected,
                chip = true,
                modifier = (if (selected) Modifier.focusRequester(selectedChip) else Modifier)
                    .then(if (upToTabs) Modifier.focusProperties { up = selectedTab } else Modifier)
                    .onFocusChanged {
                        if (it.hasFocus) {
                            onFocus()
                            onSelect(title)
                        }
                    },
            ) { onSelect(title) }
        }
    }
}

/** The chips are a lazy row, so bring the selected one into view before asking it for focus. */
internal suspend fun focusSelectedChip(
    state: LazyListState,
    shelves: List<MovieShelf>,
    current: String?,
    selectedChip: FocusRequester,
) {
    val index = shelves.indexOfFirst { it.title == current }
    if (index >= 0 && state.layoutInfo.visibleItemsInfo.none { it.index == index }) {
        state.scrollToItem(index)
        withFrameNanos { }
    }
    selectedChip.tryFocus()
}

@Composable
private fun HintLine() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = PorchSpace.Margin, vertical = PorchSpace.TileGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PorchSpace.HomeTilePad),
    ) {
        OkKeycap()
        Text("opens the film", style = PorchType.Hint)
    }
}
