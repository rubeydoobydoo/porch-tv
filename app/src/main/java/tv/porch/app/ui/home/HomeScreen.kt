package tv.porch.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.porch.app.AppGraph
import tv.porch.app.data.SyncStatus
import tv.porch.app.data.pacedWhile
import tv.porch.app.domain.Channel
import tv.porch.app.domain.GroupChoice
import tv.porch.app.domain.GuideHealth
import tv.porch.app.domain.HomeLists
import tv.porch.app.domain.LiveRows
import tv.porch.app.domain.NowChip
import tv.porch.app.domain.Programme
import tv.porch.app.domain.ProgrammeDetail
import tv.porch.app.domain.Kind
import tv.porch.app.domain.SOON_WINDOW_MS
import tv.porch.app.domain.Tabs
import tv.porch.app.domain.formatHeaderClockLong
import tv.porch.app.domain.formatTime
import tv.porch.app.ui.VmFactory
import tv.porch.app.ui.components.ReminderMarks
import tv.porch.app.ui.components.rememberReminderMarks
import tv.porch.app.ui.components.LiveRowItem
import tv.porch.app.ui.components.Pill
import tv.porch.app.ui.components.LocalQuickMenu
import tv.porch.app.ui.components.LocalReturnTile
import tv.porch.app.ui.components.OkKeycap
import tv.porch.app.ui.components.TopBar
import tv.porch.app.ui.components.RowFocus
import tv.porch.app.ui.components.tryFocus
import tv.porch.app.ui.components.clickScale
import tv.porch.app.player.CatchupRequest
import tv.porch.app.ui.components.rememberNow
import tv.porch.app.ui.theme.PorchColors
import tv.porch.app.ui.theme.PorchFocus
import tv.porch.app.ui.theme.PorchRadius
import tv.porch.app.ui.theme.PorchSpace
import tv.porch.app.ui.theme.PorchType
import tv.porch.app.ui.theme.VIEWER_NAME

