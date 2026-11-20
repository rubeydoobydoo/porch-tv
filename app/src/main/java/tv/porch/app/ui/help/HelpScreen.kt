package tv.porch.app.ui.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.tv.material3.Text
import tv.porch.app.domain.HelpText
import tv.porch.app.domain.Tabs
import tv.porch.app.domain.formatHeaderClockLong
import tv.porch.app.ui.components.Pill
import tv.porch.app.ui.components.TopBar
import tv.porch.app.ui.components.rememberNow
import tv.porch.app.ui.theme.PorchColors
import tv.porch.app.ui.theme.PorchSpace
import tv.porch.app.ui.theme.PorchType

@Composable
fun HelpScreen(onTab: (String) -> Unit, onDone: () -> Unit) {
    val gotIt = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    // Ask once the first frame is laid out (asking earlier finds nothing to focus), and again if focus is ever lost.
    LaunchedEffect(hasFocus) {
        if (hasFocus) return@LaunchedEffect
        withFrameNanos { }
        runCatching { gotIt.requestFocus() }
    }

    Column(Modifier.fillMaxSize().onFocusChanged { hasFocus = it.hasFocus }) {
        TopBar(
            title = "How Porch works",
            clock = formatHeaderClockLong(rememberNow()),
            tabs = Tabs.labels,
            current = "Settings",
            onTab = onTab,
        )
        Column(Modifier.padding(start = PorchSpace.Margin, end = PorchSpace.Margin, top = PorchSpace.HelpTop)) {
            Text("Three things, then you're set.", style = PorchType.Body, color = PorchColors.Muted)
            Row(Modifier.padding(top = PorchSpace.HelpIntroGap)) {
                HelpText.columns.forEach { (title, lines) ->
                    Column(Modifier.width(PorchSpace.HelpColumn)) {
                        Text(title, style = PorchType.Section, color = PorchColors.Text)
                        Column(
                            Modifier.padding(top = PorchSpace.HelpTitleGap),
                            verticalArrangement = Arrangement.spacedBy(PorchSpace.HelpLineGap),
                        ) {
                            lines.forEach { Text(it, style = PorchType.Body, color = PorchColors.TextSecondary) }
                        }
                    }
                }
            }
            Pill(
                "Got it",
                modifier = Modifier.padding(top = PorchSpace.HelpButtonGap).focusRequester(gotIt),
                onClick = onDone,
            )
        }
    }
}
