package tv.porch.app.ui.now

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.key.key
import tv.porch.app.domain.ProgrammeText
import tv.porch.app.domain.LiveRows
import tv.porch.app.domain.SportMatch
import tv.porch.app.ui.components.LocalReturnTile
import tv.porch.app.ui.components.WatchOnPanel
import tv.porch.app.ui.components.LiveRowItem
import tv.porch.app.ui.components.RowFocus
import tv.porch.app.ui.components.tryFocus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Text
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.porch.app.AppGraph
import tv.porch.app.data.pacedWhile
import tv.porch.app.domain.Channel
import tv.porch.app.domain.ChannelFold
import tv.porch.app.domain.GroupChoice
import tv.porch.app.domain.GroupKey
import tv.porch.app.domain.GuideCoverage
import tv.porch.app.domain.Kind
import tv.porch.app.domain.Kinds
import tv.porch.app.domain.NowChip
import tv.porch.app.domain.NowShelves
import tv.porch.app.domain.Programme
import tv.porch.app.domain.Tabs
import tv.porch.app.domain.formatHeaderClockLong
import tv.porch.app.domain.formatTime
import tv.porch.app.ui.VmFactory
import tv.porch.app.ui.components.LocalQuickMenu
import tv.porch.app.ui.components.OkKeycap
import tv.porch.app.R
import tv.porch.app.ui.components.Pill
import tv.porch.app.ui.components.iconRes
import tv.porch.app.ui.components.SectionHeader
import tv.porch.app.ui.components.TopBar
import tv.porch.app.player.CatchupRequest
import tv.porch.app.ui.components.rememberNow
import tv.porch.app.ui.theme.PorchColors
import tv.porch.app.ui.theme.PorchSpace
import tv.porch.app.ui.theme.PorchType

/** Which time window a shelf shows. */
enum class NowFilter(val title: String) { Live("Live now"), Hour("Next hour"), Tonight("Tonight") }

data class NowUi(
    val shelves: NowShelves.Lists = NowShelves.Lists(emptyList(), emptyList(), emptyList()),
    /** The Live now screen's rows, built off the main thread. */
    val rows: LiveRows.Screen = LiveRows.Screen(LiveRows.Live(emptyList(), 0, null), emptyList(), emptyList()),
    val loading: Boolean = true,
    /** True when the viewer has picked groups, so the "All groups" chip means something. */
    val hasChoice: Boolean = false,
    /** True while the "All groups" chip bypasses the choice. */
    val allGroups: Boolean = false,
    val chip: NowChip = NowChip.All,
    /** The chip last asked for, before a kind that is gone was dropped. The screen waits for this to match a "See all" chip. */
    val requested: NowChip = NowChip.All,
    /** The kinds the list has channels of; they are the chips after Favourites. */
    val kinds: List<Kind> = emptyList(),
)

/** The kind chips in the frame's order. */
private val SportChip = NowChip.OfKind(Kind.Sport)

private val ChipKinds = listOf(Kind.News, Kind.Sport, Kind.Movies, Kind.Kids, Kind.Music, Kind.Docs)

private data class Choice(val chosen: Set<GroupKey>, val allGroups: Boolean, val showWithoutGuide: Boolean, val chip: NowChip)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class NowViewModel(private val graph: AppGraph) : ViewModel() {
    private val allGroupsFlow = MutableStateFlow(false)
    private val chipFlow = MutableStateFlow<NowChip>(NowChip.All)
    private val choice = combine(
        graph.repository.observeChosenGroups(),
        allGroupsFlow,
        graph.guideFlags.showWithoutGuide,
        chipFlow,
    ) { chosen, allGroups, showWithoutGuide, chip -> Choice(chosen, allGroups, showWithoutGuide, chip) }

