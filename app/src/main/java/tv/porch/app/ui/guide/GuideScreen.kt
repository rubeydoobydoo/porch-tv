package tv.porch.app.ui.guide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Text
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.porch.app.AppGraph
import tv.porch.app.R
import tv.porch.app.data.SourceEntity
import tv.porch.app.data.pacedWhile
import tv.porch.app.domain.AllDayText
import tv.porch.app.domain.ChannelNames
import tv.porch.app.domain.Channel
import tv.porch.app.domain.ChannelFamilies
import tv.porch.app.domain.ChannelFold
import tv.porch.app.domain.GuideChip
import tv.porch.app.domain.GuideChips
import tv.porch.app.domain.GuideHeroText
import tv.porch.app.domain.GuideFilterRule
import tv.porch.app.domain.Kind
import tv.porch.app.domain.Kinds
import tv.porch.app.domain.GroupChoice
import tv.porch.app.domain.GroupKey
import tv.porch.app.domain.GuideClock
import tv.porch.app.domain.GuideCoverage
import tv.porch.app.domain.Programme
import tv.porch.app.domain.catchupAllowed
import tv.porch.app.domain.formatGuideDay
import tv.porch.app.domain.formatHourMinute
import tv.porch.app.domain.formatTime
import tv.porch.app.player.CatchupRequest
import tv.porch.app.ui.VmFactory
import tv.porch.app.ui.components.ChannelLogo
import tv.porch.app.ui.components.FavouriteStar
import tv.porch.app.ui.components.ReminderBell
import tv.porch.app.ui.components.ReminderMarks
import tv.porch.app.ui.components.rememberReminderMarks
import tv.porch.app.ui.components.LocalQuickMenu
import tv.porch.app.ui.components.QuickMenuState
import tv.porch.app.ui.components.HintBar
import tv.porch.app.ui.components.iconRes
import tv.porch.app.ui.components.Pill
import tv.porch.app.ui.theme.PorchColors
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.key
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import tv.porch.app.domain.GuideCellText
import tv.porch.app.domain.GuideWindow
import tv.porch.app.domain.Tabs
import tv.porch.app.domain.formatGuideDayLong
import tv.porch.app.domain.formatHeaderClockLong
import tv.porch.app.ui.components.TopBar
import tv.porch.app.ui.components.rememberNow
import tv.porch.app.ui.home.PreviewFrame
import tv.porch.app.ui.home.PreviewStatusLine
import tv.porch.app.ui.home.PreviewWords
import tv.porch.app.ui.home.channelColor
import tv.porch.app.domain.ProgrammeDetail
import tv.porch.app.ui.theme.PorchRadius
import tv.porch.app.ui.theme.PorchSpace
import tv.porch.app.ui.theme.PorchType

private const val MAX_ROWS = 6
private const val MARKS = 6
private const val WINDOW_MS = 3L * 60L * 60L * 1000L

private data class Listing(
    val sources: List<SourceEntity>,
    val channels: List<Channel>,
    val chosen: Set<GroupKey>,
    val showWithoutGuide: Boolean,
    val noGuideSources: Set<Long>,
    /** Each channel's kind, worked out off the main thread once per emission. */
    val kindOf: Map<Long, Kind>,
    val families: ChannelFamilies,
)