data class HomeUi(
    val loading: Boolean = true,
    val lists: HomeLists.Lists? = null,
    /** True when the viewer has channels in the groups they picked, whether or not the guide knows what is on. */
    val hasChannels: Boolean = false,
    /** True when most channels the guide knows have nothing on now. Never true while a refresh is running. */
    val guideOutOfDate: Boolean = false,
    /** True while a refresh runs and there are still no channels or nothing on now: the first import. */
    val loadingGuide: Boolean = false,
    /** True when there are no channels yet or nothing is on now, so a running import is worth a line. */
    val thin: Boolean = false,
    /** True while this is the saved Home from last time, which keeps no favourites list (simple mode waits for the real one). */
    val fromSnapshot: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val graph: AppGraph) : ViewModel() {
    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }

    /**
     * What the viewer follows that is on now or coming up. It starts unknown (null) and fills in on its own, so Home's first lists never
     * wait for it; it is looked up again when the follows change or a guide refresh starts or ends.
     */
    private val following = MutableStateFlow<List<Pair<Channel, Programme>>?>(null)

    // So the seven-day window moves on while Home stays open.
    private val quarterHour = flow {
        while (true) {
            emit(Unit)
            delay(15 * 60_000L)
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            combine(graph.follows.follows, graph.folds.listing, graph.sync.status.map { graph.sync.importing() }.distinctUntilChanged(), quarterHour) { terms, listing, _, _ -> terms to listing.families }
                .collectLatest { (terms, families) -> following.value = runCatching { graph.repository.following(terms, System.currentTimeMillis(), families) }.getOrDefault(emptyList()) }
        }
    }

    /** Channels in the groups the viewer picked (all of them when nothing is picked). */
    private val mineChannels = combine(
        graph.folds.listing,
        graph.repository.observeChosenGroups(),
    ) { listing, chosen -> GroupChoice.filter(listing.channels, GroupChoice.live(chosen, listing.channels)) to listing.families }

    /** The four lists are built here, off the main thread. The screen only draws them. */
    private val built = clock.flatMapLatest { now ->
        combine(
            mineChannels,
            graph.repository.observeOnNow(now).pacedWhile(graph.sync::importing),
            graph.repository.observeStartingUntil(now, now + SOON_WINDOW_MS).pacedWhile(graph.sync::importing),
            graph.repository.observeSources(),
            // A refresh starting or ending is a reason to count again; what is followed rides along and is never waited for.
            graph.sync.status.combine(following) { _, followed -> followed },
        ) { (mine, families), onNow, soon, sources, followed ->
            val offsets = sources.associate { it.id to it.guideOffsetMinutes }
            val onAir = onNow.mapTo(HashSet()) { it.sourceId to it.channelKey }
            val matched = mine.filter { it.matchedXmltvId != null }
            val withShow = matched.count { (it.sourceId to it.matchedXmltvId) in onAir }
            val importing = graph.sync.importing()
            HomeUi(
                loading = false,
                lists = HomeLists.build(mine, onNow, soon, offsets, now, families, followed.orEmpty()).let { built ->
                    // Until the follows are looked up, keep the saved Following list rather than flashing Starting soon.
                    if (followed == null) built.copy(following = saved.value?.lists?.following ?: built.following) else built
                },
                hasChannels = mine.isNotEmpty(),
                guideOutOfDate = !importing && GuideHealth.outOfDate(matched.size, withShow),
                loadingGuide = importing && (mine.isEmpty() || onNow.isEmpty()),
                thin = mine.isEmpty() || onNow.isEmpty(),
            )
        }
    }.conflate().pacedWhile(graph.sync::importing)
        // Remember what Home showed, but not a half-built Home while the first import runs.
        .onEach { ui -> if (!ui.loadingGuide && ui.hasChannels) ui.lists?.let { graph.homeSnapshot.offer(it) } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    /** Home as it was last time, read once in the background. Null when there is none, or it can't be read. */
    private val saved = MutableStateFlow<HomeUi?>(null)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val lists = graph.homeSnapshot.read()?.live(System.currentTimeMillis()) ?: return@launch
            saved.value = HomeUi(loading = false, lists = lists, hasChannels = true, fromSnapshot = true)
        }
    }

    /** What the screen draws: the saved Home until the database has built the real one, then the real one. */
    val state: StateFlow<HomeUi> = combine(built, saved) { real, old -> if (real.loading && old != null) old else real }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())
}

private val TABS = Tabs.labels

private val SportChip = NowChip.OfKind(Kind.Sport)

private const val SPORT = 1
private const val YOURS = 2
private const val SOON = 3

/** A focusable place on Home: a row (by channel), a list's "See all" or the Full guide pill, in group [list]. */
private data class HomeKey(val list: Int, val id: String)

private val OpenGuide = HomeKey(SOON, "open-guide")

/**
 * The row the preview pane follows. Only [FollowingPreview] reads [place]; the page only ever writes it, so moving
 * focus redraws the pane and the two rows involved, not the page.
 */
private class PreviewFocus {
    var place by mutableStateOf<HomeKey?>(null)
}

/**
 * The preview pane across the top, then three columns of flat rows: Live sport, Your channels and Starting soon.
 * Each row keeps its own focus look, and the pane holds its own state, so moving around never redraws the page.
 * [onWhatsOn] opens What's on now; a chip means "See all" asked for it, null is the tab.
 * There is no "Watch on" panel here: OK plays, and holding OK opens the quick menu.
 */
