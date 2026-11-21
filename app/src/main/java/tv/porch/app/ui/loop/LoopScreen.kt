package tv.porch.app.ui.loop

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import tv.porch.app.AppGraph
import tv.porch.app.data.PosterRatings
import tv.porch.app.data.ShowPosters
import tv.porch.app.domain.Channel
import tv.porch.app.domain.LoopChannels
import tv.porch.app.domain.LoopEmptyText
import tv.porch.app.domain.Tabs
import tv.porch.app.domain.formatHeaderClockLong
import tv.porch.app.ui.VmFactory
import tv.porch.app.ui.components.LocalQuickMenu
import tv.porch.app.ui.components.LocalReturnTile
import tv.porch.app.ui.components.OkKeycap
import tv.porch.app.ui.components.Pill
import tv.porch.app.ui.components.TopBar
import tv.porch.app.ui.components.clickScale
import tv.porch.app.ui.components.landsOnReturn
import tv.porch.app.ui.components.rememberNow
import tv.porch.app.ui.components.tryFocus
import tv.porch.app.ui.theme.PorchColors
import tv.porch.app.ui.theme.PorchFocus
import tv.porch.app.ui.theme.PorchRadius
import tv.porch.app.ui.theme.PorchSpace
import tv.porch.app.ui.theme.PorchType

/** One loop channel as the grid draws it: the channel, the show it plays, and its own logo when it has a real one. */
data class LoopItem(val channel: Channel, val name: String, val logo: String? = null)

/** What one chip lists, and the same channels in play order. */
class LoopShelf(val items: List<LoopItem>) {
    val channels: List<Channel> = items.map { it.channel }
}

data class LoopUi(
    val loading: Boolean = true,
    /** "All" first, then the genres, biggest first. */
    val genres: List<String> = emptyList(),
    /** The genre the grid shows, once the 200 ms settle has passed. */
    val genre: String = LoopChannels.ALL,
    val shelf: LoopShelf = LoopShelf(emptyList()),
    /** The provider has 24/7 channels at all, shown or not. Decides what the empty screen says. */
    val providerHasLoop: Boolean = false,
    /** 24/7 groups exist, but only as hidden groups. */
    val hiddenLoop: Boolean = false,
)

private class Built(val genres: List<String>, val shelves: Map<String, LoopShelf>, val providerHasLoop: Boolean, val hiddenLoop: Boolean)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class LoopViewModel(graph: AppGraph) : ViewModel() {
    /** The chip the viewer is on. Moves with focus, so the tick follows at once. */
    val selected = MutableStateFlow(LoopChannels.ALL)

    // Focus crossing the chips must not rebuild the grid on every step: the grid waits 200 ms for focus to settle.
    private val settled = flow {
        emit(selected.value)
        emitAll(selected.drop(1).debounce(200))
    }.distinctUntilChanged()

    // Built once per change to the channels or the picks, off the main thread: up to 2,800 channels.
    private val built = combine(graph.repository.observeChannels(), graph.repository.observeChosenGroups(), graph.repository.observeHidden()) { channels, chosen, hidden ->
        val listed = LoopChannels.listed(channels, chosen)
        // A logo many channels share is the provider's generic one; worked out once for the list, here, off the main thread.
        val shared = LoopChannels.sharedLogos(listed)
        val items = LoopChannels.ordered(listed).map { LoopItem(it, LoopChannels.showName(it), LoopChannels.ownLogo(it, shared)) }
        val byGenre = items.groupBy { LoopChannels.genreOf(it.channel.group) }
        Built(
            providerHasLoop = channels.any(LoopChannels::isLoop),
            // observeChannels leaves hidden groups out, so a hidden 24/7 group is only seen in the hidden list.
            hiddenLoop = hidden.any { LoopChannels.inLoopGroup(it.groupName) },
            genres = LoopChannels.genres(listed),
            shelves = byGenre.mapValues { LoopShelf(it.value) } + (LoopChannels.ALL to LoopShelf(items)),
        )
    }.flowOn(Dispatchers.Default)