/** One chip's list and what depends on it, worked out off the main thread. */
private class BuiltList(
    val chips: List<GuideChip>,
    val filter: String,
    val channels: List<Channel>,
    val hasFavourites: Boolean,
    val emptyNeedsGuide: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class GuideViewModel(private val graph: AppGraph) : ViewModel() {
    var sources by mutableStateOf<List<SourceEntity>>(emptyList())
        private set
    var sourceId by mutableStateOf<Long?>(null)
        private set
    var filter by mutableStateOf("fav")
        private set
    /** The one row of chips: Favourites, countries, kinds, All. */
    var chips by mutableStateOf<List<GuideChip>>(emptyList())
        private set
    var channels by mutableStateOf<List<Channel>>(emptyList())
        private set
    var channelIndex by mutableStateOf(0)
        private set
    var anchor by mutableStateOf(0)
        private set
    var windowStart by mutableStateOf(snap(System.currentTimeMillis() - 20 * 60_000))
        private set
    var focusTime by mutableStateOf(System.currentTimeMillis())
        private set
    var programmes by mutableStateOf<List<Programme>>(emptyList())
        private set
    var offset by mutableStateOf(0)
        private set
    var generation by mutableStateOf(0L)
        private set
    /** False until the programmes for this source and guide generation have first come back, so the hero never calls a channel empty while they load. */
    var programmesLoaded by mutableStateOf(false)
        private set
    private var programmesKey: Pair<Long?, Long>? = null
    var message by mutableStateOf<String?>(null)
        private set
    /** True when the list is empty only because channels without guide data are hidden. */
    var emptyNeedsGuide by mutableStateOf(false)
        private set
    var now by mutableStateOf(System.currentTimeMillis())
        private set
    /** True when at least one favourite is on this source's covered channels, so the Favourites list is not empty. */
    var hasFavourites by mutableStateOf(false)
        private set
    /** How many rows fit under the filters; the screen measures this. */
    var pageRows by mutableStateOf(MAX_ROWS)
        private set

    /** False until the first listing has arrived, so the screen can say it is loading rather than empty. */
    var loaded by mutableStateOf(false)
        private set

    private var pickedSource = false
    private var repicked = false
    private var viewerPickedFilter = false
    private var onNowKeys = emptySet<Pair<Long, String>>()
    private var all = emptyList<Channel>()
    private var chosen = emptySet<GroupKey>()
    private var showWithoutGuide = false
    private var noGuideSources = emptySet<Long>()
    private var kindOf = emptyMap<Long, Kind>()
    private var families = ChannelFamilies.None
    private var rebuildJob: Job? = null
    // A newer rebuild replaces one still working, so the reset it was asked for has to carry over.
    private var resetWanted = false
    private var listingArrived = false
    private val tvCountry: String = Locale.getDefault().country
    private var loadJob: Job? = null
    private var primed = false

    init {
        viewModelScope.launch {
            while (true) {
                now = System.currentTimeMillis()
                delay(15_000)
            }
        }
        viewModelScope.launch {
            // Which channels have something on now, asked again each half minute, so the list can put them first.
            snapshotFlow { now / 30_000L }.distinctUntilChanged().flatMapLatest {
                graph.repository.observeOnNow(System.currentTimeMillis()).pacedWhile(graph.sync::importing)
            }.map { rows -> rows.map { it.sourceId to it.channelKey }.toSet() }
                .distinctUntilChanged().flowOn(Dispatchers.Default).collect {
                    onNowKeys = it
                    rebuild(resetIndex = false)
                }
        }
        viewModelScope.launch {
            combine(
                graph.repository.observeSources(),
                graph.folds.listing,
                graph.repository.observeChosenGroups(),
                graph.guideFlags.showWithoutGuide,
            ) { sources, listing, chosen, showWithoutGuide ->
                Listing(
                    sources,
                    listing.channels,
                    GroupChoice.live(chosen, listing.channels),
                    showWithoutGuide,
                    sources.filter { it.xmltvUrl.isNullOrBlank() }.map { it.id }.toSet(),
                    Kinds.indexInParallel(listing.channels),
                    listing.families,
                )
            }.conflate().flowOn(Dispatchers.Default).collect { (nextSources, nextChannels, nextChosen, nextShow, nextNoGuide, nextKinds, nextFamilies) ->
                sources = nextSources
                all = nextChannels
                chosen = nextChosen
                showWithoutGuide = nextShow
                noGuideSources = nextNoGuide
                kindOf = nextKinds
                families = nextFamilies
                // The default source is the first with channels. Once, if that default turns out empty, another is tried
                // unless the player has picked a source.
                fun withChannels() = nextSources.firstOrNull { source -> nextChannels.any { it.sourceId == source.id } } ?: nextSources.firstOrNull()
                if (sourceId == null || nextSources.none { it.id == sourceId }) {
                    sourceId = withChannels()?.id
                    primed = false
                } else if (!pickedSource && !repicked && nextChannels.none { it.sourceId == sourceId }) {
                    repicked = true
                    withChannels()?.takeIf { it.id != sourceId }?.let {
                        sourceId = it.id
                        primed = false
                    }
                }
                rebuild(resetIndex = !primed)
                primed = true
                listingArrived = true
            }
        }
    }

    fun selectSource(id: Long) {
        pickedSource = true
        sourceId = id
        filter = "fav"
        viewerPickedFilter = false
        rebuild(resetIndex = true)
    }

    fun selectFilter(value: String) {
        filter = value
        viewerPickedFilter = true
        rebuild(resetIndex = true)
    }

    fun fitRows(rows: Int) {
        val fit = rows.coerceIn(1, MAX_ROWS)
        if (fit == pageRows) return
        pageRows = fit
        if (channelIndex >= anchor + fit) anchor = channelIndex - fit + 1
        reload()
    }

    fun moveChannel(delta: Int): Boolean {
        if (channels.isEmpty()) return false
        if (delta < 0 && channelIndex == 0) return false
        if (delta > 0 && channelIndex == channels.lastIndex) return true
        channelIndex = (channelIndex + delta).coerceIn(0, channels.lastIndex)
        if (channelIndex < anchor) anchor = channelIndex
        if (channelIndex >= anchor + pageRows) anchor = channelIndex - pageRows + 1
        reload()
        return true
    }

    fun moveTime(direction: Int) {
        val channel = channels.getOrNull(channelIndex) ?: return
        val current = programmeAt(channel, focusTime)
        focusTime = if (direction > 0) {
            if (current != null) GuideClock.shift(current.stopUtc, offset) + 1_000 else focusTime + 30 * 60_000
        } else {
            if (current != null) GuideClock.shift(current.startUtc, offset) - 1_000 else focusTime - 30 * 60_000
        }
        val margin = 15 * 60_000L
        if (focusTime < windowStart + margin) windowStart = snap(focusTime - 20 * 60_000)
        if (focusTime > windowStart + WINDOW_MS - margin) windowStart = snap(focusTime - WINDOW_MS + 40 * 60_000)
        reload()
    }

    fun jumpToNow() {
        now = System.currentTimeMillis()
        focusTime = now
        windowStart = snap(now - 20 * 60_000)
        message = null
        reload()
    }

    fun say(text: String) {
        message = text
    }

    fun visible(): List<Channel> = channels.subList(anchor.coerceIn(0, channels.size), (anchor + pageRows).coerceIn(0, channels.size))

    fun programmeAt(channel: Channel, time: Long = focusTime): Programme? {
        val key = channel.matchedXmltvId ?: return null
        val list = programmes.filter { it.sourceId == channel.sourceId && it.channelKey == key }.sortedBy { it.startUtc }
        return list.firstOrNull {
            val start = GuideClock.shift(it.startUtc, offset)
            val stop = GuideClock.shift(it.stopUtc, offset)
            time in start until stop
        } ?: list.firstOrNull { GuideClock.shift(it.startUtc, offset) > time }
    }

    private fun rebuild(resetIndex: Boolean) {
        resetWanted = resetWanted || resetIndex
        val source = sources.firstOrNull { it.id == sourceId }
        offset = source?.guideOffsetMinutes ?: 0
        generation = source?.guideGeneration ?: 0L
        // A new source or a new guide download starts the wait again. Scrolling and filters do not.
        if (programmesKey != (sourceId to generation)) programmesLoaded = false
        val wantedFilter = filter
        val picked = viewerPickedFilter
        val forSource = sourceId
        rebuildJob?.cancel()
        rebuildJob = viewModelScope.launch {
            val built = withContext(Dispatchers.Default) { buildList(forSource, wantedFilter, picked) }
            show(built)
        }
    }

    /** The heavy part of a rebuild: filter, fold and sort up to 49,000 channels. It only reads, and stops between stages once a newer rebuild has cancelled it. */
    private fun CoroutineScope.buildList(forSource: Long?, wantedFilter: String, viewerPicked: Boolean): BuiltList {
        val onSource = forSource?.let { id -> all.filter { it.sourceId == id } }.orEmpty()
        val picked = GroupChoice.filter(onSource, chosen)
        val coveredPicked = GuideCoverage.filter(picked, showWithoutGuide, noGuideSources)
        val kindLookup = { channel: Channel -> kindOf[channel.id] }
        // Chips come from what All lists, so a country or kind with nothing to show has no chip.
        val nextChips = GuideChips.build(coveredPicked, tvCountry, kindLookup)
        ensureActive()
        val chipKeys = nextChips.map { it.key }
        // A country or kind chip that has gone falls back to All.
        val gone = (GuideChips.isCountry(wantedFilter) || GuideChips.isKind(wantedFilter)) && wantedFilter !in chipKeys
        val nextFilter = GuideFilterRule.resolve(if (gone) GuideChips.ALL else wantedFilter, viewerPicked, chipKeys, coveredPicked.size, coveredPicked.any { it.favourite })
        // Folded inside the chip, so a family's best copy can't hide the one that belongs under this chip.
        val listed = ChannelFold.fold(GuideCoverage.listed(onSource, chosen, nextFilter, showWithoutGuide, noGuideSources, kindLookup), families)
        ensureActive()
        val onNowIds = listed.filter { (it.matchedXmltvId?.let { key -> it.sourceId to key }) in onNowKeys }.mapTo(HashSet()) { it.id }
        val ordered = GuideChips.order(listed, nextFilter, tvCountry, onNowIds)
        return BuiltList(
            chips = nextChips,
            filter = nextFilter,
            channels = ordered,
            hasFavourites = coveredPicked.any { it.favourite },
            emptyNeedsGuide = ordered.isEmpty() && !showWithoutGuide && GuideCoverage.hidesUnmatched(nextFilter) && GuideCoverage.view(picked, nextFilter, kindLookup).isNotEmpty(),
        )
    }

    private fun show(built: BuiltList) {
        val selectedId = channels.getOrNull(channelIndex)?.id
        chips = built.chips
        filter = built.filter
        hasFavourites = built.hasFavourites
        channels = built.channels
        emptyNeedsGuide = built.emptyNeedsGuide
        if (resetWanted || channels.isEmpty()) {
            channelIndex = 0
            anchor = 0
        } else {
            // Keep the cursor on the same channel when the order shifts under it (a programme rolled over), or on the copy that now stands for it.
            val kept = if (selectedId != null) placeOf(selectedId) else -1
            channelIndex = if (kept >= 0) kept else channelIndex.coerceIn(0, channels.lastIndex)
            if (channelIndex < anchor) anchor = channelIndex
            if (channelIndex >= anchor + pageRows) anchor = channelIndex - pageRows + 1
        }
        resetWanted = false
        reload()
        // Last, and in the same frame as the list: "loaded" with no list yet would draw the empty panel and move focus.
        if (listingArrived) loaded = true
    }

    private fun placeOf(id: Long): Int {
        val same = channels.indexOfFirst { it.id == id }
        if (same >= 0) return same
        val family = families.copiesOf(id).mapTo(HashSet()) { it.id }
        return if (family.isEmpty()) -1 else channels.indexOfFirst { it.id in family }
    }

    /** The other copies of a channel, best first. */
    fun otherCopies(channel: Channel): List<Channel> = families.othersOf(channel.id)

    /** The copy that can play a programme from [start] as catch-up: this one first. Folding must not lose catch-up when only another copy keeps recordings. */
    fun recordedCopy(channel: Channel, start: Long, now: Long): Channel? =
        (listOf(channel) + otherCopies(channel)).firstOrNull { it.xtreamStreamId != null && catchupAllowed(it.archiveDays, start, now) }

    fun keepsRecordings(channel: Channel): Boolean = channel.archiveDays > 0 || otherCopies(channel).any { it.archiveDays > 0 }

    private fun reload() {
        val id = sourceId ?: return
        val keys = visible().mapNotNull { it.matchedXmltvId }.distinct()
        val start = windowStart
        val gen = generation
        val off = offset
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            programmes = graph.repository.programmesFor(id, gen, keys, start, start + WINDOW_MS, off)
            if (id == sourceId && gen == generation) {
                programmesKey = id to gen
                programmesLoaded = true
            }
        }
    }

    private fun snap(epoch: Long): Long {
        val zoned = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault())
        val back = zoned.minute % 30
        return zoned.minusMinutes(back.toLong()).withSecond(0).withNano(0).toInstant().toEpochMilli()
    }
}

