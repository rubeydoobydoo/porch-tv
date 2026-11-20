package tv.porch.app.ui.setup

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import tv.porch.app.AppGraph
import tv.porch.app.data.SourceEdit
import tv.porch.app.domain.SourceDraft
import tv.porch.app.domain.Tabs
import tv.porch.app.domain.formatHeaderClockLong
import tv.porch.app.setup.SetupServer
import tv.porch.app.ui.components.DetailCard
import tv.porch.app.ui.components.Pill
import tv.porch.app.ui.components.TopBar
import tv.porch.app.ui.components.TvField
import tv.porch.app.ui.components.rememberNow
import tv.porch.app.ui.theme.PorchColors
import tv.porch.app.ui.theme.PorchSpace
import tv.porch.app.ui.theme.PorchType

/**
 * Add a source, or edit one when [editId] is set. An edit keeps the source's id, so favourites, hidden groups, moved channels
 * and resume points stay; the kind can't change, and there is no phone page (it would carry the password over plain HTTP).
 * The phone-setup page is plain HTTP, so it is opt-in: it starts when [startPhone] is set
 * (Settings' "Set up from your phone") or when the player presses "Start phone setup", and stops when this screen closes. [onCancel] is null on first launch, when there is nowhere to go back to.
 */
@Composable
fun SetupScreen(
    graph: AppGraph,
    startPhone: Boolean,
    onTab: (String) -> Unit,
    onDone: () -> Unit,
    onCancel: (() -> Unit)?,
    editId: Long? = null,
) {
    val editing = editId != null
    var kind by remember { mutableStateOf("m3u") }
    var name by remember { mutableStateOf("") }
    var playlist by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var guide by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var phoneOn by remember { mutableStateOf(startPhone) }
    val sources by graph.repository.observeSources().collectAsStateWithLifecycle(emptyList())
    // The phone page is plain HTTP, so it closes the moment the app leaves the screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) phoneOn = false }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var address by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val now = rememberNow()
    val firstFocus = remember { FocusRequester() }
    // Down from the last field lands on Save, not on Cancel, which sits nearer to it on screen and would throw the edit away.
    val saveFocus = remember { FocusRequester() }
    // An edit fills the form from the saved source first; focus waits until the fields are there.
    var loaded by remember { mutableStateOf(!editing) }
    LaunchedEffect(editId) {
        if (editId != null) {
            val source = graph.repository.source(editId)
            if (source == null) {
                onCancel?.invoke()
                return@LaunchedEffect
            }
            val draft = SourceEdit.draftFor(source)
            kind = draft.kind
            name = draft.name
            playlist = draft.m3uUrl
            server = draft.server
            username = draft.username
            password = draft.password
            guide = draft.xmltvUrl
            loaded = true
        }
    }
    LaunchedEffect(loaded) { if (loaded) runCatching { firstFocus.requestFocus() } }

    fun save() {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                val draft = SourceDraft(
                    name = name,
                    kind = kind,
                    m3uUrl = playlist,
                    server = server,
                    username = username,
                    password = password,
                    xmltvUrl = guide,
                )
                if (editId != null) {
                    if (graph.repository.editSource(editId, draft)) {
                        graph.movies.forgetFetched(editId)
                        graph.shows.forgetFetched(editId)
                    }
                    // A refresh already running finishes first, then one more runs with the new details.
                    graph.sync.refresh(editId, rerun = true)
                } else {
                    graph.sync.refresh(graph.repository.addSource(draft))
                }
                onDone()
            } catch (errorSaving: Exception) {
                error = errorSaving.message ?: "Couldn't save that source."
                busy = false
            }
        }
    }

    DisposableEffect(phoneOn) {
        if (!phoneOn) return@DisposableEffect onDispose { }
        val gate = java.util.concurrent.atomic.AtomicBoolean(false)
        val server = SetupServer { draft ->
            if (!gate.compareAndSet(false, true)) return@SetupServer
            scope.launch {
                try {
                    val id = graph.repository.addSource(draft)
                    graph.sync.refresh(id)
                    onDone()
                } catch (errorSaving: Exception) {
                    gate.set(false)
                    phoneError = errorSaving.message ?: "Couldn't save that source."
                }
            }
        }
        try {
            address = server.start()
        } catch (failed: Exception) {
            server.stop()
            phoneError = failed.message ?: "Phone setup couldn't start."
        }
        onDispose { server.stop() }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = if (editing) "Edit source" else "Add a source",
            clock = formatHeaderClockLong(now),
            // On first launch the tabs lead to empty screens, so they wait until there is a source.
            tabs = if (sources.isEmpty()) emptyList() else Tabs.labels,
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
                verticalArrangement = Arrangement.spacedBy(PorchSpace.SetupFieldGap),
            ) {
                if (!loaded) return@Column
                if (!editing) {
                    Row(horizontalArrangement = Arrangement.spacedBy(PorchSpace.ListGap * 2)) {
                        Pill("Playlist URL", selected = kind == "m3u", restColor = PorchColors.Muted, modifier = Modifier.focusRequester(firstFocus)) { kind = "m3u" }
                        Pill("Xtream login", selected = kind == "xtream", restColor = PorchColors.Muted) { kind = "xtream" }
                    }
                }
                // Editing has no kind pills, so the first field takes the first focus.
                val first = if (editing) Modifier.focusRequester(firstFocus) else Modifier
                if (kind == "m3u") {
                    TvField("Playlist URL", playlist, { playlist = it }, modifier = first)
                    TvField("Guide URL", guide, { guide = it })
                    TvField("Name (optional)", name, { name = it }, modifier = Modifier.focusProperties { down = saveFocus }, onDone = ::save)
                } else {
                    TvField("Server", server, { server = it }, modifier = first)
                    TvField("Username", username, { username = it })
                    TvField("Password", password, { password = it }, password = true)
                    TvField(
                        "Guide URL (optional)",
                        guide,
                        { guide = it },
                        placeholder = "Leave empty to use the one from the login",
                    )
                    TvField("Name", name, { name = it }, modifier = Modifier.focusProperties { down = saveFocus }, onDone = ::save)
                }
                error?.let { Text(it, style = PorchType.Body, color = PorchColors.TextSecondary) }
                Row(horizontalArrangement = Arrangement.spacedBy(PorchSpace.SetupButtonGap)) {
                    Pill(if (busy) "Saving…" else if (editing) "Save and refresh" else "Save and import", modifier = Modifier.focusRequester(saveFocus), onClick = ::save)
                    if (onCancel != null) Pill("Cancel", onClick = onCancel)
                }
                Spacer(Modifier.height(PorchSpace.SettingsTop))
            }
            Spacer(Modifier.width(PorchSpace.SetupColumnGap))
            if (editing) {
                Column(Modifier.width(PorchSpace.SetupCard)) {
                    DetailCard {
                        Text("What stays", style = PorchType.ScreenTitle)
                        Spacer(Modifier.height(PorchSpace.CardLineGap))
                        Text(
                            "Favourites, hidden groups, moved channels and where you stopped in films and shows all stay. Porch fetches the channels and the guide again when you save.",
                            style = PorchType.Body,
                            color = PorchColors.TextSecondary,
                        )
                        Spacer(Modifier.height(PorchSpace.CardLineGap))
                        Text(
                            if (kind == "m3u") "A new playlist address can give channels new stream addresses. A favourite on a channel like that is lost."
                            else "This is for fixing a typo or a new password. To change to another provider, add it as a new source.",
                            style = PorchType.Body,
                            color = PorchColors.TextSecondary,
                        )
                    }
                }
                return@Row
            }
            Column(Modifier.width(PorchSpace.SetupCard)) {
                DetailCard {
                    Text("Use your phone", style = PorchType.ScreenTitle)
                    Spacer(Modifier.height(PorchSpace.CardLineGap))
                    val url = address
                    Text(
                        "Typing long addresses on a remote is slow. On a phone on the same Wi-Fi, open:",
                        style = PorchType.Body,
                        color = PorchColors.TextSecondary,
                    )
                    Spacer(Modifier.height(PorchSpace.CardLineGap * 2))
                    // One pill for both states, so focus stays on it when the page starts instead of falling out of the panel.
                    // Open look: a plain Pill has the same fill as the card behind it.
                    Pill(if (phoneOn) "Stop phone setup" else "Start phone setup", open = true) {
                        phoneOn = !phoneOn
                        if (!phoneOn) {
                            address = null
                            phoneError = null
                        }
                    }
                    if (phoneOn) Spacer(Modifier.height(PorchSpace.CardLineGap * 2))
                    if (!phoneOn) {
                        // The pill above is all there is until the page starts.
                    } else if (url == null) {
                        Text(phoneError ?: "Starting…", style = PorchType.Body, color = PorchColors.TextSecondary)
                    } else {
                        Text(url.removePrefix("http://").trimEnd('/'), style = PorchType.ScreenTitle)
                        Text(url.trimEnd('/'), style = PorchType.Meta)
                        Spacer(Modifier.height(PorchSpace.CardLineGap * 2))
                        Text(
                            "Fill in the form there and press Send. It appears here, ready to save.",
                            style = PorchType.Body,
                            color = PorchColors.TextSecondary,
                        )
                        phoneError?.let {
                            Spacer(Modifier.height(PorchSpace.CardLineGap))
                            Text(it, style = PorchType.Body)
                        }
                    }
                }
                Spacer(Modifier.height(PorchSpace.CardLineGap))
                if (phoneOn) Text("The phone page has a new address each time. Only use it on a network you trust.", style = PorchType.Hint)
            }
        }
    }
}
