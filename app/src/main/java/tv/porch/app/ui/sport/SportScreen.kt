package tv.porch.app.ui.sport

import android.os.Process
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import java.time.ZoneId
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.porch.app.AppGraph
import tv.porch.app.data.SportGuide
import tv.porch.app.data.SportScores
import tv.porch.app.data.SportSchedule
import tv.porch.app.domain.Channel
import tv.porch.app.domain.Reminder
import tv.porch.app.domain.ScoreRow
import tv.porch.app.domain.ScoreSection
import tv.porch.app.domain.SportChannels
import tv.porch.app.domain.SportOk
import tv.porch.app.domain.SportScoreRules
import tv.porch.app.domain.SportRowText
import tv.porch.app.domain.SportRules
import tv.porch.app.domain.Tabs
import tv.porch.app.domain.formatHeaderClockLong
import tv.porch.app.ui.VmFactory
import tv.porch.app.ui.components.HintBar
import tv.porch.app.ui.components.ReminderBell
import tv.porch.app.ui.components.ReminderMarks
import tv.porch.app.ui.components.rememberReminderMarks
import tv.porch.app.ui.components.Pill
import tv.porch.app.ui.components.TopBar
import tv.porch.app.ui.components.clickScale
import tv.porch.app.ui.components.rememberNow
import tv.porch.app.ui.components.tryFocus
import tv.porch.app.ui.movies.ChipRow
import tv.porch.app.ui.theme.PorchColors
import tv.porch.app.ui.theme.PorchFocus
import tv.porch.app.ui.theme.PorchRadius
import tv.porch.app.ui.theme.PorchSpace
import tv.porch.app.ui.theme.PorchType

/** [schedule] is null until the saved copy or the first load answers. */
data class SportUi(val schedule: SportSchedule.State? = null, val refreshing: Boolean = false)

/** One of Results or Ladders: [data] is null until the saved copy or the first load answers. */
data class ScoreFeed<T>(val data: T? = null, val refreshing: Boolean = false)

private const val VIEW_COMING_UP = "Coming up"
private const val VIEW_RESULTS = "Results"
private const val VIEW_LADDERS = "Ladders"
private val VIEWS = listOf(VIEW_COMING_UP, VIEW_RESULTS, VIEW_LADDERS)

class SportViewModel(private val graph: AppGraph) : ViewModel() {
    /** The view the viewer is on. Moves with focus. */
    val view = MutableStateFlow(VIEW_COMING_UP)
    private val resultsState = MutableStateFlow(ScoreFeed<SportScores.Results>())
    private val laddersState = MutableStateFlow(ScoreFeed<SportScores.Ladders>())
    val resultsUi: StateFlow<ScoreFeed<SportScores.Results>> = resultsState.asStateFlow()
    val laddersUi: StateFlow<ScoreFeed<SportScores.Ladders>> = laddersState.asStateFlow()
    private val shownKeys = MutableStateFlow<Set<String>>(emptySet())

    /** Keys of the rows the viewer has opened. Kept here only, never saved. */
    val revealed: StateFlow<Set<String>> = shownKeys.asStateFlow()
    private var resultsRunning = false
    private var laddersRunning = false

    /** The chip the viewer is on. Moves with focus, so the tick follows at once. */
    val selected = MutableStateFlow(SportRules.ALL)
    private val state = MutableStateFlow(SportUi())
    val ui: StateFlow<SportUi> = state.asStateFlow()
    /** Sport channels' programmes from 3 hours ago to 14 days on, read each time the screen opens. Null until read. */
    private val guide = MutableStateFlow<SportGuide?>(null)

