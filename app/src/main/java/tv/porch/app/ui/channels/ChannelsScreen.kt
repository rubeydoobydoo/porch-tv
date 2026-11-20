package tv.porch.app.ui.channels

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import tv.porch.app.AppGraph
import tv.porch.app.domain.ChannelNames
import tv.porch.app.domain.ChannelsChoice
import tv.porch.app.domain.ChoiceLabels
import tv.porch.app.domain.LanguageRow
import tv.porch.app.ui.components.HintBar
import tv.porch.app.ui.components.Pill
import tv.porch.app.ui.theme.PorchSpace
import tv.porch.app.ui.theme.PorchType

/** A source's group counts, tagged with the source they are for so a slow load can't show another source's groups. */
private class Loaded(val sourceId: Long, val counts: Map<String, Int>, val languages: List<LanguageRow>)

private val NotLoaded = Loaded(-1L, emptyMap(), emptyList())

/** How long the language in focus must stay put before the group list on the right swaps, so holding Down stays smooth. */
private const val SETTLE_MS = 120L

private fun number(value: Int): String = String.format(Locale.US, "%,d", value)

/** Choose what shows: languages on the left, the groups of the one in focus on the right. */
@Composable
fun ChannelsScreen(graph: AppGraph) {
    val sources by graph.repository.observeSources().collectAsStateWithLifecycle(emptyList())
    val hiddenRows by graph.repository.observeHidden().collectAsStateWithLifecycle(emptyList())
    val chosenKeys by graph.repository.observeChosenGroups().collectAsStateWithLifecycle(emptySet())
    var sourceId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(sources) {
        if (sourceId == null || sources.none { it.id == sourceId }) sourceId = sources.firstOrNull()?.id
    }
    val source = sources.firstOrNull { it.id == sourceId }
    // An old single-language filter becomes picks once, so the viewer sees the same channels as before.
    LaunchedEffect(source?.id, source?.languageFilter) {
        val id = source?.id
        if (id != null && !source.languageFilter.isNullOrBlank()) graph.repository.convertLanguageFilter(id)
    }
    val loaded by remember(sourceId) {
        val id = sourceId ?: -1L
        graph.repository.observeGroupCounts(id).map { Loaded(id, it, ChannelsChoice.languages(it, Locale.getDefault().country)) }.flowOn(Dispatchers.Default)
    }.collectAsStateWithLifecycle(NotLoaded)
    // Nothing is shown or acted on until the counts are for the source in view.
    val ready = sourceId != null && loaded.sourceId == sourceId
    val counts = if (ready) loaded.counts else emptyMap()
    val languages = if (ready) loaded.languages else emptyList()
    val chosen = remember(chosenKeys, sourceId, counts) {
        ChannelsChoice.effective(chosenKeys.filter { it.sourceId == sourceId }.mapTo(HashSet()) { it.group }, counts.keys)
    }
    val hidden = remember(hiddenRows, sourceId) { hiddenRows.filter { it.sourceId == sourceId }.mapTo(HashSet()) { it.groupName } }
    val total = remember(counts) { counts.values.sum() }
    val keeping = remember(counts, chosen, hidden) { ChannelsChoice.keeping(counts, chosen, hidden) }

    // The row in focus changes at once; the list on the right follows once it has settled.
    var focused by remember { mutableStateOf<String?>(null) }
    var shown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(languages) {
        if (languages.none { it.label == focused }) {
            focused = languages.firstOrNull()?.label
            shown = focused
        }
    }
    LaunchedEffect(focused) {
        delay(SETTLE_MS)
        shown = focused
    }
    val current = languages.firstOrNull { it.label == shown }
    val scope = rememberCoroutineScope()

    val first = remember { FocusRequester() }
    val groupList = remember { FocusRequester() }
    val firstGroup = remember { FocusRequester() }
    val leftRows = remember { mutableMapOf<String, FocusRequester>() }
    // Focus the list once, when it first has rows; switching source afterwards leaves focus on the source pills.
    var focusedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(languages.isNotEmpty()) {
        if (languages.isNotEmpty() && !focusedOnce) {
            focusedOnce = true
            first.requestFocus()
        }
    }

    Column(Modifier.fillMaxSize().padding(top = PorchSpace.Margin / 2)) {
        Column(Modifier.padding(horizontal = PorchSpace.Margin)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Channels", style = PorchType.ScreenTitle)
                Row(horizontalArrangement = Arrangement.spacedBy(PorchSpace.TileGap), verticalAlignment = Alignment.CenterVertically) {
                    Text("Keeping ${number(keeping)} of ${number(total)}", style = PorchType.Meta)
                    Pill("Use the suggested set") {
                        val id = sourceId ?: return@Pill
                        scope.launch { graph.repository.useStartingPick(id) }
                    }
                }
            }
            if (sources.size > 1) {
                Spacer(Modifier.height(PorchSpace.TileGap))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sources.forEach { item ->
                        Pill(item.name, selected = item.id == sourceId) {
                            sourceId = item.id
                            focused = null
                            shown = null
                        }
                    }
                }
            }
            Spacer(Modifier.height(PorchSpace.TileGap))
        }
        Row(Modifier.weight(1f).padding(horizontal = PorchSpace.Margin)) {
            Column(Modifier.width(360.dp)) {
                Text("Countries and languages", style = PorchType.Section)
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(PorchSpace.ListGap),
                    // Room above for the lifted first row and below so the last row clears the hint bar.
                    contentPadding = PaddingValues(top = PorchSpace.FocusRoom, bottom = PorchSpace.Margin),
                    modifier = Modifier.weight(1f),
                ) {
                    itemsIndexed(languages, key = { _, row -> row.label }) { index, row ->
                        val requester = leftRows.getOrPut(row.label) { FocusRequester() }
                        // A language with groups showing gets the tick; one with none goes quiet. The open row also carries an arrow.
                        val open = row.label == focused
                        val anyShowing = row.groups.any { ChannelsChoice.showing(it, chosen, hidden) }
                        Pill(
                            "${row.name}   ${number(row.channels)}" + if (open) "  ›" else "",
                            selected = anyShowing,
                            hidden = !anyShowing,
                            open = open,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusProperties { right = groupList }
                                // Right before the list has settled would land on a list about to be replaced,
                                // and focus would fall to the top of the screen. Swap it first, then move.
                                .onPreviewKeyEvent { event ->
                                    if (event.key != Key.DirectionRight || event.type != KeyEventType.KeyDown || shown == focused) {
                                        return@onPreviewKeyEvent false
                                    }
                                    shown = focused
                                    scope.launch {
                                        withFrameNanos { }
                                        withFrameNanos { }
                                        runCatching { groupList.requestFocus() }
                                    }
                                    true
                                }
                                .focusRequester(requester)
                                .let { if (index == 0) it.focusRequester(first) else it }
                                .onFocusChanged { if (it.hasFocus) focused = row.label },
                        ) {
                            val id = sourceId ?: return@Pill
                            scope.launch { graph.repository.setGroupsShowing(id, row.groups, null) }
                        }
                    }
                }
            }
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f)) {
                Text(current?.name ?: "Groups", style = PorchType.Section)
                Spacer(Modifier.height(8.dp))
                // A new list per language, so it starts at the top. Entering it lands on the row last in focus, or the first.
                key(shown) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(PorchSpace.ListGap),
                        contentPadding = PaddingValues(top = PorchSpace.FocusRoom, bottom = PorchSpace.Margin),
                        modifier = Modifier.weight(1f).focusRequester(groupList).focusRestorer(firstGroup),
                    ) {
                        itemsIndexed(current?.groups.orEmpty(), key = { _, group -> group }) { index, group ->
                            val showing = ChannelsChoice.showing(group, chosen, hidden)
                            // Every row says its state in words: this was the first thing Ruben couldn't tell on the TV.
                            Pill(
                                "${ChoiceLabels.group(hidden = !showing)}   ${ChannelNames.groupRow(group, current?.label)}   (${number(counts[group] ?: 0)})",
                                selected = showing,
                                hidden = !showing,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusProperties { left = focused?.let { leftRows[it] } ?: FocusRequester.Default }
                                    .let { if (index == 0) it.focusRequester(firstGroup) else it },
                            ) {
                                val id = sourceId ?: return@Pill
                                // Send the state this row is asking for, so a second quick press asks for the same thing.
                                scope.launch { graph.repository.setGroupsShowing(id, listOf(group), !showing) }
                            }
                        }
                    }
                }
            }
        }
        HintBar(
            "OK switches a group or a whole language" +
                if (chosen.isNotEmpty()) "   ·   New groups from your provider stay hidden until you switch them on" else "",
            "Back returns to Settings",
        )
    }
}
