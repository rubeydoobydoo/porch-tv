package tv.porch.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import tv.porch.app.AppGraph
import tv.porch.app.domain.BackupGuide
import tv.porch.app.domain.Tabs
import tv.porch.app.domain.formatHeaderClockLong
import tv.porch.app.domain.formatTime
import tv.porch.app.setup.SetupServer
import tv.porch.app.ui.components.DetailCard
import tv.porch.app.ui.components.Pill
import tv.porch.app.ui.components.TopBar
import tv.porch.app.ui.components.rememberNow
import tv.porch.app.ui.theme.PorchColors
import tv.porch.app.ui.theme.PorchSpace
import tv.porch.app.ui.theme.PorchType

/** Outside guide files. Addresses come from the phone page, which only runs while this screen is showing. Only hosts are shown here. */
@Composable
fun BackupGuidesScreen(graph: AppGraph, onTab: (String) -> Unit) {
    val addresses by graph.backupGuides.addresses.collectAsStateWithLifecycle()
    val result by graph.backupGuides.result.collectAsStateWithLifecycle()
    val sources by graph.repository.observeSources().collectAsStateWithLifecycle(emptyList())
    val status by graph.sync.status.collectAsStateWithLifecycle()
    val now = rememberNow()
    var confirmRemove by remember { mutableStateOf(false) }
    var removed by remember { mutableStateOf(false) }
    var phoneOn by remember { mutableStateOf(true) }
    var phoneAddress by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    // The phone page is plain HTTP, so it closes the moment the app leaves the screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) phoneOn = false
            if (event == Lifecycle.Event.ON_START) phoneOn = true
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(phoneOn) {
        if (!phoneOn) return@DisposableEffect onDispose { }
        val server = SetupServer.forGuides { list ->
            graph.backupGuides.setAddresses(list)
            removed = list.isEmpty()
        }
        try {
            phoneAddress = server.start()
        } catch (failed: Exception) {
            server.stop()
            phoneError = "Phone setup couldn't start."
        }
        onDispose { server.stop() }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = "Backup guides",
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
                    .width(PorchSpace.SetupList)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(PorchSpace.CardLineGap),
            ) {
                Text(
                    "Outside guide files fill in channels your provider's guide misses or has run out on.",
                    style = PorchType.Body,
                    color = PorchColors.TextSecondary,
                )
                Spacer(Modifier.height(PorchSpace.CardLineGap))
                if (addresses.isEmpty()) {
                    Text("None added.", style = PorchType.TileTitle)
                } else {
                    addresses.forEach { address ->
                        Text(BackupGuide.host(address), style = PorchType.TileTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(PorchSpace.CardLineGap))
                result?.let { Text(BackupGuide.resultLine(it, formatTime(it.at)), style = PorchType.Body, color = PorchColors.TextSecondary) }
                if (removed && addresses.isEmpty()) {
                    Text("Removed. Porch stops using them at the next refresh.", style = PorchType.Body, color = PorchColors.TextSecondary)
                }
                graph.sync.line(status)?.let { Text(it, style = PorchType.Hint) }
                Spacer(Modifier.height(PorchSpace.CardLineGap))
                Row(horizontalArrangement = Arrangement.spacedBy(PorchSpace.SetupButtonGap)) {
                    Pill("Refresh now", modifier = Modifier.focusRequester(firstFocus)) {
                        sources.forEach { graph.sync.refresh(it.id) }
                    }
                    Pill(
                        if (confirmRemove) "Yes, remove all" else "Remove all",
                        modifier = Modifier.onFocusChanged { if (!it.hasFocus) confirmRemove = false },
                    ) {
                        if (confirmRemove) {
                            graph.backupGuides.setAddresses(emptyList())
                            removed = true
                            confirmRemove = false
                        } else {
                            confirmRemove = true
                        }
                    }
                }
                Spacer(Modifier.height(PorchSpace.SettingsTop))
            }
            Spacer(Modifier.width(PorchSpace.SetupColumnGap))
            Column(Modifier.width(PorchSpace.SetupCard)) {
                DetailCard {
                    Text("Use your phone", style = PorchType.ScreenTitle)
                    Spacer(Modifier.height(PorchSpace.CardLineGap))
                    Text("On a phone on the same Wi-Fi, open:", style = PorchType.Body, color = PorchColors.TextSecondary)
                    Spacer(Modifier.height(PorchSpace.CardLineGap * 2))
                    val url = phoneAddress
                    if (url == null) {
                        Text(phoneError ?: "Starting…", style = PorchType.Body, color = PorchColors.TextSecondary)
                    } else {
                        Text(url.removePrefix("http://") + "guides", style = PorchType.ScreenTitle)
                        Spacer(Modifier.height(PorchSpace.CardLineGap * 2))
                        Text(
                            "Paste one address per line, up to ${BackupGuide.MAX_ADDRESSES}, and press Send. It replaces the list.",
                            style = PorchType.Body,
                            color = PorchColors.TextSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(PorchSpace.CardLineGap))
                Text("The phone page has a new address each time. Only use it on a network you trust.", style = PorchType.Hint)
            }
        }
    }
}