    init {
        // No live pick left: the "All groups" chip goes away, so forget it was on.
        viewModelScope.launch {
            combine(graph.repository.observeChosenGroups(), graph.repository.observeChannels()) { chosen, channels ->
                GroupChoice.live(chosen, channels).isEmpty()
            }.distinctUntilChanged().flowOn(Dispatchers.Default).collect { none -> if (none) allGroupsFlow.value = false }
        }
    }

    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }
    // Live and Next hour change with the clock, so they re-query on each tick. Tonight is a fixed window: once an hour.
    // The Sport chip looks three hours ahead instead of one, and that window is large, so it asks again every 5 minutes.
    // Focus moving across the chips passes through Sport; the flag settles for 200 ms before queries restart.
    private val sportChip = flow {
        emit(chipFlow.value == SportChip)
        emitAll(chipFlow.map { it == SportChip }.distinctUntilChanged().drop(1).debounce(200))
    }.distinctUntilChanged()
    private val liveAndHour = sportChip.flatMapLatest { sport ->
        val hourClock = if (sport) clock.distinctUntilChangedBy { it / 300_000L } else clock
        combine(
            clock.flatMapLatest { programmes(it, NowFilter.Live) },
            hourClock.flatMapLatest { programmes(it, NowFilter.Hour, sport) },
            clock,
        ) { live, hour, now -> Triple(now, live, hour) }
    }
    private val tonightWindow = clock.distinctUntilChangedBy { it / 3_600_000L }.flatMapLatest { programmes(it, NowFilter.Tonight) }
    val state = combine(
        graph.folds.listing,
        liveAndHour,
        tonightWindow,
        graph.repository.observeSources(),
        choice,
    ) { listing, (now, live, hour), tonight, sources, (chosen, allGroups, showWithoutGuide, pickedChip) ->
        val channels = listing.channels
        val offsets = sources.associate { it.id to it.guideOffsetMinutes }
        val picks = GroupChoice.live(chosen, channels)
        val mine = if (allGroups) channels else GroupChoice.filter(channels, picks)
        val noGuide = sources.filter { it.xmltvUrl.isNullOrBlank() }.map { it.id }.toSet()
        val covered = GuideCoverage.filter(mine, showWithoutGuide, noGuide)
        val kinds = Kinds.present(covered)
        // The picked kind is gone from this list: forget it, so it does not come back when it returns.
        val chip = if (pickedChip is NowChip.OfKind && pickedChip.kind !in kinds) NowChip.All else pickedChip
        if (chip != pickedChip) chipFlow.value = chip
        // One channel, one entry, so "+N channels" counts channels. Folded inside the chip, so a family's best copy can't be of another kind.
        // Only channels with guide data: a shelf lists none without, and an unmatched best copy would take its family's matched copy with it.
        val shelfChannels = ChannelFold.foldGuided(NowShelves.narrow(covered, chip), listing.families)
        val shelves = NowShelves.build(shelfChannels, live, hour, tonight, chip, offsets, now)
        val rows = when {
            chip == SportChip -> SportMatch.screen(shelves.live, NowShelves.upcoming(shelfChannels, hour, offsets, now), offsets)
            else -> LiveRows.build(shelves, offsets)
        }
        NowUi(
            shelves = shelves,
            rows = rows,
            loading = false,
            hasChoice = picks.isNotEmpty(),
            allGroups = allGroups && picks.isNotEmpty(),
            chip = chip,
            requested = pickedChip,
            kinds = kinds,
        )
    }
        .conflate()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NowUi())

    fun setChip(value: NowChip) {
        chipFlow.value = value
    }

    fun setAllGroups(value: Boolean) {
        allGroupsFlow.value = value
    }

    private fun programmes(now: Long, filter: NowFilter, sport: Boolean = false): Flow<List<Programme>> =
        programmeSource(now, filter, sport).pacedWhile(graph.sync::importing)

    private fun programmeSource(now: Long, filter: NowFilter, sport: Boolean) = when (filter) {
        NowFilter.Live -> graph.repository.observeOnNow(now)
        NowFilter.Hour -> graph.repository.observeStartingUntil(now, now + (if (sport) 3L else 1L) * 60L * 60L * 1000L)
        NowFilter.Tonight -> {
            val (from, to) = tonightBounds(now)
            graph.repository.observeStartingBetween(from, to)
        }
    }
}

fun tonightBounds(now: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
    val zoned = Instant.ofEpochMilli(now).atZone(zone)
    val evening = zoned.withHour(18).withMinute(0).withSecond(0).withNano(0)
    val midnight = zoned.toLocalDate().plusDays(1).atStartOfDay(zone)
    val from = if (zoned.isBefore(evening)) evening else zoned
    return from.toInstant().toEpochMilli() to midnight.toInstant().toEpochMilli()
}

private fun emptyLine(filter: NowFilter): String = when (filter) {
    NowFilter.Live -> "Nothing on in this list right now."
    NowFilter.Hour -> "Nothing starting in the next hour."
    NowFilter.Tonight -> "Nothing tonight."
}