    val state = combine(built, settled) { built, genre ->
        // A genre that is gone after a refresh falls back to All.
        val shown = if (genre in built.genres) genre else LoopChannels.ALL
        LoopUi(loading = false, genres = built.genres, genre = shown, shelf = built.shelves[shown] ?: LoopShelf(emptyList()), providerHasLoop = built.providerHasLoop, hiddenLoop = built.hiddenLoop)
    }
        .conflate()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoopUi())

    fun select(genre: String) {
        selected.value = genre
    }
}

private const val COLUMNS = 7
private val TilePad = PorchFocus.TileOutline + PorchFocus.TileOutlineGap

@Composable
fun LoopScreen(
    graph: AppGraph,
    onTab: (String) -> Unit,
    onPlay: (List<Channel>, Channel) -> Unit,
) {
    val model: LoopViewModel = viewModel(factory = VmFactory { LoopViewModel(graph) })
    val ui by model.state.collectAsStateWithLifecycle()
    val picked by model.selected.collectAsStateWithLifecycle()
    val ratingsOn by graph.ratings.on.collectAsStateWithLifecycle()
    val quick = LocalQuickMenu.current
    val returnTile = LocalReturnTile.current
    val selectedChip = remember { FocusRequester() }
    val selectedTab = remember { FocusRequester() }
    val firstTile = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    // Coming back from the player, landsOnReturn puts focus on the tile the viewer left; start-up focus must not fight it.
    val returning = remember { returnTile.armed }
    var gridFocused by remember { mutableStateOf(false) }
    var inChrome by remember { mutableStateOf(false) }

    val genre = if (picked in ui.genres) picked else LoopChannels.ALL
    val items = ui.shelf.items
    val empty = !ui.loading && ui.genres.size <= 1 && items.isEmpty()

    // A new chip starts at the top of its grid.
    val shownGenre = remember { arrayOf(ui.genre) }
    LaunchedEffect(ui.genre) {
        if (shownGenre[0] != ui.genre) {
            shownGenre[0] = ui.genre
            gridState.scrollToItem(0)
        }
    }

    // Focus starts on the first tile once the list is built, or on the tab when there is nothing to show.
    // It never pulls focus back from the chips or the tabs.
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(ui.loading, items.isEmpty()) {
        if (placed || ui.loading) return@LaunchedEffect
        withFrameNanos { }
        if (returning) {
            withFrameNanos { }
            if (!returnTile.armed) {
                placed = true
                return@LaunchedEffect
            }
        }
        if (!inChrome && !gridFocused) {
            val landed = if (items.isEmpty()) selectedTab.tryFocus() else firstTile.tryFocus()
            if (!landed) selectedChip.tryFocus()
        }
        placed = true
    }
    // The grid is far from the chips, so Back is the way up to them. A second Back leaves the screen.
    BackHandler(enabled = gridFocused) { selectedChip.tryFocus() }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = "24/7",
            clock = formatHeaderClockLong(rememberNow()),
            tabs = Tabs.labels,
            current = "24/7",
            onTab = onTab,
            modifier = Modifier.onFocusChanged { if (it.hasFocus) inChrome = true },
            selectedTab = selectedTab,
        )
        Text(
            "Shows on a loop, on all the time",
            style = PorchType.Meta,
            modifier = Modifier.padding(horizontal = PorchSpace.Margin),
        )
        if (empty) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = PorchSpace.Margin), contentAlignment = Alignment.Center) {
                Text(
                    LoopEmptyText.say(ui.providerHasLoop, ui.hiddenLoop),
                    style = PorchType.Body,
                    color = PorchColors.Muted,
                )
            }
        } else {
            Spacer(Modifier.height(PorchSpace.FilterGap))
            ChipRow(ui.genres, genre, selectedChip, selectedTab, onSelect = { model.select(it) }, onFocus = { inChrome = true })
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
                itemsIndexed(items, key = { _, item -> item.channel.id }) { index, item ->
                    var modifier: Modifier = Modifier
                    if (index == 0) modifier = modifier.focusRequester(firstTile)
                    // Up from the top row goes to the chosen chip, not to whichever chip is nearest.
                    if (index < COLUMNS) modifier = modifier.focusProperties { up = selectedChip }
                    LoopTile(
                        item = item,
                        posters = graph.posters,
                        ratings = graph.ratings,
                        modifier = modifier,
                        onPlay = { onPlay(ui.shelf.channels, item.channel) },
                        onLong = { quick.open(item.channel) },
                    )
                }
            }
        }
        HintLine(gridFocused, ratingsOn)
    }
}