@Composable
fun GuideScreen(
    graph: AppGraph,
    onTab: (String) -> Unit,
    /** The list, the channel in it, a catch-up to play, and another copy of the channel to play in its place. */
    onPlay: (List<Channel>, Channel, CatchupRequest?, Channel?) -> Unit,
) {
    val model: GuideViewModel = viewModel(factory = VmFactory { GuideViewModel(graph) })
    val grid = remember { FocusRequester() }
    val firstChip = remember { FocusRequester() }
    var gridFocused by remember { mutableStateOf(false) }
    var filtersFocused by remember { mutableStateOf(false) }
    // True once the player has pressed any key here; until then focus is ours to place.
    var userMoved by remember { mutableStateOf(false) }
    // Up from the grid's top row, and Back from the grid, come to the chip for the current list.
    val chipRow = remember { FocusRequester() }
    val hasGrid = model.channels.isNotEmpty()
    // The first time the grid appears, focus goes to its live cell unless the player has already moved.
    // With no grid, focus goes to the first chip, but never pulls focus off the filters.
    LaunchedEffect(hasGrid, model.loaded) {
        if (!model.loaded) return@LaunchedEffect
        withFrameNanos { }
        if (hasGrid) {
            if (!userMoved) runCatching { grid.requestFocus() }
        } else if (!filtersFocused) {
            runCatching { firstChip.requestFocus() }
        }
    }
    LaunchedEffect(model.message) {
        if (model.message != null) {
            delay(4_000)
            model.say("")
        }
    }
    // Up walks one row at a time, so from deep in a long list Back is the way to the chips. A second Back leaves the guide.
    BackHandler(enabled = gridFocused) { runCatching { chipRow.requestFocus() } }
    val now = rememberNow()
    val channel = model.channels.getOrNull(model.channelIndex)
    Column(
        Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown) userMoved = true
            false
        },
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                TopBar(
                    title = "Guide",
                    clock = formatHeaderClockLong(now),
                    tabs = Tabs.labels,
                    current = "Full guide",
                    onTab = onTab,
                )
                GuidePreview(model, channel, graph.repository::programmeDetail)
                Spacer(Modifier.height(PorchSpace.GuideGap))
            }
        }
        // No side margin on the column: the chips scroll off the screen's edge, not off a cut line inside the margin.
        Column(Modifier.onFocusChanged { filtersFocused = it.hasFocus }) {
            var first = true
            // The chip for the current list is where Up from the grid, or Back, comes back to.
            fun chip(current: Boolean = false): Modifier {
                var modifier: Modifier = if (first) Modifier.focusRequester(firstChip) else Modifier
                if (current) modifier = modifier.focusRequester(chipRow)
                first = false
                return modifier
            }
            // Jump to now is the first pill in the row, so nothing is pinned over the chips. The row scrolls off the screen's edge.
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(PorchSpace.GuideChipRow)
                    .focusGroup()
                    .horizontalScroll(rememberScrollState())
                    // The first pill starts at the margin.
                    .padding(start = PorchSpace.Margin),
                horizontalArrangement = Arrangement.spacedBy(PorchSpace.FilterGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Pill("Jump to now", chip = true, lift = false) {
                    model.jumpToNow()
                    runCatching { grid.requestFocus() }
                }
                if (model.sources.size > 1) {
                    model.sources.forEach { source ->
                        Pill(source.name, selected = source.id == model.sourceId, modifier = chip(), chip = true, lift = false) { model.selectSource(source.id) }
                    }
                }
                model.chips.forEach { chip ->
                    Pill(
                        chip.label,
                        selected = model.filter == chip.key,
                        modifier = chip(model.filter == chip.key),
                        icon = chipIcon(chip.key),
                        chip = true,
                        lift = false,
                    ) { model.selectFilter(chip.key) }
                }
            }
        }
        Spacer(Modifier.height(PorchSpace.GuideGap))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = PorchSpace.Margin)) {
            val rows = ((maxHeight - PorchSpace.GuideHeaderHeight + PorchSpace.GuideRowGap) /
                (PorchSpace.GuideRowHeight + PorchSpace.GuideRowGap)).toInt()
            LaunchedEffect(rows) { model.fitRows(rows) }
            if (!model.loaded) {
                // Nothing yet: neither a grid nor the empty panel, so a slow first load never reads as "no channels".
            } else if (hasGrid) {
                GuideGrid(model, grid, gridFocused, chipRow, rememberReminderMarks(graph), onFocus = { gridFocused = it }, onPlay = onPlay)
            } else {
                EmptyGuide(model)
            }
        }
        // Back leaves the screen from the chips; from the grid it goes up to them first.
        val back = if (gridFocused) "Back goes to the filters" else "Back goes to the lists"
        val line = model.message?.takeIf { it.isNotBlank() } ?: "Left / Right move in time     Up / Down change channel     OK plays     $back"
        HintBar(line, "")
    }
}