    /** One thread at background priority, so working out links never competes with playback or a key press. */
    private val linkWork = Executors.newSingleThreadExecutor { work ->
        Thread({ Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND); work.run() }, "porch-sport-links")
    }.asCoroutineDispatcher().also { addCloseable(it) }

    /**
     * Every game's possible channels, read from the guide on [linkWork]. On a big guide this can take seconds on the
     * Chromecast, so it runs only when the schedule or the guide changes, one at a time, and never inside a frame
     * (16 Nov: worked out during composition, it froze the TV past Android's 5-second limit). Null until both are read.
     */
    val candidates: StateFlow<SportChannels.Candidates?> = combine(state.map { it.schedule }.distinctUntilChanged(), guide) { schedule, found ->
        schedule to found
    }
        .conflate()
        .map { (schedule, found) -> if (schedule == null || found == null) null else SportChannels.candidates(schedule.fixtures, found.airings, found.offsets) }
        .flowOn(linkWork)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private var running = false

    init {
        // The saved copy shows at once; the screen asks for a refresh when it opens.
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { try { graph.sport.saved() } catch (error: Exception) { if (error is CancellationException) throw error; null } }
            if (saved != null) state.update { if (it.schedule == null) it.copy(schedule = saved) else it }
        }
        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) { try { graph.scores.savedResults() } catch (error: Exception) { if (error is CancellationException) throw error; null } }
            if (results != null) resultsState.update { if (it.data == null) it.copy(data = results) else it }
            val ladders = withContext(Dispatchers.IO) { try { graph.scores.savedLadders() } catch (error: Exception) { if (error is CancellationException) throw error; null } }
            if (ladders != null) laddersState.update { if (it.data == null) it.copy(data = ladders) else it }
        }
    }

    /** Each visit starts on Coming up. */
    fun openScreen() {
        view.value = VIEW_COMING_UP
    }

    /** Moving onto Results or Ladders asks for the leagues whose copy is an hour old. */
    fun selectView(next: String) {
        if (view.value == next) return
        view.value = next
        if (next != VIEW_COMING_UP) refreshView(next, false)
    }

    fun refreshView(of: String, force: Boolean) {
        when (of) {
            VIEW_RESULTS -> refreshResults(force)
            VIEW_LADDERS -> refreshLadders(force)
            else -> refresh(force)
        }
    }

    /** OK on a hidden row shows its result or position. */
    fun reveal(key: String) {
        shownKeys.update { it + key }
    }

    private fun refreshResults(force: Boolean) {
        if (resultsRunning) return
        resultsRunning = true
        resultsState.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            try {
                val result = try { graph.scores.refreshResults(force) { partial -> resultsState.update { it.copy(data = partial) } } } catch (error: Exception) { if (error is CancellationException) throw error; null }
                resultsState.update {
                    it.copy(
                        data = result ?: it.data?.copy(failed = SportRules.chips.drop(1), allFailed = true)
                            ?: SportScores.Results(emptyList(), SportRules.chips.drop(1), true, null),
                        refreshing = false,
                    )
                }
            } finally {
                resultsRunning = false
            }
        }
    }

    private fun refreshLadders(force: Boolean) {
        if (laddersRunning) return
        laddersRunning = true
        laddersState.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            try {
                val result = try { graph.scores.refreshLadders(force) { partial -> laddersState.update { it.copy(data = partial) } } } catch (error: Exception) { if (error is CancellationException) throw error; null }
                laddersState.update {
                    it.copy(
                        data = result ?: it.data?.copy(failed = SportRules.chips.drop(1), allFailed = true)
                            ?: SportScores.Ladders(emptyList(), SportRules.chips.drop(1), true, null),
                        refreshing = false,
                    )
                }
            } finally {
                laddersRunning = false
            }
        }
    }

    /** Reads the guide again. The screen calls it each time the tab opens, because this model lives as long as the activity. */
    fun loadGuide() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val read = try {
                graph.repository.sportGuide(now - GUIDE_BEHIND_MS, now + (SportRules.WINDOW_DAYS + 1) * 86_400_000L)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                null
            }
            // Setting it compares the new guide with the old one, row by row, so do that off the main thread too.
            if (read != null) withContext(Dispatchers.Default) { guide.value = read }
        }
    }

    /** Asks for the leagues whose copy is an hour old (all of them when [force]). Never two at once. */
    fun refresh(force: Boolean) {
        if (running) return
        running = true
        state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            try {
                val result = try { graph.sport.refresh(force) { partial -> state.update { it.copy(schedule = partial) } } } catch (error: Exception) { if (error is CancellationException) throw error; null }
                state.update {
                    it.copy(
                        schedule = result ?: it.schedule?.copy(failed = SportRules.chips.drop(1), allFailed = true)
                            ?: SportSchedule.State(emptyList(), SportRules.chips.drop(1), true, null),
                        refreshing = false,
                    )
                }
            } finally {
                running = false
            }
        }
    }

    fun select(chip: String) {
        selected.value = chip
    }

    private companion object {
        /** A game that began up to 3 hours ago can still be on; its programme started before now. */
        const val GUIDE_BEHIND_MS = 3 * 3_600_000L
    }
}

