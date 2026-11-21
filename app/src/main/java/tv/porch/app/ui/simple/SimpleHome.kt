package tv.porch.app.ui.simple

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import tv.porch.app.AppGraph
import tv.porch.app.domain.Channel
import tv.porch.app.domain.LiveRows
import tv.porch.app.domain.Tabs
import tv.porch.app.domain.formatHeaderClockLong
import tv.porch.app.player.CatchupRequest
import tv.porch.app.ui.VmFactory
import tv.porch.app.ui.components.LiveRowItem
import tv.porch.app.ui.components.LocalQuickMenu
import tv.porch.app.ui.components.LocalReturnTile
import tv.porch.app.ui.components.OkKeycap
import tv.porch.app.ui.components.Pill
import tv.porch.app.ui.components.TopBar
import tv.porch.app.ui.components.rememberNow
import tv.porch.app.ui.components.tryFocus
import tv.porch.app.ui.home.HomeViewModel
import tv.porch.app.ui.home.endsText
import tv.porch.app.ui.theme.PorchColors
import tv.porch.app.ui.theme.PorchSpace
import tv.porch.app.ui.theme.PorchType

/**
 * Home in simple mode: every favourite, in channel order, with what is on it. One list and nothing else, so the
 * viewer only ever looks for a channel and presses OK. Up and Down in the player zap through this same list.
 */
@Composable
fun SimpleHome(
    graph: AppGraph,
    onTab: (String) -> Unit,
    onGuide: () -> Unit,
    onPlay: (List<Channel>, Channel) -> Unit,
    onWatch: (List<Channel>, Channel, CatchupRequest?, Channel?) -> Unit,
) {
    val model: HomeViewModel = viewModel(factory = VmFactory { HomeViewModel(graph) })
    val ui by model.state.collectAsStateWithLifecycle()
    // The saved Home from last time has no favourites list, so it would flash "No favourites yet".
    val rows = if (ui.fromSnapshot) null else ui.lists?.favourites?.all
    val quick = LocalQuickMenu.current
    val returnTile = LocalReturnTile.current
    val returning = remember { returnTile.armed }
    val homeTab = remember { FocusRequester() }
    val first = remember { FocusRequester() }
    val openGuide = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // First focus: the first channel, or the way into the guide; again whenever the list empties or fills, since the
    // focused place goes. Coming back from the player, the row left keeps it.
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(rows?.isEmpty()) {
        if (rows == null) return@LaunchedEffect
        withFrameNanos { }
        if (!placed && returning) {
            withFrameNanos { }
            if (!returnTile.armed) {
                placed = true
                return@LaunchedEffect
            }
        }
        val target = if (rows.isEmpty()) openGuide else first
        if (!target.tryFocus()) homeTab.tryFocus()
        placed = true
    }

    val zap: List<Channel> = remember(rows) { rows.orEmpty().map { it.channels.first() }.distinctBy { it.id } }
    val hold: (LiveRows.Row) -> Unit = { row ->
        val channel = row.channels.first()
        scope.launch {
            val programme = if (row.programmeId != 0L) graph.repository.programme(row.programmeId) else null
            quick.open(channel, programme = programme, onWatch = { catchup, copy -> onWatch(zap, channel, catchup, copy) })
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = "Your channels",
            clock = formatHeaderClockLong(rememberNow()),
            tabs = Tabs.labels,
            current = "Home",
            onTab = onTab,
            selectedTab = homeTab,
        )
        Text(
            "Your channels",
            style = PorchType.Section,
            modifier = Modifier.padding(start = PorchSpace.Margin, top = PorchSpace.ListGap),
        )
        when {
            rows == null -> Text(
                "Loading your channels…",
                style = PorchType.Body,
                color = PorchColors.Muted,
                modifier = Modifier.padding(horizontal = PorchSpace.Margin, vertical = PorchSpace.TileGap).weight(1f),
            )
            rows.isEmpty() -> Column(Modifier.weight(1f).padding(horizontal = PorchSpace.Margin, vertical = PorchSpace.TileGap)) {
                Text(
                    "No favourites yet. Hold OK on a channel in the Full guide to add one.",
                    style = PorchType.Body,
                    color = PorchColors.TextSecondary,
                )
                Spacer(Modifier.height(PorchSpace.TileGap))
                Pill("Open Full guide", modifier = Modifier.focusRequester(openGuide), onClick = onGuide)
            }
            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = PorchSpace.Margin, vertical = PorchSpace.FocusRoom),
                verticalArrangement = Arrangement.spacedBy(PorchSpace.ListGap),
                modifier = Modifier.weight(1f),
            ) {
                itemsIndexed(rows, key = { _, row -> row.channels.first().id }) { index, row ->
                    LiveRowItem(
                        row = row,
                        trailing = endsText(row),
                        liveLabel = false,
                        wideSlot = false,
                        modifier = if (index == 0) Modifier.focusRequester(first).focusProperties { up = homeTab } else Modifier,
                        onFocused = {},
                        onOpenOthers = null,
                        onPlay = { onPlay(zap, row.channels.first()) },
                        onLong = { hold(row) },
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = PorchSpace.Margin, vertical = PorchSpace.ListGap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PorchSpace.HomeTilePad),
        ) {
            OkKeycap()
            Text("plays   ·   Back leaves Porch", style = PorchType.Hint)
        }
    }
}