@Composable
fun NowScreen(
    graph: AppGraph,
    onTab: (String) -> Unit,
    onPlay: (List<Channel>, Channel) -> Unit,
    /** Plays a programme as catch-up, from the details sheet. */
    onWatch: (List<Channel>, Channel, CatchupRequest?, Channel?) -> Unit,
    /** The chip Home's "See all" asked for. Null leaves whatever chip the viewer last chose. */
    startChip: NowChip? = null,
) {
    val model: NowViewModel = viewModel(factory = VmFactory { NowViewModel(graph) })
    val ui by model.state.collectAsStateWithLifecycle()
    // A "See all" chip is applied once. Saved with the route, so coming back from the player does not snap the chip back.
    var applied by rememberSaveable { mutableStateOf(startChip == null) }
    remember { if (!applied && startChip != null) model.setChip(startChip) }
    // The view model outlives the screen, so its first state can still be the last chip's rows. Hold the list until
    // the state answers the chip that was asked for (or 1.5 s, if it never matches).
    val waiting = !applied && startChip != null && ui.requested != startChip
    LaunchedEffect(waiting) { if (!applied && !waiting) applied = true }
    LaunchedEffect(Unit) {
        delay(1_500)
        applied = true
    }
    val quick = LocalQuickMenu.current
    val holdScope = rememberCoroutineScope()
    // Hold OK: the programme is a row away in the database; the menu opens as soon as it answers.
    val hold: (LiveRows.Row, List<LiveRows.Row>) -> Unit = { row, section ->
        val channel = row.channels.first()
        val zap = section.map { it.channels.first() }.distinctBy { it.id }
        holdScope.launch {
            val programme = if (row.programmeId != 0L) graph.repository.programme(row.programmeId) else null
            quick.open(channel, programme = programme, onWatch = { catchup, copy -> onWatch(zap, channel, catchup, copy) })
        }
    }
    val returnTile = LocalReturnTile.current
    val selectedChip = remember { FocusRequester() }
    val rowFocus = remember { RowFocus<String>() }
    val listState = rememberLazyListState()
    // Coming back from the player, landsOnReturn puts focus on the row the viewer left; start-up focus must not fight it.
    val returning = remember { returnTile.armed }
    var panel by remember { mutableStateOf<LiveRows.Row?>(null) }
    var listFocused by remember { mutableStateOf(false) }
    // The key of the focused row if it has other channels. Only that row can clear it, so a new row's first report cannot.
    var othersKey by remember { mutableStateOf<String?>(null) }

    val shownRows = if (waiting) NoRows else ui.rows
    val loadingNow = ui.loading || waiting
    val live = shownRows.live
    val hour = shownRows.hour
    val tonight = shownRows.tonight
    val hasRows = live.rows.isNotEmpty() || hour.isNotEmpty() || tonight.isNotEmpty()
    // The Sport chip turns the sections into Live now and Next 3 hours, and the rows into matches first.
    val sport = ui.chip == SportChip
    val order = remember(live, hour, tonight) {
        live.rows.map { "live:${it.key}" } + hour.map { "hour:${it.key}" } + tonight.map { "tonight:${it.key}" }
    }

    // A new chip starts at the top of its list.
    val shownChip = remember { arrayOf(ui.chip) }
    LaunchedEffect(ui.chip) {
        if (shownChip[0] != ui.chip) {
            shownChip[0] = ui.chip
            listState.scrollToItem(0)
        }
    }

    // Focus starts on the first row once the guide has loaded, or on the selected chip when there are no rows.
    // It never pulls focus back from the chips or the tabs.
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(loadingNow, hasRows) {
        if (!placed && !loadingNow) {
            withFrameNanos { }
            if (returning) {
                withFrameNanos { }
                if (!returnTile.armed) {
                    placed = true
                    return@LaunchedEffect
                }
            }
            if (!rowFocus.inChrome && !rowFocus.anyRowFocused) focusFirstOrChip(rowFocus, listState, selectedChip, order)
            placed = true
        }
    }
    // The row that had focus is gone (its programme ended): put focus on the nearest row, else the first, else the chip.
    LaunchedEffect(order) {
        withFrameNanos { }
        withFrameNanos { }
        val lost = rowFocus.lostRow(order)
        val target = rowFocus.settle(order)
        if (lost && !(target != null && rowFocus.requester(target).tryFocus())) {
            focusFirstOrChip(rowFocus, listState, selectedChip, order)
        }
    }
    // Rows are far apart in a long list, so Back is the way to the chips. A second Back leaves the screen.
    BackHandler(enabled = listFocused) { selectedChip.tryFocus() }
    val onRowFocus: (String, Boolean, Boolean) -> Unit = { key, focused, others ->
        if (focused) {
            rowFocus.gained(key)
            othersKey = key.takeIf { others }
        } else if (othersKey == key) {
            othersKey = null
        }
    }
    val onChrome: () -> Unit = { rowFocus.chrome() }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = "What's on now",
            clock = formatHeaderClockLong(rememberNow()),
            tabs = Tabs.labels,
            current = "What's on now",
            onTab = onTab,
            modifier = Modifier.onFocusChanged { if (it.hasFocus) onChrome() },
        )
        Spacer(Modifier.height(PorchSpace.FilterGap))
        ChipRow(ui, model, selectedChip, onChrome)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = PorchSpace.Margin).padding(top = PorchSpace.NowShelfGap),
            verticalAlignment = Alignment.Bottom,
        ) {
            SectionHeader("Live now", live.commonEnd?.let { "Most shows end ${formatTime(it)}" })
            Spacer(Modifier.weight(1f))
            if (!loadingNow) {
                Text("${live.rows.size} shows", style = PorchType.Meta)
            }
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = PorchSpace.Margin, vertical = PorchSpace.FocusRoom),
            verticalArrangement = Arrangement.spacedBy(PorchSpace.ListGap),
            modifier = Modifier.weight(1f).onFocusChanged { listFocused = it.hasFocus },
        ) {
            if (loadingNow && !hasRows) item(key = "loading") { EmptyRows("Loading what's on…") }
            // Sport with nothing at all says so once; if nothing is on for any sport channel the usual line applies.
            val noSport = sport && hour.isEmpty() && ui.shelves.live.isNotEmpty()
            if (live.rows.isEmpty() && !loadingNow) item(key = "empty-live") { EmptyRows(if (noSport) "No sport in the guide right now." else emptyLine(NowFilter.Live)) }
            sectionRows("live", live.rows, rowFocus, order.firstOrNull(), selectedChip, live.commonEnd, sport,
                onFocused = onRowFocus, onOpenOthers = { panel = it },
                onPlay = { row -> onPlay(live.rows.map { it.channels.first() }.distinctBy { it.id }, row.channels.first()) }, onLong = { hold(it, live.rows) })
            if (hour.isNotEmpty() || (!loadingNow && !(sport && live.rows.isEmpty()))) {
                item(key = "head-hour") { SectionTitle(if (sport) "Next 3 hours" else NowFilter.Hour.title) }
                if (hour.isEmpty()) item(key = "empty-hour") { EmptyRows(if (sport) "No sport starting in the next 3 hours." else emptyLine(NowFilter.Hour)) }
            }
            sectionRows("hour", hour, rowFocus, order.firstOrNull(), selectedChip, null, sport,
                onFocused = onRowFocus, onOpenOthers = { panel = it },
                onPlay = { row -> onPlay(hour.map { it.channels.first() }.distinctBy { it.id }, row.channels.first()) }, onLong = { hold(it, hour) })
            if (!sport && (tonight.isNotEmpty() || !loadingNow)) {
                item(key = "head-tonight") { SectionTitle(NowFilter.Tonight.title) }
                if (tonight.isEmpty()) item(key = "empty-tonight") { EmptyRows(emptyLine(NowFilter.Tonight)) }
            }
            sectionRows("tonight", tonight, rowFocus, order.firstOrNull(), selectedChip, null, sport,
                onFocused = onRowFocus, onOpenOthers = { panel = it },
                onPlay = { row -> onPlay(tonight.map { it.channels.first() }.distinctBy { it.id }, row.channels.first()) }, onLong = { hold(it, tonight) })
        }
        HintLine { othersKey != null }
    }
    panel?.let { row ->
        WatchOnPanel(
            title = ProgrammeText.title(row.title),
            channels = row.channels,
            onPlay = { channel ->
                panel = null
                // Up and Down keep zapping through the whole list, with this copy of the channel in its row's place.
                val section = listOf(live.rows, hour, tonight).firstOrNull { row in it } ?: listOf(row)
                onPlay(section.map { if (it == row) channel else it.channels.first() }.distinctBy { it.id }, channel)
            },
            onClose = { panel = null },
        )
    }
}