@Composable
fun SportScreen(graph: AppGraph, onTab: (String) -> Unit, onPlay: (List<Channel>, Channel) -> Unit) {
    val model: SportViewModel = viewModel(factory = VmFactory { SportViewModel(graph) })
    // Back to Coming up before the first frame, so a Results or Ladders list from the last visit never draws or takes focus.
    remember { model.openScreen() }
    val ui by model.ui.collectAsStateWithLifecycle()
    val chip by model.selected.collectAsStateWithLifecycle()
    val candidates by model.candidates.collectAsStateWithLifecycle()
    val view by model.view.collectAsStateWithLifecycle()
    val resultsUi by model.resultsUi.collectAsStateWithLifecycle()
    val laddersUi by model.laddersUi.collectAsStateWithLifecycle()
    val revealed by model.revealed.collectAsStateWithLifecycle()
    val follows by graph.follows.follows.collectAsStateWithLifecycle()
    val marks = rememberReminderMarks(graph)
    val now = rememberNow()
    val zone = remember { ZoneId.systemDefault() }
    val selectedChip = remember { FocusRequester() }
    val selectedTab = remember { FocusRequester() }
    val viewChip = remember { FocusRequester() }
    val firstRow = remember { FocusRequester() }
    val chipState = rememberLazyListState()
    val viewState = rememberLazyListState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var rowFocused by remember { mutableStateOf(false) }
    var focusedKey by remember { mutableStateOf("") }
    var moved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        model.loadGuide()
        model.refresh(false)
    }

    val comingUp = view == VIEW_COMING_UP
    val schedule = ui.schedule
    // Links are made from every fixture, not just this chip's, so a chip change does not read the guide again. Each
    // minute only drops programmes that have ended, which is cheap.
    val links = remember(candidates, now / 60_000L) { candidates?.at(now).orEmpty() }
    val days = remember(schedule, chip, follows, links, now / 60_000L) {
        SportRules.days(SportRules.forChip(schedule?.fixtures.orEmpty(), chip), now, zone, follows, links)
    }
    val sections = remember(view, resultsUi.data, laddersUi.data, chip, follows, revealed, now / 60_000L) {
        when (view) {
            VIEW_RESULTS -> resultsUi.data?.let { SportScoreRules.resultSections(SportScoreRules.forChip(it.results, chip), now, zone, follows, revealed) }.orEmpty()
            VIEW_LADDERS -> laddersUi.data?.let { SportScoreRules.ladderSections(SportScoreRules.forChip(it.ladders, chip), follows, revealed) }.orEmpty()
            else -> emptyList()
        }
    }
    fun okOf(row: SportRowText): SportOk {
        val link = row.link ?: return SportOk.None
        return SportRules.okFor(link, now, marks.has(link.channels.first(), link.startMs))
    }
    fun press(row: SportRowText) {
        val link = row.link ?: return
        val channel = link.channels.first()
        when (okOf(row)) {
            SportOk.Play -> onPlay(link.channels, channel)
            SportOk.Remind, SportOk.Unremind -> scope.launch {
                val identity = graph.repository.channelIdentity(channel.id) ?: return@launch
                graph.reminders.toggle(Reminder(channel.sourceId, identity, row.title, link.startMs, link.stopMs))
            }
            SportOk.None -> Unit
        }
    }
    val rowCount = if (comingUp) days.sumOf { it.rows.size } else sections.sumOf { it.rows.size }
    // What this view knows about its own load, so the messages work the same on all three.
    val feed: ViewFeed? = when (view) {
        VIEW_RESULTS -> resultsUi.data?.let { ViewFeed(it.failed, it.allFailed, it.updatedAt) }
        VIEW_LADDERS -> laddersUi.data?.let { ViewFeed(it.failed, it.allFailed, it.updatedAt) }
        else -> schedule?.let { ViewFeed(it.failed, it.allFailed, it.updatedAt) }
    }
    val busy = when (view) {
        VIEW_RESULTS -> resultsUi.refreshing
        VIEW_LADDERS -> laddersUi.refreshing
        else -> ui.refreshing
    }
    // A row that left the screen sends no "lost focus", so a changed chip or view or a gone list clears it here.
    LaunchedEffect(chip, view, rowCount > 0) { rowFocused = false }
    val neverLoaded = feed != null && feed.updatedAt == null && feed.failed.isNotEmpty()
    val listShown = rowCount > 0 && feed != null && !neverLoaded

    // Focus starts on the All chip, then moves to the first row once the list is there, unless the viewer already moved.
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (!moved) focusChip(chipState, chip, selectedChip)
    }
    LaunchedEffect(rowCount > 0) {
        if (placed || rowCount == 0) return@LaunchedEffect
        withFrameNanos { }
        if (!moved) firstRow.tryFocus()
        placed = true
    }
    // A new chip or view starts at the top of its list.
    val shown = remember { arrayOf("$chip|$view") }
    LaunchedEffect(chip, view) {
        if (shown[0] != "$chip|$view") {
            shown[0] = "$chip|$view"
            listState.scrollToItem(0)
        }
    }
    // The list is far from the chips, so Back is the way up to them. A second Back leaves the tab.
    BackHandler(enabled = rowFocused && listShown) {
        scope.launch {
            listState.scrollToItem(0)
            withFrameNanos { }
            focusChip(chipState, chip, selectedChip)
        }
    }

    val note = feed?.let {
        when (view) {
            VIEW_RESULTS -> SportScoreRules.refreshNote("results", it.failed, it.allFailed, it.updatedAt, now)
            VIEW_LADDERS -> SportScoreRules.refreshNote("ladders", it.failed, it.allFailed, it.updatedAt, now)
            else -> SportRules.refreshNote(it.failed, it.allFailed, it.updatedAt, now)
        }
    }
    val loadingText = when (view) {
        VIEW_RESULTS -> "Loading the results..."
        VIEW_LADDERS -> "Loading the ladders..."
        else -> "Loading the schedule..."
    }
    val failedText = when (view) {
        VIEW_RESULTS -> "Results couldn't load."
        VIEW_LADDERS -> "Ladders couldn't load."
        else -> "Schedule couldn't load."
    }
    val emptyText = when (view) {
        VIEW_RESULTS -> SportScoreRules.emptyResults(chip)
        VIEW_LADDERS -> SportScoreRules.emptyLadders(chip)
        else -> SportRules.emptyText(chip)
    }
    val loadingLine = busy && (note != null || rowCount == 0)
    val aimUp = { if (chipState.layoutInfo.visibleItemsInfo.any { it.index == SportRules.chips.indexOf(chip) }) selectedChip else FocusRequester.Default }

    Column(Modifier.fillMaxSize().onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown) moved = true; false }) {
        TopBar(
            title = "Sport",
            clock = formatHeaderClockLong(now),
            tabs = Tabs.labels,
            current = "Sport",
            onTab = onTab,
            selectedTab = selectedTab,
        )
        ChipRow(
            VIEWS, view, viewState, viewChip, selectedTab, upToTabs = true,
            onSelect = { model.selectView(it) }, onFocus = { },
        )
        ChipRow(
            SportRules.chips, chip, chipState, selectedChip, viewChip, upToTabs = true,
            onSelect = { model.select(it) }, onFocus = { },
        )
        Column(Modifier.weight(1f).fillMaxWidth()) {
            when {
                feed == null -> Message(loadingText)
                neverLoaded && !busy -> {
                    Message(failedText)
                    TryAgain { model.refreshView(view, true) }
                }
                else -> {
                    if (loadingLine) Message(loadingText)
                    else if (note != null) {
                        Message(note)
                        TryAgain { model.refreshView(view, true) }
                    }
                    if (rowCount == 0) {
                        if (!busy) Message(emptyText)
                    }
                    else if (comingUp) LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = PorchSpace.Margin, vertical = PorchSpace.FocusRoom),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        var first = true
                        for (day in days) {
                            item(key = "day:${day.heading}") {
                                Text(
                                    day.heading, style = PorchType.Section,
                                    modifier = Modifier.padding(top = PorchSpace.GuideGap, bottom = PorchSpace.ListGap),
                                )
                            }
                            for (row in day.rows) {
                                val isFirst = first
                                first = false
                                item(key = row.key) {
                                    var modifier: Modifier = Modifier
                                    if (isFirst) modifier = modifier.focusRequester(firstRow).focusProperties {
                                        // Only aim at the chip when it is composed; otherwise let focus find its own way.
                                        up = aimUp()
                                    }
                                    SportRow(
                                        row, marks, okOf(row) != SportOk.None, { press(row) },
                                        modifier.onFocusChanged {
                                            rowFocused = it.hasFocus
                                            if (it.hasFocus) focusedKey = row.key
                                        },
                                    )
                                }
                            }
                        }
                    } else ScoreList(
                        sections, listState, firstRow, aimUp, { model.reveal(it.key) },
                    ) { key, has ->
                        rowFocused = has
                        if (has) focusedKey = key
                    }
                }
            }
        }
        HintBar(
            if (rowFocused && listShown) {
                if (comingUp) {
                    val focused = days.firstNotNullOfOrNull { day -> day.rows.firstOrNull { it.key == focusedKey } }
                    // Only "A v B" games can have a channel, so an event (UFC, golf) says nothing about OK.
                    val okText = focused?.takeIf { it.link != null || SportChannels.sides(it.title) != null }?.let { SportRules.okHint(okOf(it)) + " · " }.orEmpty()
                    okText + "Back goes to the filters"
                } else {
                    val focused = sections.firstNotNullOfOrNull { section -> section.rows.firstOrNull { it.key == focusedKey } }
                    val okText = if (focused != null && !focused.revealed) {
                        (if (view == VIEW_RESULTS) "OK shows the result" else "OK shows the position") + " · "
                    } else ""
                    okText + "Back goes to the filters"
                }
            } else "",
            when (view) {
                VIEW_RESULTS -> resultsUi.data?.updatedAt?.let { SportScoreRules.updatedText("Results", it, now) }.orEmpty()
                VIEW_LADDERS -> laddersUi.data?.updatedAt?.let { SportScoreRules.updatedText("Ladders", it, now) }.orEmpty()
                else -> schedule?.updatedAt?.let { SportRules.updatedText(it, now) }.orEmpty()
            },
        )
    }
}