/** Icons as on What's on now: a heart, a live dot and one per kind. A country chip has none. */
private fun chipIcon(key: String): Int? = when {
    key == GuideChips.FAV -> R.drawable.ic_favourite
    key == GuideChips.ALL -> R.drawable.ic_live
    GuideChips.isKind(key) -> Kind.valueOf(key.removePrefix(GuideChips.KIND_PREFIX)).iconRes()
    else -> null
}

/**
 * Home's preview pane, on the programme under the focused cell. Not focusable; it follows focus with Home's settle delay.
 * While programmes load it shows the channel's logo and name only. With an empty list, the list's name and what other lists have channels.
 */
@Composable
private fun GuidePreview(model: GuideViewModel, channel: Channel?, loadDetail: suspend (Long) -> ProgrammeDetail?) {
    val modifier = Modifier.padding(horizontal = PorchSpace.Margin)
    if (!model.loaded) {
        PreviewFrame(null, 0L, loadDetail, modifier, PorchSpace.GuidePreviewHeight, PorchSpace.GuidePreviewWidth) { Text("Loading the guide…", style = PorchType.Body, color = PorchColors.Muted) }
        return
    }
    if (channel == null) {
        PreviewFrame(null, 0L, loadDetail, modifier, PorchSpace.GuidePreviewHeight, PorchSpace.GuidePreviewWidth) { EmptyListWords(model) }
        return
    }
    val programme = model.programmeAt(channel)
    val matched = channel.matchedXmltvId != null
    val name = ChannelNames.listTitle(channel.name)
    PreviewFrame(channel, programme?.id ?: 0L, loadDetail, modifier, PorchSpace.GuidePreviewHeight, PorchSpace.GuidePreviewWidth) { description ->
        PreviewWords(
            channel = channel,
            channelLine = name,
            // The channel line already has the name: no second copy while programmes load, or on a channel with no guide.
            title = if (programme == null && (!matched || !model.programmesLoaded)) null
            else GuideHeroText.title(programme?.title, matched, model.programmesLoaded, name),
            description = description,
            descriptionLines = 1,
            showChannelLine = false,
            titleStyle = PorchType.GuideHeroTitle,
            titleLines = 1,
        ) {
            if (programme == null) {
                val status = GuideHeroText.status(matched, model.programmesLoaded)
                if (status.isNotEmpty()) PreviewStatusLine(live = false, text = status)
            } else {
                val start = GuideClock.shift(programme.startUtc, model.offset)
                val catchup = model.recordedCopy(channel, start, model.now) != null
                val status = GuideCellText.status(programme.startUtc, programme.stopUtc, model.now, model.offset, catchup)
                val live = GuideClock.isOnNow(programme.startUtc, programme.stopUtc, model.now, model.offset)
                PreviewStatusLine(live, if (live) status.removePrefix("On now").removePrefix("  ·  ") else status)
            }
        }
    }
}