@Composable
fun HomeScreen(
    graph: AppGraph,
    onWhatsOn: (NowChip?) -> Unit,
    onGuide: () -> Unit,
    onLoop: () -> Unit,
    onMovies: () -> Unit = {},
    onSeries: () -> Unit = {},
    onSport: () -> Unit = {},
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onPlay: (List<Channel>, Channel) -> Unit,
    /** Plays a programme as catch-up, from the details sheet. */
    onWatch: (List<Channel>, Channel, CatchupRequest?, Channel?) -> Unit,
    exitWarning: State<Boolean>,
) {
    val model: HomeViewModel = viewModel(factory = VmFactory { HomeViewModel(graph) })
    val quick = LocalQuickMenu.current
    val marks = rememberReminderMarks(graph)
    val returnTile = LocalReturnTile.current
    val ui by model.state.collectAsStateWithLifecycle()
    val lists = ui.lists
    // A new TV with no guide yet: channels, but no row on any list has a programme. Favourites alone don't count.
    val noGuide = lists != null && ui.hasChannels && listOf(lists.sport, lists.yours, lists.third).none { section -> section.all.any { it.programmeId != 0L } }
    val rowFocus = remember { RowFocus<HomeKey> { it.list } }
    val previewFocus = remember { PreviewFocus() }
    val selectedTab = remember { FocusRequester() }
    // Coming back from the player, landsOnReturn puts focus on the row the viewer left; start-up focus must not fight it.
    val returning = remember { returnTile.armed }
    // Every place focus can rest, in reading order (Live sport, Your channels, Starting soon), "See all" last in its list.
    val order = remember(lists, noGuide) {
        if (lists == null) {
            emptyList()
        } else if (noGuide) {
            // With no guide, favourites and recent channels still show, then the way into the Full guide.
            lists.yours.shown.map { rowKey(YOURS, it) } + (if (lists.yours.more > 0) listOf(HomeKey(YOURS, "see-all")) else emptyList()) + OpenGuide
        } else {
            listOf(SPORT to lists.sport, YOURS to lists.yours, SOON to lists.third).flatMap { (i, section) ->
                section.shown.map { rowKey(i, it) } +
                    if (section.more > 0) listOf(HomeKey(i, "see-all")) else emptyList()
            }
        }
    }
    // What each row on screen shows, in reading order, for the pane to look up.
    val previewRows = remember(lists, noGuide) {
        buildMap {
            if (lists != null && !noGuide) {
                for ((i, section) in listOf(SPORT to lists.sport, YOURS to lists.yours, SOON to lists.third)) {
                    for (row in section.shown) put(rowKey(i, row), row)
                }
            }
        }
    }

    // First focus: the first row; with nothing at all, the selected tab.
    // It does not take focus from the top bar if the viewer has already moved there while the data loaded.
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(lists != null, order.isNotEmpty()) {
        if (!placed && lists != null) {
            withFrameNanos { }
            if (returning) {
                withFrameNanos { }
                if (!returnTile.armed) {
                    placed = true
                    return@LaunchedEffect
                }
            }
            if (!rowFocus.inChrome && !rowFocus.anyRowFocused) {
                val first = order.firstOrNull()
                if (first == null || !rowFocus.requester(first).tryFocus()) selectedTab.tryFocus()
            }
            placed = true
        }
    }
    // The place focus was on is gone (a list emptied, "See all" went): the nearest row in that list, else the first row, else the tab.
    LaunchedEffect(order) {
        withFrameNanos { }
        withFrameNanos { }
        val lost = rowFocus.lostRow(order)
        val target = rowFocus.settle(order)
        if (lost && !(target != null && rowFocus.requester(target).tryFocus())) {
            val first = order.firstOrNull()
            if (first == null || !rowFocus.requester(first).tryFocus()) selectedTab.tryFocus()
        }
    }

    val playFrom: (HomeLists.Section, LiveRows.Row) -> Unit = { section, row ->
        onPlay(section.all.map { it.channels.first() }.distinctBy { it.id }, row.channels.first())
    }
    val holdScope = rememberCoroutineScope()
    val hold: (HomeLists.Section, LiveRows.Row) -> Unit = { section, row ->
        val channel = row.channels.first()
        val zap = section.all.map { it.channels.first() }.distinctBy { it.id }
        // The programme is a row away in the database; the menu opens as soon as it answers.
        holdScope.launch {
            val programme = if (row.programmeId != 0L) graph.repository.programme(row.programmeId) else null
            quick.open(channel, programme = programme, onWatch = { catchup, copy -> onWatch(zap, channel, catchup, copy) })
        }
    }
    // Up from the first row of a list goes to the current tab. The pane in between can't take focus.
    val upToTabs: Modifier = Modifier.focusProperties { up = selectedTab }
    val onRowFocused: (HomeKey) -> Unit = { place ->
        rowFocus.gained(place)
        previewFocus.place = place
    }

    Column(Modifier.fillMaxSize()) {
        HomeTopBar(
            selectedTab = selectedTab,
            onChrome = { rowFocus.chrome() },
            onTab = { tab ->
                when (tab) {
                    "Full guide" -> onGuide()
                    "What's on now" -> onWhatsOn(null)
                    "24/7" -> onLoop()
                    "Movies" -> onMovies()
                    "Series" -> onSeries()
                    "Sport" -> onSport()
                    "Search" -> onSearch()
                    "Settings" -> onSettings()
                }
            },
        )
        HomeNote(graph, thin = ui.thin || noGuide, outOfDate = ui.guideOutOfDate, empty = lists != null && !ui.hasChannels)
        // With no row to show there is no pane, so the no-guide line and the empty lists get the screen.
        if (lists == null || previewRows.isNotEmpty()) {
            FollowingPreview(
                previewFocus,
                previewRows,
                loadDetail = graph.repository::programmeDetail,
                modifier = Modifier.padding(start = PorchSpace.Margin, end = PorchSpace.Margin, bottom = PorchSpace.PreviewBelow),
            )
        }
        Row(
            // Rows inset their content, so the columns start that much earlier and logos line up with the headings above.
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = PorchSpace.Margin - PorchSpace.TileGap),
            horizontalArrangement = Arrangement.spacedBy(PorchSpace.HomeColumnGap),
        ) {
            if (noGuide) {
                val yours = lists?.yours
                if (yours != null && yours.all.isNotEmpty()) {
                    HomeList(
                        YOURS, "Your channels", yours, "",
                        rowFocus, liveLabel = false, trailing = ::endsText,
                        firstRow = upToTabs, onRowFocused = onRowFocused, onPlay = playFrom, onHold = hold, onSeeAll = { onWhatsOn(NowChip.Favourites) },
                    )
                }
                NoGuide(
                    graph,
                    Modifier.weight(if (yours != null && yours.all.isNotEmpty()) 2f else 1f).fillMaxHeight(),
                    focus = Modifier
                        .focusRequester(rowFocus.requester(OpenGuide))
                        .onFocusChanged { if (it.hasFocus) rowFocus.gained(OpenGuide) }
                        .then(upToTabs),
                    onOpen = onGuide,
                    onSettings = onSettings,
                )
            } else {
                HomeList(
                    SPORT, "Live sport", lists?.sport, "No sport in the guide right now.",
                    rowFocus, liveLabel = true, trailing = { "ends ${formatTime(it.endMs)}" },
                    firstRow = upToTabs, onRowFocused = onRowFocused, onPlay = playFrom, onHold = hold, onSeeAll = { onWhatsOn(SportChip) },
                )
                HomeList(
                    YOURS, "Your channels", lists?.yours, "Hold OK on a channel to make it a favourite.",
                    rowFocus, liveLabel = false, trailing = ::endsText,
                    firstRow = upToTabs, onRowFocused = onRowFocused, onPlay = playFrom, onHold = hold, onSeeAll = { onWhatsOn(NowChip.Favourites) },
                )
                val followingNow = lists?.thirdIsFollowing == true
                val nowMs = rememberNow()
                HomeList(
                    SOON, if (followingNow) "Following" else "Starting soon", lists?.third, "Nothing starts in the next 30 minutes.",
                    rowFocus, liveLabel = false,
                    trailing = if (followingNow) { row -> HomeLists.followingTrailing(row, nowMs) } else { row -> formatTime(row.startMs) },
                    firstRow = upToTabs, onRowFocused = onRowFocused, onPlay = playFrom, onHold = hold, onSeeAll = { onWhatsOn(NowChip.All) },
                    marks = marks,
                )
            }
        }
        HintLine(exitWarning)
    }
}

