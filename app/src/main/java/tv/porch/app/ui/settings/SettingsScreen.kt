package tv.porch.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tv.porch.app.AppGraph
import tv.porch.app.data.SourceEntity
import tv.porch.app.data.SyncStatus
import tv.porch.app.domain.ChannelFold
import tv.porch.app.domain.FoldCounts
import tv.porch.app.domain.GuideOffsetText
import tv.porch.app.domain.SourceProblem
import tv.porch.app.domain.Tabs
import tv.porch.app.domain.formatHeaderClockLong
import tv.porch.app.domain.formatTime
import tv.porch.app.ui.VmFactory
import tv.porch.app.ui.components.DetailCard
import tv.porch.app.ui.components.Pill
import tv.porch.app.ui.components.TopBar
import tv.porch.app.ui.components.clickScale
import tv.porch.app.ui.components.rememberNow
import tv.porch.app.ui.theme.PorchColors
import tv.porch.app.ui.theme.PorchRadius
import tv.porch.app.ui.theme.PorchSpace
import tv.porch.app.ui.theme.PorchType

private const val OFFSET = "offset"
private const val COMBINE = "combine"

/** Remembers the last focused row so Back from a pushed screen lands where the player was. */
class SettingsViewModel : ViewModel() {
    var lastRow: String? = null
}