/** The empty list's name, and which other lists have channels. */
@Composable
private fun EmptyListWords(model: GuideViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(PorchSpace.PreviewLineGap)) {
        Text(
            model.chips.firstOrNull { it.key == model.filter }?.label ?: "All",
            style = PorchType.PreviewTitle,
            color = PorchColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val others = buildList {
            model.chips.filter { it.key != model.filter && it.key != GuideChips.ALL && (it.key != GuideChips.FAV || model.hasFavourites) }
                .forEach { add(it.label) }
        }
        // Four names at most, so the line stays on one line however many groups there are.
        val named = others.take(4)
        val more = others.size - named.size
        val have = when {
            others.isEmpty() -> ""
            more > 0 -> " · ${named.joinToString(", ")} and $more more have channels"
            named.size == 1 -> " · ${named[0]} has channels"
            else -> " · ${named.dropLast(1).joinToString(", ")} and ${named.last()} have channels"
        }
        Text("0 channels$have", style = PorchType.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The "No channels" panel, under the same time header, so the screen keeps its shape. */
@Composable
private fun EmptyGuide(model: GuideViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(PorchSpace.GuideHeaderHeight), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(PorchSpace.GuideChannelColumn)) { DayText(model.windowStart) }
            Box(Modifier.weight(1f)) { TimeMarks(model.windowStart, model.now) }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(PorchColors.Surface, RoundedCornerShape(PorchRadius.Panel))
                .padding(horizontal = PorchSpace.Margin),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("No channels in this list.", style = PorchType.ScreenTitle)
            Spacer(Modifier.height(PorchSpace.ListGap))
            Text(
                when {
                    model.sources.isEmpty() -> "No sources yet. Add one in Settings."
                    model.emptyNeedsGuide ->
                        "No guide data for these channels yet. Settings > Show channels without a guide lists them anyway."
                    model.filter == "fav" -> "You have no favourites yet. Hold OK on a channel to add one, or pick another list above."
                    model.filter == "all" -> "No channels came through. Refresh your sources in Settings."
                    else -> "None of the channels in this list came through. Pick another list above, or refresh your sources in Settings."
                },
                style = PorchType.Body,
                color = PorchColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun DayText(windowStart: Long) {
    Text(formatGuideDayLong(windowStart), style = PorchType.Meta, maxLines = 1)
}

/** Half-hour marks across the window; the first carries am or pm. */
@Composable
private fun TimeMarks(windowStart: Long, now: Long) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth
        repeat(MARKS) { step ->
            val at = windowStart + step * 30 * 60_000L
            if (GuideWindow.markHiddenByNow(at, now, WINDOW_MS)) return@repeat
            Text(
                if (step == 0) formatTime(at) else formatHourMinute(at),
                style = PorchType.Meta,
                maxLines = 1,
                modifier = Modifier.align(Alignment.CenterStart).offset(x = width * (step / MARKS.toFloat())),
            )
        }
    }
}

@Composable
private fun GuideGrid(
    model: GuideViewModel,
    grid: FocusRequester,
    gridFocused: Boolean,
    chipRow: FocusRequester,
    marks: ReminderMarks,
    onFocus: (Boolean) -> Unit,
    onPlay: (List<Channel>, Channel, CatchupRequest?, Channel?) -> Unit,
) {
    val byChannel = remember(model.programmes) { model.programmes.groupBy { it.sourceId to it.channelKey } }
    val visible = model.visible()
    val quick = LocalQuickMenu.current
    // A long press on OK opens the quick menu; the short press plays when OK comes back up, but only if its key-down was ours.
    var pressed by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .focusRequester(grid)
            .focusProperties { up = chipRow }
            .onKeyEvent { event ->
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        when (event.type) {
                            KeyEventType.KeyDown -> {
                                pressed = true
                                if (event.nativeKeyEvent.repeatCount == 0) held = false
                                if (event.nativeKeyEvent.repeatCount > 0 && !held) {
                                    // The menu takes the key-up, so this row never sees it: the next press starts clean.
                                    held = true
                                    model.channels.getOrNull(model.channelIndex)?.let { channel ->
                                        quick.open(
                                            channel,
                                            onPlayCopy = { copy -> onPlay(model.channels, channel, null, copy) },
                                            programme = model.programmeAt(channel),
                                            onWatch = { catchup, copy -> onPlay(model.channels, channel, catchup, copy) },
                                        )
                                    }
                                }
                            }
                            KeyEventType.KeyUp -> {
                                if (pressed && !held) activate(model, quick, onPlay)
                                pressed = false
                                held = false
                            }
                            else -> Unit
                        }
                        true
                    }
                    Key.DirectionDown, Key.DirectionUp, Key.DirectionRight, Key.DirectionLeft -> {
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (event.key) {
                            Key.DirectionDown -> model.moveChannel(1)
                            Key.DirectionUp -> model.moveChannel(-1)
                            Key.DirectionRight -> {
                                model.moveTime(1)
                                true
                            }
                            else -> {
                                model.moveTime(-1)
                                true
                            }
                        }
                    }
                    else -> false
                }
            }
            .onFocusChanged {
                onFocus(it.isFocused)
                pressed = false
                held = false
            }
            .focusable(),
    ) {
        Column(Modifier.width(PorchSpace.GuideChannelColumn)) {
            Box(Modifier.height(PorchSpace.GuideHeaderHeight), contentAlignment = Alignment.CenterStart) { DayText(model.windowStart) }
            Column(verticalArrangement = Arrangement.spacedBy(PorchSpace.GuideRowGap)) {
                // Under a country chip the heading already says the country, so a row doesn't repeat it.
                val heading = if (GuideChips.isCountry(model.filter)) model.filter.removePrefix(GuideChips.COUNTRY_PREFIX) else null
                visible.forEach { channel -> key(channel.id) { ChannelLabel(channel, heading, model.keepsRecordings(channel)) } }
            }
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            val width = maxWidth
            // Drawn first, so the cells paint over the line and it never crosses text.
            NowLine(model, width)
            Column {
                Box(Modifier.fillMaxWidth().height(PorchSpace.GuideHeaderHeight)) { TimeMarks(model.windowStart, model.now) }
                Column(verticalArrangement = Arrangement.spacedBy(PorchSpace.GuideRowGap)) {
                    visible.forEachIndexed { index, channel ->
                        key(channel.id) {
                            val selected = model.anchor + index == model.channelIndex
                            GuideCells(
                                channel = channel,
                                row = byChannel[channel.sourceId to channel.matchedXmltvId].orEmpty(),
                                model = model,
                                width = width,
                                focusedId = if (selected) model.programmeAt(channel)?.id else null,
                                selected = selected,
                                ring = gridFocused,
                                marks = marks,
                            )
                        }
                    }
                }
            }
            NowPill(model, width)
        }
    }
}