/** What the messages need to know about a view's last load. */
private data class ViewFeed(val failed: List<String>, val allFailed: Boolean, val updatedAt: Long?)

/** The Results and Ladders list: a heading per section, a row each. The first row takes focus like Coming up's. */
@Composable
private fun ScoreList(
    sections: List<ScoreSection>,
    listState: LazyListState,
    firstRow: FocusRequester,
    aimUp: () -> FocusRequester,
    onPress: (ScoreRow) -> Unit,
    onFocus: (String, Boolean) -> Unit,
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = PorchSpace.Margin, vertical = PorchSpace.FocusRoom),
        modifier = Modifier.fillMaxSize(),
    ) {
        var first = true
        for (section in sections) {
            item(key = "section:${section.heading}") {
                Text(
                    section.heading, style = PorchType.Section,
                    modifier = Modifier.padding(top = PorchSpace.GuideGap, bottom = PorchSpace.ListGap),
                )
            }
            for (row in section.rows) {
                val isFirst = first
                first = false
                item(key = row.key) {
                    var modifier: Modifier = Modifier
                    if (isFirst) modifier = modifier.focusRequester(firstRow).focusProperties { up = aimUp() }
                    ScoreRowView(row, { onPress(row) }, modifier.onFocusChanged { onFocus(row.key, it.hasFocus) })
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = PorchType.Meta, modifier = Modifier.padding(horizontal = PorchSpace.Margin, vertical = PorchSpace.TileGap))
}

@Composable
private fun TryAgain(onClick: () -> Unit) {
    Box(Modifier.padding(horizontal = PorchSpace.Margin - PorchSpace.ButtonPadX, vertical = PorchSpace.FocusRoom)) {
        Pill("Try again", modifier = Modifier.padding(horizontal = PorchSpace.ButtonPadX), onClick = onClick)
    }
}

/**
 * One fixture: title, league beside it, the channel showing it, a "Following" label when the viewer follows it, a bell when
 * a reminder is set, and the time (or LIVE) at the right edge. OK does what [SportRules.okFor] says; [canPress] is false
 * for a game with no channel.
 */
@Composable
private fun SportRow(row: SportRowText, marks: ReminderMarks, canPress: Boolean, onPress: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val link = row.link
    val bell = link != null && !row.live && marks.has(link.channels.first(), link.startMs)
    Surface(
        onClick = { if (canPress) onPress() },
        modifier = modifier
            .fillMaxWidth()
            .height(PorchSpace.SportRow)
            .onFocusChanged { focused = it.hasFocus },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(PorchRadius.Row)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = PorchColors.Text,
            focusedContainerColor = PorchColors.Focus,
            focusedContentColor = PorchColors.OnFocus,
            pressedContainerColor = PorchColors.Focus,
            pressedContentColor = PorchColors.OnFocus,
        ),
        border = ClickableSurfaceDefaults.border(),
        scale = clickScale(PorchFocus.RowScale),
        glow = ClickableSurfaceDefaults.glow(),
    ) {
        val quiet = if (focused) PorchColors.OnFocus else PorchColors.Muted
        Row(
            Modifier.fillMaxSize().padding(horizontal = PorchSpace.PanelPad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(row.title, style = PorchType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(2f, fill = false))
                Spacer(Modifier.width(PorchSpace.TileGap))
                Text(row.league, style = PorchType.Meta, color = quiet, maxLines = 1)
                if (link != null) {
                    Text(" · ", style = PorchType.Meta, color = quiet, maxLines = 1)
                    Text(link.channels.first().shownTitle, style = PorchType.Meta, color = quiet, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
            }
            if (row.followed) {
                Spacer(Modifier.width(PorchSpace.TileGap))
                Text("Following", style = PorchType.Meta, color = if (focused) PorchColors.OnFocus else PorchColors.Accent, maxLines = 1)
            }
            if (bell) {
                Spacer(Modifier.width(PorchSpace.TileGap))
                ReminderBell(if (focused) PorchColors.OnFocus else PorchColors.Accent)
            }
            Spacer(Modifier.width(PorchSpace.TileGap))
            if (row.live) {
                Text("LIVE", style = PorchType.LiveLabel, color = if (focused) PorchColors.LiveOnLight else PorchColors.LiveOnDark, maxLines = 1)
            } else {
                Text(row.time, style = PorchType.Meta, color = quiet, maxLines = 1)
            }
        }
    }
}

/**
 * One result or ladder row: title, league beside it when there is one, a "Following" label, and at the right edge "Hidden"
 * until OK shows the result or position. OK on a shown row does nothing.
 */
@Composable
private fun ScoreRowView(row: ScoreRow, onPress: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = { if (!row.revealed) onPress() },
        modifier = modifier
            .fillMaxWidth()
            .height(PorchSpace.SportRow)
            .onFocusChanged { focused = it.hasFocus },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(PorchRadius.Row)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = PorchColors.Text,
            focusedContainerColor = PorchColors.Focus,
            focusedContentColor = PorchColors.OnFocus,
            pressedContainerColor = PorchColors.Focus,
            pressedContentColor = PorchColors.OnFocus,
        ),
        border = ClickableSurfaceDefaults.border(),
        scale = clickScale(PorchFocus.RowScale),
        glow = ClickableSurfaceDefaults.glow(),
    ) {
        val quiet = if (focused) PorchColors.OnFocus else PorchColors.Muted
        Row(
            Modifier.fillMaxSize().padding(horizontal = PorchSpace.PanelPad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(row.title, style = PorchType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(2f, fill = false))
                if (row.meta.isNotBlank()) {
                    Spacer(Modifier.width(PorchSpace.TileGap))
                    Text(row.meta, style = PorchType.Meta, color = quiet, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
            }
            if (row.followed) {
                Spacer(Modifier.width(PorchSpace.TileGap))
                Text("Following", style = PorchType.Meta, color = if (focused) PorchColors.OnFocus else PorchColors.Accent, maxLines = 1)
            }
            Spacer(Modifier.width(PorchSpace.TileGap))
            val shownColor = when {
                focused -> PorchColors.OnFocus
                row.revealed -> PorchColors.Text
                else -> PorchColors.Faint
            }
            // A shown result shares the row with the title, so a long one (two tennis finals) shortens itself
            // instead of squeezing the title out. "Hidden" is short and keeps its place at the right edge.
            Text(
                row.shown, style = PorchType.Meta, color = shownColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End, modifier = if (row.revealed) Modifier.weight(1f) else Modifier,
            )
        }
    }
}

/** The chips are a lazy row, so bring the selected one into view before asking it for focus. */
private suspend fun focusChip(state: LazyListState, current: String, selectedChip: FocusRequester) {
    val index = SportRules.chips.indexOf(current)
    if (index >= 0 && state.layoutInfo.visibleItemsInfo.none { it.index == index }) {
        state.scrollToItem(index)
        withFrameNanos { }
    }
    selectedChip.tryFocus()
}

private fun neverLoadedOf(schedule: SportSchedule.State) = schedule.updatedAt == null && schedule.failed.isNotEmpty()