private fun rowKey(list: Int, row: LiveRows.Row) = HomeKey(list, "row:${row.channels.first().id}")

/**
 * The pane, showing the row focus last rested on. Before any, or if that row has gone, the first row. The one place that
 * reads [focus], so a focus move recomposes this and not the page. Tabs and "See all" don't write it, so they keep the last one.
 */
@Composable
private fun FollowingPreview(
    focus: PreviewFocus,
    rows: Map<HomeKey, LiveRows.Row>,
    loadDetail: suspend (Long) -> ProgrammeDetail?,
    modifier: Modifier,
) {
    val row = focus.place?.let { rows[it] } ?: rows.values.firstOrNull()
    PreviewPane(row, loadDetail, modifier)
}

/** A programme on now says when it ends; a channel the guide knows nothing about says nothing. */
internal fun endsText(row: LiveRows.Row): String? = if (row.endMs > 0L) "ends ${formatTime(row.endMs)}" else null

@Composable
private fun HomeTopBar(selectedTab: FocusRequester, onChrome: () -> Unit, onTab: (String) -> Unit) {
    TopBar(
        title = "Hi $VIEWER_NAME",
        clock = formatHeaderClockLong(rememberNow()),
        tabs = TABS,
        current = "Home",
        onTab = onTab,
        modifier = Modifier.onFocusChanged { if (it.hasFocus) onChrome() },
        selectedTab = selectedTab,
    )
}

