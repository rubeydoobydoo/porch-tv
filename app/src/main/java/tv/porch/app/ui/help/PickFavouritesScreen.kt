package tv.porch.app.ui.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.porch.app.AppGraph
import tv.porch.app.domain.FavouriteTopics
import tv.porch.app.domain.Kinds
import tv.porch.app.domain.Tabs
import tv.porch.app.domain.formatHeaderClockLong
import tv.porch.app.ui.components.Pill
import tv.porch.app.ui.components.TopBar
import tv.porch.app.ui.components.rememberNow
import tv.porch.app.ui.theme.PorchColors
import tv.porch.app.ui.theme.PorchSpace
import tv.porch.app.ui.theme.PorchType

/** Shown once after setup, and from Settings any time. Back or Done leaves it; nothing has to be picked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PickFavouritesScreen(graph: AppGraph, onTab: (String) -> Unit, onDone: () -> Unit) {
    val done = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf(emptySet<Long>()) }
    val sections by produceState(emptyList<FavouriteTopics.Section>()) {
        val channels = graph.repository.observeChannels().first { it.isNotEmpty() }
        // About 49,000 channels: worked out off the main thread. Channels already favourited show ticked.
        val found = withContext(Dispatchers.Default) { FavouriteTopics.sections(channels, Kinds::of) }
        picked = found.flatMap { it.channels }.filter { it.favourite }.mapTo(HashSet()) { it.id }
        value = found
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { done.requestFocus() }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(
            // Short on purpose: "Pick your channels" runs into the tabs and is cut off.
            title = "Pick channels",
            clock = formatHeaderClockLong(rememberNow()),
            tabs = Tabs.labels,
            current = "Settings",
            onTab = onTab,
        )
        Column(Modifier.padding(horizontal = PorchSpace.Margin), verticalArrangement = Arrangement.spacedBy(PorchSpace.ListGap)) {
            Text("These come first everywhere in Porch. You can change them any time.", style = PorchType.Body, color = PorchColors.Muted)
            // About ten seconds the first time on the Chromecast, while every channel's kind is read.
            if (sections.isEmpty()) Text("Finding channels…", style = PorchType.Body, color = PorchColors.Muted)
            Pill(if (picked.isEmpty()) "Skip for now" else "Done (${picked.size})", modifier = Modifier.focusRequester(done), onClick = onDone)
        }
        LazyColumn(
            Modifier.padding(horizontal = PorchSpace.Margin).padding(top = PorchSpace.ListGap),
            verticalArrangement = Arrangement.spacedBy(PorchSpace.ListGap),
        ) {
            items(sections, key = { it.label }) { section ->
                Column(verticalArrangement = Arrangement.spacedBy(PorchSpace.ListGap)) {
                    Text(section.label, style = PorchType.Section, color = PorchColors.Text)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(PorchSpace.FilterGap),
                        verticalArrangement = Arrangement.spacedBy(PorchSpace.FilterGap),
                    ) {
                        section.channels.forEach { channel ->
                            val on = channel.id in picked
                            Pill(channel.shownTitle, selected = on) {
                                picked = if (on) picked - channel.id else picked + channel.id
                                scope.launch { graph.repository.setFavourite(channel.id, !on) }
                            }
                        }
                    }
                }
            }
        }
    }
}