/** The red line down through the grid at the current time, under the cells. Red means live. */
@Composable
private fun BoxScope.NowLine(model: GuideViewModel, width: Dp) {
    val fraction = (model.now - model.windowStart).toFloat() / WINDOW_MS
    if (fraction !in 0f..1f) return
    val x = width * fraction
    Box(Modifier.matchParentSize().padding(top = PorchSpace.GuideHeaderHeight)) {
        Box(Modifier.offset(x = x - PorchSpace.NowLineWidth / 2).width(PorchSpace.NowLineWidth).fillMaxHeight().background(PorchColors.Live))
    }
}

/** The red pill in the time header at the current time. */
@Composable
private fun NowPill(model: GuideViewModel, width: Dp) {
    val fraction = (model.now - model.windowStart).toFloat() / WINDOW_MS
    if (fraction !in 0f..1f) return
    val x = width * fraction
    Box(
        Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(placeable.width, placeable.height) { placeable.place(x.roundToPx() - placeable.width / 2, 0) }
            }
            .height(PorchSpace.GuideHeaderHeight)
            .padding(vertical = PorchSpace.ChipPadY)
            .background(PorchColors.Live, RoundedCornerShape(PorchRadius.Row))
            .padding(horizontal = PorchSpace.ChipPadX),
        contentAlignment = Alignment.Center,
    ) {
        Text(formatHourMinute(model.now), style = PorchType.LiveLabel, color = PorchColors.OnAccent, maxLines = 1)
    }
}