@Composable
fun SettingsScreen(
    graph: AppGraph,
    onTab: (String) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onPhone: () -> Unit,
    onChannels: () -> Unit,
    onBackupGuides: () -> Unit,
    onFollowing: () -> Unit,
    onPickFavourites: () -> Unit,
    onPosterRatings: () -> Unit,
    onHelp: () -> Unit,
    /** Turning simple mode on: the code pad. Turning it off happens from the favourites list, with the code. */
    onSimpleMode: () -> Unit,
) {
    val sources by graph.repository.observeSources().collectAsStateWithLifecycle(emptyList())
    val channels by graph.repository.observeChannels().collectAsStateWithLifecycle(emptyList())
    val status by graph.sync.status.collectAsStateWithLifecycle()
    val showWithoutGuide by graph.guideFlags.showWithoutGuide.collectAsStateWithLifecycle()
    val combineCopies by graph.guideFlags.combineCopies.collectAsStateWithLifecycle()
    val matchRefresh by graph.guideFlags.matchRefresh.collectAsStateWithLifecycle()
    val linenLook by graph.guideFlags.linenLook.collectAsStateWithLifecycle()
    // Counted whichever way the switch is set, off the main thread, while this screen is open.
    val foldCounts by produceState<FoldCounts?>(null, channels) {
        value = if (channels.isEmpty()) null else withContext(Dispatchers.Default) { ChannelFold.counts(channels, ChannelFold.familiesInParallel(channels)) }
    }
    val followed by graph.follows.follows.collectAsStateWithLifecycle()
    val backupAddresses by graph.backupGuides.addresses.collectAsStateWithLifecycle()
    val ratingsOn by graph.ratings.on.collectAsStateWithLifecycle()
    val counts = remember(channels) { channels.groupingBy { it.sourceId }.eachCount() }
    val now = rememberNow()
    val scope = rememberCoroutineScope()

    var focusedKey by remember { mutableStateOf("") }
    var sheetId by remember { mutableStateOf<Long?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }
    val sheetSource = sources.firstOrNull { it.id == sheetId }
    val sheetFirst = remember { FocusRequester() }
    val model: SettingsViewModel = viewModel(factory = VmFactory { SettingsViewModel() })
    val rows = remember { mutableMapOf<String, FocusRequester>() }
    fun rowRequester(key: String) = rows.getOrPut(key) { FocusRequester() }
    val bringers = remember { mutableMapOf<String, BringIntoViewRequester>() }
    fun rowModifier(key: String) = Modifier.focusRequester(rowRequester(key)).bringIntoViewRequester(bringers.getOrPut(key) { BringIntoViewRequester() })
    // The row to put focus on: the one we came back from, or the one just after a removed source.
    // A fresh visit lands on Channels, so hiding a group is OK, not three Downs (docs/DECISIONS.md, 19 Nov).
    var wantFocus by remember { mutableStateOf<String?>(model.lastRow ?: "channels") }
    var returnKey by remember { mutableStateOf<String?>(null) }

    fun focus(key: String) {
        focusedKey = key
        model.lastRow = key
    }

    fun closeSheet(next: String? = returnKey) {
        confirmRemove = false
        sheetId = null
        wantFocus = next
    }

    LaunchedEffect(sheetSource?.id) {
        if (sheetSource != null) runCatching { sheetFirst.requestFocus() }
    }
    LaunchedEffect(wantFocus, sheetSource?.id, sources.isNotEmpty()) {
        val key = wantFocus ?: return@LaunchedEffect
        if (sheetSource != null) return@LaunchedEffect
        // A source row only exists once the list has loaded.
        if (key.startsWith("source:") && sources.isEmpty()) return@LaunchedEffect
        val target = if (key.startsWith("source:") && sources.none { "source:${it.id}" == key }) "add" else key
        val focused = if (runCatching { rowRequester(target).requestFocus() }.isSuccess) target else "refresh".also { runCatching { rowRequester(it).requestFocus() } }
        // A row restored below the fold (coming back from a sub-screen) has to scroll into view, once.
        delay(200)
        runCatching { bringers[focused]?.bringIntoView() }
        wantFocus = null
    }
    BackHandler(enabled = sheetSource != null) { closeSheet() }

    // The offset is kept per source. Settings shows the first source's value and moves every source by the same step.
    // Held keys step from the last value written, not from the last database emission, and the writes run one at a time.
    val pending = remember { mutableStateMapOf<Long, Int>() }
    val writes = remember { Mutex() }
    fun current(source: SourceEntity) = pending[source.id] ?: source.guideOffsetMinutes
    fun stepOffset(direction: Int) {
        val targets = sources.map { it.id to GuideOffsetText.step(current(it), direction) }
        targets.forEach { (id, value) -> pending[id] = value }
        scope.launch { writes.withLock { targets.forEach { (id, value) -> graph.repository.setOffset(id, value) } } }
    }
    val offset = sources.firstOrNull()?.let { current(it) } ?: 0
    val varies = sources.map { current(it) }.distinct().size > 1
    val lastSync = sources.mapNotNull { it.lastSyncAt }.maxOrNull()

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = "Settings",
            clock = formatHeaderClockLong(now),
            tabs = Tabs.labels,
            current = "Settings",
            onTab = onTab,
        )
        Row(
            Modifier
                .fillMaxSize()
                .padding(start = PorchSpace.Margin, end = PorchSpace.Margin, top = PorchSpace.SettingsTop),
        ) {
            Column(
                Modifier
                    .width(PorchSpace.SettingsList)
                    .verticalScroll(rememberScrollState())
                    // Inside the scroll, so a label or row is never half-shown under the bar or the screen's edge.
                    .padding(top = PorchSpace.FocusRoom, bottom = PorchSpace.Margin),
                verticalArrangement = Arrangement.spacedBy(PorchSpace.SettingsRowGap),
            ) {
                GroupLabel("Sources", first = true)
                if (sources.isEmpty()) {
                    Text("No sources yet.", style = PorchType.Meta)
                }
                sources.forEach { source ->
                    // A failure is a few plain words here; the whole sentence and the code are in the card.
                    val message = when (val state = status[source.id]) {
                        is SyncStatus.Running -> state.message
                        is SyncStatus.Failed -> state.problem.short
                        else -> null
                    }
                    SettingsRow(
                        label = source.name,
                        secondary = if (source.kind == "xtream") "Xtream login" else "Playlist URL",
                        trailing = message ?: "${"%,d".format(counts[source.id] ?: 0)} channels",
                        modifier = rowModifier("source:${source.id}"),
                        onFocus = { focus("source:${source.id}") },
                    ) {
                        confirmRemove = false
                        returnKey = "source:${source.id}"
                        sheetId = source.id
                    }
                }
                GroupLabel("Actions")
                SettingsRow(
                    "Refresh all sources",
                    trailing = lastSync?.let { "Last ${formatTime(it)}" },
                    modifier = rowModifier("refresh"),
                    onFocus = { focus("refresh") },
                ) { sources.forEach { graph.sync.refresh(it.id) } }
                SettingsRow("Add a source", modifier = rowModifier("add"), onFocus = { focus("add") }, onClick = onAdd)
                SettingsRow("Set up from your phone", modifier = rowModifier("phone"), onFocus = { focus("phone") }, onClick = onPhone)
                SettingsRow("Channels", modifier = rowModifier("channels"), onFocus = { focus("channels") }, onClick = onChannels)
                SettingsRow(
                    "Show channels without a guide",
                    trailing = if (showWithoutGuide) "On" else "Off",
                    modifier = rowModifier("noguide"),
                    onFocus = { focus("noguide") },
                ) { graph.guideFlags.setShowWithoutGuide(!showWithoutGuide) }
                SettingsRow(
                    "Combine copies of a channel",
                    trailing = if (combineCopies) "On" else "Off",
                    modifier = rowModifier(COMBINE),
                    onFocus = { focus(COMBINE) },
                ) { graph.guideFlags.setCombineCopies(!combineCopies) }
                SettingsRow(
                    "Match TV to the channel",
                    trailing = if (matchRefresh) "On" else "Off",
                    modifier = rowModifier("refreshmatch"),
                    onFocus = { focus("refreshmatch") },
                ) { graph.guideFlags.setMatchRefresh(!matchRefresh) }
                SettingsRow(
                    "Appearance",
                    trailing = if (linenLook) "Linen" else "Dark",
                    modifier = rowModifier("appearance"),
                    onFocus = { focus("appearance") },
                ) { graph.guideFlags.setLinenLook(!linenLook) }
                // Settings is out of reach in simple mode, so this row only ever turns it on.
                SettingsRow(
                    "Simple mode",
                    trailing = "Off",
                    modifier = rowModifier("simple"),
                    onFocus = { focus("simple") },
                    onClick = onSimpleMode,
                )
                GroupLabel("Guide and help")
                SettingsRow(
                    "Guide offset",
                    trailing = if (varies) "Varies" else GuideOffsetText.label(offset),
                    modifier = rowModifier(OFFSET).onPreviewKeyEvent { event ->
                        val direction = when (event.key) {
                            Key.DirectionLeft -> -1
                            Key.DirectionRight -> 1
                            else -> 0
                        }
                        if (direction != 0) {
                            if (event.type == KeyEventType.KeyDown) stepOffset(direction)
                            true
                        } else {
                            false
                        }
                    },
                    onFocus = { focus(OFFSET) },
                ) {}
                SettingsRow(
                    "Backup guides",
                    trailing = if (backupAddresses.isEmpty()) "None" else "${backupAddresses.size} added",
                    modifier = rowModifier("backup"),
                    onFocus = { focus("backup") },
                    onClick = onBackupGuides,
                )
                SettingsRow(
                    "Following",
                    trailing = if (followed.isEmpty()) "None" else "${followed.size} followed",
                    modifier = rowModifier("following"),
                    onFocus = { focus("following") },
                    onClick = onFollowing,
                )
                SettingsRow(
                    "Pick your channels",
                    trailing = channels.count { it.favourite }.let { if (it == 0) "None" else "$it favourites" },
                    modifier = rowModifier("pick"),
                    onFocus = { focus("pick") },
                    onClick = onPickFavourites,
                )
                SettingsRow(
                    "Poster ratings",
                    trailing = if (ratingsOn) "On" else "Off",
                    modifier = rowModifier("posters"),
                    onFocus = { focus("posters") },
                    onClick = onPosterRatings,
                )
                SettingsRow("How Porch works", modifier = rowModifier("help"), onFocus = { focus("help") }, onClick = onHelp)
                Spacer(Modifier.height(PorchSpace.SettingsTop))
            }
            Spacer(Modifier.width(PorchSpace.SettingsColumnGap))
            DetailCard(Modifier.width(PorchSpace.SettingsCard)) {
                if (sheetSource != null) {
                    SourceSheet(
                        source = sheetSource,
                        problem = (status[sheetSource.id] as? SyncStatus.Failed)?.problem,
                        confirming = confirmRemove,
                        firstFocus = sheetFirst,
                        onRefresh = { graph.sync.refresh(sheetSource.id) },
                        onEdit = {
                            closeSheet()
                            onEdit(sheetSource.id)
                        },
                        onRemove = {
                            if (confirmRemove) {
                                // Focus goes to the next source, or to Add a source when it was the last.
                                val next = sources.getOrNull(sources.indexOfFirst { it.id == sheetSource.id } + 1)
                                    ?.let { "source:${it.id}" } ?: "add"
                                scope.launch {
                                    graph.repository.deleteSource(sheetSource.id)
                                    graph.sync.forget(sheetSource.id)
                                    graph.homeSnapshot.delete()
                                    closeSheet(next)
                                }
                            } else {
                                confirmRemove = true
                            }
                        },
                        onClose = { closeSheet() },
                    )
                } else if (focusedKey == OFFSET) {
                    Text("Guide offset", style = PorchType.ScreenTitle)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = PorchSpace.CardLineGap * 2),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("–", style = PorchType.ScreenTitle, color = PorchColors.Muted)
                        Text(if (varies) "Varies" else if (offset == 0) "0 min" else GuideOffsetText.label(offset), style = PorchType.ScreenTitle)
                        Text("+", style = PorchType.ScreenTitle, color = PorchColors.Muted)
                    }
                    Text(if (varies) "Your sources have different offsets. Each step moves them all." else GuideOffsetText.sentence(offset), style = PorchType.Body, color = PorchColors.TextSecondary)
                    Spacer(Modifier.height(PorchSpace.CardLineGap))
                    Text("Left and Right change it by ${GuideOffsetText.STEP_MINUTES} minutes.", style = PorchType.Hint)
                } else {
                    val focusedSource = sources.firstOrNull { "source:${it.id}" == focusedKey }
                    val problem = focusedSource?.let { (status[it.id] as? SyncStatus.Failed)?.problem }
                    val (title, line) = describe(focusedKey, focusedSource)
                    Text(title, style = PorchType.ScreenTitle)
                    Spacer(Modifier.height(PorchSpace.CardLineGap))
                    Text(problem?.line ?: line, style = PorchType.Body, color = PorchColors.TextSecondary)
                    if (problem != null) {
                        Spacer(Modifier.height(PorchSpace.CardLineGap))
                        Text("Press OK to edit or refresh it.", style = PorchType.Body, color = PorchColors.TextSecondary)
                        problem.code?.let { code ->
                            Spacer(Modifier.height(PorchSpace.CardLineGap))
                            Text("Error code $code", style = PorchType.Hint)
                        }
                    }
                    if (focusedKey == COMBINE) {
                        foldCounts?.let { counts ->
                            Spacer(Modifier.height(PorchSpace.CardLineGap))
                            Text(combineCountLine(counts, combineCopies), style = PorchType.Body, color = PorchColors.TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

private fun combineCountLine(counts: FoldCounts, on: Boolean): String {
    val total = "%,d".format(counts.total)
    val shown = "%,d".format(counts.shown)
    return if (on) "$total channels, shown as $shown" else "$total channels. On, they'd show as $shown"
}

private fun describe(key: String, source: SourceEntity?): Pair<String, String> = when {
    source != null -> source.name to "Press OK to refresh, edit or remove this source."
    key == "refresh" -> "Refresh all sources" to "Fetches the channels and the guide again from every source."
    key == "add" -> "Add a source" to "Add a playlist address or an Xtream login."
    key == "phone" -> "Set up from your phone" to "Type the login on your phone instead of the remote."
    key == "channels" -> "Channels" to "Choose which languages and groups Porch shows."
    key == "noguide" -> "Show channels without a guide" to "Press OK to show or hide channels that have no programme listings."
    key == COMBINE -> "Combine copies of a channel" to "Shows one row for each channel, using its best copy. If a stream fails, the next copy takes over. Hold OK for the others."
    key == "appearance" -> "Appearance" to "Press OK to switch between Dark and Linen, a lighter look. The picture always stays dark."
    key == "simple" -> "Simple mode" to "For someone who just wants their channels: favourites and the guide, nothing else. You choose a 4-digit code, and only that code turns it off."
    key == "refreshmatch" -> "Match TV to the channel" to "Changes the TV's refresh rate to suit each channel, so movement looks smoother. The screen can blink for a moment when it changes."
    key == "backup" -> "Backup guides" to "Add outside guide files for channels your provider's guide misses or has run out on."
    key == "following" -> "Following" to "Names and titles you follow. Home lists them when they are on now or coming up. Press OK on one to stop following it."
    key == "pick" -> "Pick your channels" to "A short list of channels by sport, league and kind. The ones you pick come first everywhere in Porch."
    key == "posters" -> "Poster ratings" to "Add your RatingPosterDB key to show posters with ratings on the 24/7 tab."
    key == "help" -> "How Porch works" to "A short tour of Home, the guide and the player."
    else -> "Settings" to "Pick a row to see what it does."
}

@Composable
private fun GroupLabel(text: String, first: Boolean = false) {
    Text(
        text,
        style = PorchType.Section,
        color = PorchColors.TextSecondary,
        modifier = Modifier.padding(
            top = if (first) 0.dp else PorchSpace.SettingsGroupGap,
            start = PorchSpace.SettingsGroupLabelGap,
        ),
    )
}

@Composable
private fun SettingsRow(
    label: String,
    secondary: String? = null,
    trailing: String? = null,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(PorchRadius.Row)
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(PorchSpace.SettingsRow)
            .onFocusChanged {
                focused = it.hasFocus
                if (it.hasFocus) onFocus()
            },
        shape = ClickableSurfaceDefaults.shape(shape),
        // Focus is the solid light fill on every screen (DESIGN.md), not a ring.
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
        val quiet = if (focused) PorchColors.OnFocus else PorchColors.Muted
        Row(
            Modifier.fillMaxSize().padding(horizontal = PorchSpace.SettingsRowPadX),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = PorchType.TileTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (secondary != null) {
                Spacer(Modifier.width(PorchSpace.CardLineGap))
                Text(secondary, style = PorchType.Meta, color = quiet, maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            if (trailing != null) {
                Text(trailing, style = PorchType.Meta, color = quiet, maxLines = 1, textAlign = TextAlign.End)
            }
        }
    }
}

/**
 * The action sheet for one source: Refresh, Edit, Remove with a confirm, and Close. A source whose last refresh failed says
 * why, and focus starts on Edit, because the fix is usually a typo.
 */
@Composable
private fun SourceSheet(
    source: SourceEntity,
    problem: SourceProblem?,
    confirming: Boolean,
    firstFocus: FocusRequester,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
) {
    Text(source.name, style = PorchType.ScreenTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(PorchSpace.CardLineGap))
    val line = when {
        confirming -> "Remove it and its favourites?"
        problem != null -> problem.line
        else -> "Refresh it, edit it, or remove it from Porch."
    }
    Text(line, style = PorchType.Body, color = PorchColors.TextSecondary)
    Spacer(Modifier.height(PorchSpace.CardLineGap * 2))
    Column(verticalArrangement = Arrangement.spacedBy(PorchSpace.CardLineGap)) {
        val startOnEdit = problem != null
        Pill("Refresh", modifier = if (startOnEdit) Modifier else Modifier.focusRequester(firstFocus), onClick = onRefresh)
        Pill("Edit", modifier = if (startOnEdit) Modifier.focusRequester(firstFocus) else Modifier, onClick = onEdit)
        Pill(if (confirming) "Yes, remove it" else "Remove", onClick = onRemove)
        Pill("Close", onClick = onClose)
    }
}