/** Focus on the first row, scrolling to the top if it is not composed; the selected chip if there is no row. */
private suspend fun focusFirstOrChip(rowFocus: RowFocus<String>, listState: LazyListState, chip: FocusRequester, order: List<String>) {
    val first = order.firstOrNull()
    if (first != null) {
        if (rowFocus.requester(first).tryFocus()) return
        listState.scrollToItem(0)
        withFrameNanos { }
        if (rowFocus.requester(first).tryFocus()) return
    }
    chip.tryFocus()
}

private val NoRows = LiveRows.Screen(LiveRows.Live(emptyList(), 0, null), emptyList(), emptyList())

/**
 * One section's rows. [firstKey] is the very first row on the screen's key (it gets Up to the chips).
 * [commonEnd] set means a Live row, which shows its end time only when it differs; null means a later row, which shows its start.
 */
private fun LazyListScope.sectionRows(
    prefix: String,
    rows: List<LiveRows.Row>,
    rowFocus: RowFocus<String>,
    firstKey: String?,
    chip: FocusRequester,
    commonEnd: Long?,
    sport: Boolean,
    onFocused: (key: String, focused: Boolean, hasOthers: Boolean) -> Unit,
    onOpenOthers: (LiveRows.Row) -> Unit,
    onPlay: (LiveRows.Row) -> Unit,
    onLong: (LiveRows.Row) -> Unit,
) {
    val isLive = prefix == "live"
    itemsIndexed(rows, key = { _, row -> "$prefix:${row.key}" }) { _, row ->
        val key = "$prefix:${row.key}"
        val trailing = when {
            !isLive -> formatTime(row.startMs)
            !LiveRows.endWorthShowing(row.endMs, commonEnd) -> null
            else -> "ends ${formatTime(row.endMs)}"
        }
        var modifier: Modifier = Modifier.focusRequester(rowFocus.requester(key))
        if (key == firstKey) modifier = modifier.focusProperties { up = chip }
        LiveRowItem(
            row = row,
            trailing = trailing,
            liveLabel = sport && isLive && row.match,
            wideSlot = sport,
            modifier = modifier,
            onFocused = { focused -> onFocused(key, focused, row.channels.size > 1) },
            onOpenOthers = { onOpenOthers(row) },
            onPlay = { onPlay(row) },
            onLong = { onLong(row) },
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Column(Modifier.padding(top = PorchSpace.NowShelfGap)) { SectionHeader(title) }
}

@Composable
private fun EmptyRows(text: String) {
    Text(text, color = PorchColors.Muted, style = PorchType.Body, modifier = Modifier.padding(vertical = PorchSpace.TileGap))
}

/** All, Favourites, then the kinds. Selection follows focus, like a tab row. "All groups" only toggles. */
@Composable
private fun ChipRow(ui: NowUi, model: NowViewModel, selectedChip: FocusRequester, onChrome: () -> Unit) {
    val chips = listOf<Pair<String, NowChip>>(
        "All" to NowChip.All,
        "Favourites" to NowChip.Favourites,
    ) + ChipKinds.filter { it in ui.kinds }.map { it.label to NowChip.OfKind(it) }
    // Every chip has to be reachable, however many kinds the list has. Margin on the start only: padding on the end would make a row
    // that fits the screen still scroll by that much, and the first chip would be cut. When the chips are too many they leave at the screen's edge.
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(start = PorchSpace.Margin, top = PorchSpace.FocusRoom, bottom = PorchSpace.FocusRoom)
            .onFocusChanged { if (it.hasFocus) onChrome() },
        horizontalArrangement = Arrangement.spacedBy(PorchSpace.FilterGap),
    ) {
        for ((label, chip) in chips) {
            val selected = ui.chip == chip
            Pill(
                label,
                selected = selected,
                chip = true,
                icon = when (chip) {
                    is NowChip.OfKind -> chip.kind.iconRes()
                    NowChip.Favourites -> R.drawable.ic_favourite
                    NowChip.All -> R.drawable.ic_live
                },
                modifier = (if (selected) Modifier.focusRequester(selectedChip) else Modifier)
                    .onFocusChanged { if (it.hasFocus) model.setChip(chip) },
            ) { model.setChip(chip) }
        }
        if (ui.hasChoice) {
            Pill("All groups", selected = ui.allGroups, chip = true) { model.setAllGroups(!ui.allGroups) }
        }
    }
}

/** The right part is left out when the focused row has no other channels. */
@Composable
private fun HintLine(focusedHasOthers: () -> Boolean) {
    val others = if (focusedHasOthers()) "Right shows the other channels   ·   " else ""
    Row(
        Modifier.fillMaxWidth().padding(horizontal = PorchSpace.Margin, vertical = PorchSpace.TileGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PorchSpace.HomeTilePad),
    ) {
        OkKeycap()
        Text("plays   ·   ${others}Back goes to the filters", style = PorchType.Hint)
    }
}
