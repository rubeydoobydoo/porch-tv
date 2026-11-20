package tv.porch.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import tv.porch.app.AppGraph
import tv.porch.app.domain.Tabs
import tv.porch.app.domain.formatHeaderClockLong
import tv.porch.app.setup.SetupServer
import tv.porch.app.ui.components.DetailCard
import tv.porch.app.ui.components.Pill
import tv.porch.app.ui.components.TopBar
import tv.porch.app.ui.components.rememberNow
import tv.porch.app.ui.theme.PorchColors
import tv.porch.app.ui.theme.PorchSpace
import tv.porch.app.ui.theme.PorchType

/**
 * The key for rating posters on the 24/7 tab. It comes from the phone page, which only runs while this screen is
 * showing. The key is a secret, so this screen says whether one is saved and never what it is.
 */
@Composable
fun PosterRatingsScreen(graph: AppGraph, onTab: (String) -> Unit) {
    val saved by graph.ratings.on.collectAsStateWithLifecycle()
    val result by graph.ratings.result.collectAsStateWithLifecycle()
    val now = rememberNow()
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
        val server = SetupServer.forPosters { key -> graph.ratings.set(key) }
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
            title = "Poster ratings",
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
                    "With your RatingPosterDB key, the 24/7 tab shows posters with the rating on them. Without one, nothing changes.",
                    style = PorchType.Body,
                    color = PorchColors.TextSecondary,
                )
                Spacer(Modifier.height(PorchSpace.CardLineGap))
                Text(
                    result ?: if (saved) "Key saved. Open the 24/7 tab to try it." else "No key saved.",
                    style = PorchType.TileTitle,
                )
                Spacer(Modifier.height(PorchSpace.CardLineGap))
                Pill("Remove key", modifier = Modifier.focusRequester(firstFocus)) { graph.ratings.remove() }
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
                        Text(url.removePrefix("http://") + "posters", style = PorchType.ScreenTitle)
                        Spacer(Modifier.height(PorchSpace.CardLineGap * 2))
                        Text(
                            "Paste your key and press Send. It replaces the saved one. Send it blank to remove it.",
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