/**
 * One line at the top of Home while an import runs and Home has little to show ("Found 20,000 channels. Fetching the guide…"),
 * when the guide is out of date, or why a source has no channels. It reads the refresh status itself, so a progress tick
 * redraws this line and not the page.
 */
@Composable
private fun HomeNote(graph: AppGraph, thin: Boolean, outOfDate: Boolean, empty: Boolean) {
    val status by graph.sync.status.collectAsStateWithLifecycle()
    val running = graph.sync.progress(status).values.firstOrNull()
    val note = when {
        running != null && (thin || empty) -> running.homeLine()
        outOfDate -> "The guide is out of date. Channels still play, and Porch keeps trying."
        empty -> status.values.filterIsInstance<SyncStatus.Failed>().firstOrNull()?.message
        else -> null
    }
    if (note != null) {
        Text(
            note,
            style = PorchType.Body,
            color = PorchColors.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = PorchSpace.Margin, vertical = PorchSpace.ListGap),
        )
    }
}

/**
 * Home with no guide: one plain line and one way in, so the screen never looks empty. When the last refresh failed, the line
 * says why ("Guide address not found. Check it in Settings.") instead of "No guide yet".
 */
@Composable
private fun NoGuide(graph: AppGraph, modifier: Modifier, focus: Modifier, onOpen: () -> Unit, onSettings: () -> Unit) {
    val status by graph.sync.status.collectAsStateWithLifecycle()
    val failed = status.values.filterIsInstance<SyncStatus.Failed>().firstOrNull()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(
            failed?.message ?: "No guide yet. Your channels are all in the Full guide.",
            style = PorchType.Body,
            color = PorchColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(PorchSpace.TileGap))
        // A source that failed is fixed in Settings; the Full guide has no programmes to show for it.
        if (failed != null) Pill("Open Settings", modifier = focus, onClick = onSettings) else Pill("Open Full guide", modifier = focus, onClick = onOpen)
    }
}

/**
 * One list: heading, up to [HomeLists.ROWS] rows, then a quiet "See all" row when there are more.
 * An empty list keeps its place and says one plain line. [section] is null while the first load runs.
 * [firstRow] is added to the first row, so Up from it can reach the tabs.
 */