/** Under the UK chip a trailing "GB" is the same label as "UK", so it goes too. */
private fun guideTitle(name: String, heading: String?): String {
    val title = ChannelNames.listTitle(name, heading)
    val other = when (heading?.uppercase()) {
        "UK" -> "GB"
        "GB" -> "UK"
        else -> return title
    }
    return if (title == ChannelNames.listTitle(name)) ChannelNames.listTitle(name, other) else title
}

@Composable
private fun ChannelLabel(channel: Channel, heading: String?, keepsRecordings: Boolean) {
    Row(Modifier.height(PorchSpace.GuideRowHeight), verticalAlignment = Alignment.CenterVertically) {
        // The badge sits in a fixed slot so the names line up whatever the letters.
        Box(Modifier.width(PorchSpace.PanelBadgeSlot), contentAlignment = Alignment.CenterStart) {
            ChannelLogo(channel, channelColor(channel), PorchSpace.PreviewSmallLogo)
        }
        Column(Modifier.padding(end = PorchSpace.GuideCellPad)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (channel.favourite) {
                    FavouriteStar()
                    Spacer(Modifier.width(4.dp))
                }
                Text(guideTitle(channel.name, heading), style = PorchType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val meta = listOfNotNull(channel.number.takeIf { it > 0 }?.toString(), "catch-up".takeIf { keepsRecordings }).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, style = PorchType.Meta, maxLines = 1)
            }
        }
    }
}

