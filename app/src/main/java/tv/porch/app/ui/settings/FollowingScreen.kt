package tv.porch.app.ui.settings

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import tv.porch.app.AppGraph
import tv.porch.app.domain.Tabs
import tv.porch.app.domain.formatHeaderClockLong
import tv.porch.app.ui.components.Pill
import tv.porch.app.ui.components.TopBar
import tv.porch.app.ui.components.rememberNow
import tv.porch.app.ui.theme.PorchColors
import tv.porch.app.ui.theme.PorchSpace
import tv.porch.app.ui.theme.PorchType

/** What the viewer follows. OK on one stops following it. */
@Composable
fun FollowingScreen(graph: AppGraph, onTab: (String) -> Unit) {
    val follows by graph.follows.follows.collectAsStateWithLifecycle()
    val now = rememberNow()
    val firstFocus = remember { FocusRequester() }
    // Focus stays on the first row after one goes, so OK can be pressed down the list.
    // With none left, focus rests on the screen itself, so the remote never has nowhere to be.
    val screenFocus = remember { FocusRequester() }
    LaunchedEffect(follows.size) { runCatching { (if (follows.isEmpty()) screenFocus else firstFocus).requestFocus() } }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Following", clock = formatHeaderClockLong(now), tabs = Tabs.labels, current = "Settings", onTab = onTab)
        Column(
            Modifier
                .focusRequester(screenFocus)
                .focusable()
                .padding(start = PorchSpace.Margin, end = PorchSpace.Margin, top = PorchSpace.SettingsTop)
                .width(PorchSpace.SetupList)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(PorchSpace.CardLineGap),
        ) {
            if (follows.isEmpty()) {
                Text("Nothing followed yet. Press Follow on a programme's details.", style = PorchType.Body, color = PorchColors.TextSecondary)
            } else {
                Text("Press OK on one to stop following it.", style = PorchType.Body, color = PorchColors.TextSecondary)
                follows.forEachIndexed { index, term ->
                    Pill(term, modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier) { graph.follows.remove(term) }
                }
            }
        }
    }
}