@Composable
private fun RowScope.HomeList(
    index: Int,
    title: String,
    section: HomeLists.Section?,
    emptyLine: String,
    rowFocus: RowFocus<HomeKey>,
    liveLabel: Boolean,
    trailing: (LiveRows.Row) -> String?,
    firstRow: Modifier,
    onRowFocused: (HomeKey) -> Unit,
    onPlay: (HomeLists.Section, LiveRows.Row) -> Unit,
    onHold: (HomeLists.Section, LiveRows.Row) -> Unit,
    onSeeAll: () -> Unit,
    /** Rows wear a bell for a set reminder; null for the lists that don't. */
    marks: ReminderMarks? = null,
) {
    Column(Modifier.weight(1f)) {
        Text(
            title,
            style = PorchType.Section,
            modifier = Modifier.padding(start = PorchSpace.TileGap, top = PorchSpace.HomeRowGap, bottom = PorchSpace.ListGap),
        )
        if (section == null) {
            // The first load: say so where the rows will be, instead of leaving a bare heading.
            Text("Loading your channels and guide…", style = PorchType.Body, color = PorchColors.Muted, maxLines = 2, modifier = Modifier.padding(start = PorchSpace.TileGap))
            return@Column
        }
        if (section.all.isEmpty()) {
            Text(emptyLine, style = PorchType.Body, color = PorchColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = PorchSpace.TileGap))
            return@Column
        }
        Column(verticalArrangement = Arrangement.spacedBy(PorchSpace.HomeRowGap)) {
            section.shown.forEachIndexed { position, row ->
                val id = row.channels.first().id
                // Keyed by channel: focus and the return from the player follow the channel when the order changes.
                key(id) {
                    val place = rowKey(index, row)
                    LiveRowItem(
                        row = row,
                        trailing = trailing(row),
                        liveLabel = liveLabel,
                        wideSlot = false,
                        modifier = Modifier.focusRequester(rowFocus.requester(place)).then(if (position == 0) firstRow else Modifier),
                        onFocused = { focused -> if (focused) onRowFocused(place) },
                        onOpenOthers = null,
                        onPlay = { onPlay(section, row) },
                        onLong = { onHold(section, row) },
                        compact = true,
                        reminder = marks?.has(row.channels, row.startMs) == true,
                    )
                }
            }
            if (section.more > 0) {
                val place = HomeKey(index, "see-all")
                SeeAllRow(
                    section.more,
                    Modifier.focusRequester(rowFocus.requester(place)).onFocusChanged { if (it.hasFocus) rowFocus.gained(place) },
                    onSeeAll,
                )
            }
        }
    }
}

/** The last row of a long list: quiet at rest, the same Focus fill as every other row when focused. */
@Composable
private fun SeeAllRow(more: Int, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(PorchSpace.HomeRowHeight),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(PorchRadius.Row)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = PorchColors.Muted,
            focusedContainerColor = PorchColors.Focus,
            focusedContentColor = PorchColors.OnFocus,
            pressedContainerColor = PorchColors.Focus,
            pressedContentColor = PorchColors.OnFocus,
        ),
        border = ClickableSurfaceDefaults.border(),
        scale = clickScale(PorchFocus.RowScale),
        glow = ClickableSurfaceDefaults.glow(),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = PorchSpace.TileGap), verticalAlignment = Alignment.CenterVertically) {
            Text("See all  ·  $more more", style = PorchType.TileTitle, maxLines = 1)
        }
    }
}

/** The hint bar. For two seconds after the first Back it says what a second Back does instead; only this redraws. */
@Composable
private fun HintLine(exitWarning: State<Boolean>) {
    val leaving by exitWarning
    Row(
        Modifier.fillMaxWidth().padding(horizontal = PorchSpace.Margin, vertical = PorchSpace.ListGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PorchSpace.HomeTilePad),
    ) {
        if (leaving) {
            Text("Press Back again to exit", style = PorchType.Hint)
        } else {
            OkKeycap()
            Text("plays   ·   Hold OK for options", style = PorchType.Hint)
        }
    }
}