@Composable
private fun GuideCells(
    channel: Channel,
    row: List<Programme>,
    model: GuideViewModel,
    width: Dp,
    focusedId: Long?,
    selected: Boolean,
    ring: Boolean,
    marks: ReminderMarks,
) {
    val shape = RoundedCornerShape(PorchRadius.Row)
    Box(Modifier.fillMaxWidth().height(PorchSpace.GuideRowHeight)) {
        val cellsInWindow = row.count {
            GuideWindow.span(GuideClock.shift(it.startUtc, model.offset), GuideClock.shift(it.stopUtc, model.offset), model.windowStart, WINDOW_MS, width.value).width > 0f
        }
        if (GuideCellText.showsBar(channel.matchedXmltvId != null, model.programmesLoaded, cellsInWindow)) {
            // No guide for this channel, or nothing in this window: one bar across it, so the row can still show focus.
            // While programmes load, every row is this bar with no text, so nothing says "empty" too soon.
            val focused = selected && ring
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = PorchSpace.GuideCellGap / 2)
                    .background(
                        when {
                            focused -> PorchColors.Focus
                            selected -> PorchColors.SurfaceRaised
                            else -> PorchColors.Surface
                        },
                        shape,
                    )
                    .padding(horizontal = PorchSpace.GuideCellPad),
                contentAlignment = Alignment.CenterStart,
            ) {
                val text = GuideCellText.barText(ChannelNames.listTitle(channel.name), channel.matchedXmltvId != null, model.programmesLoaded)
                if (text.isNotEmpty()) {
                    Text(
                        text,
                        style = PorchType.GuideCell,
                        color = if (focused) PorchColors.OnFocus else PorchColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            return@Box
        }
        row.forEach { programme ->
            val start = GuideClock.shift(programme.startUtc, model.offset)
            val stop = GuideClock.shift(programme.stopUtc, model.offset)
            val span = GuideWindow.span(start, stop, model.windowStart, WINDOW_MS, width.value)
            if (span.width <= 0f) return@forEach
            val cell = (span.width.dp - PorchSpace.GuideCellGap).coerceAtLeast(0.dp)
            val isFocused = programme.id == focusedId
            val past = stop <= model.now
            val cellX = span.x + PorchSpace.GuideCellGap.value / 2
            // A flat lighter tone from the cell's edge to the now-line, behind the title. A focused cell stays one solid fill.
            val elapsed = if (isFocused && ring) 0f else GuideWindow.elapsedWidth(start, stop, model.now, model.windowStart, WINDOW_MS, width.value, cellX)
            Box(
                Modifier
                    .offset(x = span.x.dp + PorchSpace.GuideCellGap / 2)
                    .width(cell)
                    .fillMaxHeight()
                    .background(
                        when {
                            isFocused && ring -> PorchColors.Focus
                            isFocused -> PorchColors.SurfaceRaised
                            else -> PorchColors.Surface
                        },
                        shape,
                    )
                    .drawBehind {
                        if (elapsed > 0f) {
                            val radius = PorchRadius.Row.toPx()
                            drawRoundRect(
                                PorchColors.GuideElapsed,
                                size = Size(elapsed.dp.toPx().coerceAtMost(size.width), size.height),
                                cornerRadius = CornerRadius(radius, radius),
                            )
                        }
                    }
                    .padding(horizontal = PorchSpace.GuideCellPad),
                contentAlignment = Alignment.CenterStart,
            ) {
                val bell = marks.has(channel, start)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (cell >= PorchSpace.GuideMinTextCell) {
                        Text(
                            programme.title,
                            modifier = Modifier.weight(1f),
                            style = PorchType.GuideCell,
                            color = when {
                                isFocused && ring -> PorchColors.OnFocus
                                past && !isFocused -> PorchColors.Muted
                                else -> PorchColors.Text
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // The bell sits at the cell's right edge, and shows even in a cell too narrow for its title.
                    if (bell && cell >= PorchSpace.ReminderMark + PorchSpace.GuideCellPad * 2) {
                        Spacer(Modifier.width(PorchSpace.GuideCellGap))
                        ReminderBell(if (isFocused && ring) PorchColors.OnFocus else PorchColors.Accent)
                    }
                }
            }
        }
    }
}


private fun activate(model: GuideViewModel, quick: QuickMenuState, onPlay: (List<Channel>, Channel, CatchupRequest?, Channel?) -> Unit) {
    val channel = model.channels.getOrNull(model.channelIndex) ?: return
    val programme = model.programmeAt(channel)
    val now = model.now
    val recorded = programme?.let { model.recordedCopy(channel, GuideClock.shift(it.startUtc, model.offset), now) }
    when {
        programme == null -> onPlay(model.channels, channel, null, null)
        GuideClock.isOnNow(programme.startUtc, programme.stopUtc, now, model.offset) ->
            onPlay(model.channels, channel, null, null)
        GuideClock.shift(programme.stopUtc, model.offset) <= now && recorded != null -> onPlay(
            model.channels,
            channel,
            CatchupRequest(programme.startUtc, programme.stopUtc, programme.title),
            recorded.takeIf { it.id != channel.id },
        )
        GuideClock.shift(programme.stopUtc, model.offset) <= now ->
            model.say("No catch-up on ${channel.shownTitle}")
        else -> quick.openAbout(channel, programme) { catchup, copy -> onPlay(model.channels, channel, catchup, copy) }
    }
}