/** "All" first, then the genres. Selection follows focus, like a tab row. */
@Composable
private fun ChipRow(
    genres: List<String>,
    current: String,
    selectedChip: FocusRequester,
    selectedTab: FocusRequester,
    onSelect: (String) -> Unit,
    onFocus: () -> Unit,
) {
    // Every chip has to be reachable, however many genres there are.
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = PorchSpace.Margin, vertical = PorchSpace.FocusRoom),
        horizontalArrangement = Arrangement.spacedBy(PorchSpace.FilterGap),
    ) {
        for (genre in genres) {
            val selected = genre == current
            Pill(
                genre,
                selected = selected,
                modifier = (if (selected) Modifier.focusRequester(selectedChip) else Modifier)
                    .focusProperties { up = selectedTab }
                    .onFocusChanged {
                        if (it.hasFocus) {
                            onFocus()
                            onSelect(genre)
                        }
                    },
            ) { onSelect(genre) }
        }
    }
}

/**
 * One loop channel: the show's poster (see [PosterFace]), the show's name underneath. Focused, a solid `Focus` card holds both and
 * lifts a little, the same as a Movies tile.
 */
@Composable
private fun LoopTile(item: LoopItem, posters: ShowPosters, ratings: PosterRatings, modifier: Modifier, onPlay: () -> Unit, onLong: () -> Unit) {
    val shape = RoundedCornerShape(PorchRadius.Tile + TilePad)
    Column(Modifier.width(PorchSpace.LoopPosterWidth + TilePad * 2)) {
        Surface(
            onClick = onPlay,
            onLongClick = onLong,
            modifier = modifier.landsOnReturn(),
            shape = ClickableSurfaceDefaults.shape(shape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = PorchColors.Text,
                focusedContainerColor = PorchColors.Focus,
                focusedContentColor = PorchColors.OnFocus,
                pressedContainerColor = PorchColors.Focus,
                pressedContentColor = PorchColors.OnFocus,
            ),
            border = ClickableSurfaceDefaults.border(),
            scale = clickScale(PorchFocus.TileScale),
            glow = ClickableSurfaceDefaults.glow(),
        ) {
            // The fill holds the poster and the name, as on Movies: the name takes the surface's content colour.
            Column(Modifier.padding(TilePad)) {
                PosterFace(item, posters, ratings)
                Spacer(Modifier.height(TilePad))
                Spacer(Modifier.height(PorchSpace.ListGap))
                Text(item.name, style = PorchType.TileTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun HintLine(gridFocused: Boolean, ratingsOn: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = PorchSpace.Margin, vertical = PorchSpace.TileGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PorchSpace.HomeTilePad),
    ) {
        OkKeycap()
        // Back climbs to the filters from the grid; on the filters it leaves the screen, so it says nothing there.
        Text(
            if (gridFocused) "plays   ·   Hold OK for the quick menu   ·   Back goes to the filters" else "plays   ·   Hold OK for the quick menu",
            style = PorchType.Hint,
        )
        Spacer(Modifier.weight(1f))
        Text(if (ratingsOn) "Posters from TVmaze and RPDB" else "Posters from TVmaze", style = PorchType.Hint)
    }
}
