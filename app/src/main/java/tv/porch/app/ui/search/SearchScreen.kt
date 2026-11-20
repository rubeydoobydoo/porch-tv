package tv.porch.app.ui.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import tv.porch.app.AppGraph
import tv.porch.app.data.Film
import tv.porch.app.data.ShowEntity
import tv.porch.app.parse.XtreamEpisode
import tv.porch.app.ui.series.ShowPage
import tv.porch.app.data.SearchHits
import tv.porch.app.ui.movies.FilmSheet
import kotlinx.coroutines.flow.first
import tv.porch.app.data.pacedWhile
import tv.porch.app.domain.Channel
import tv.porch.app.domain.ChannelBadge
import tv.porch.app.domain.GroupChoice
import tv.porch.app.domain.GuideClock
import tv.porch.app.domain.HomeLists
import tv.porch.app.domain.SearchKeys
import tv.porch.app.domain.Tabs
import tv.porch.app.domain.formatHeaderClockLong
import tv.porch.app.domain.formatLaterStart
import tv.porch.app.domain.formatLeft
import tv.porch.app.domain.progressOf
import tv.porch.app.ui.VmFactory
import tv.porch.app.ui.components.FavouriteStar
import tv.porch.app.ui.components.LocalQuickMenu
import tv.porch.app.ui.components.ChannelLogo
import tv.porch.app.ui.components.LiveRowItem
import tv.porch.app.ui.components.Pill
import tv.porch.app.ui.components.ProgressTrack
import tv.porch.app.ui.components.SectionHeader
import tv.porch.app.ui.components.TopBar
import tv.porch.app.ui.components.clickScale
import tv.porch.app.ui.components.rememberNow
import tv.porch.app.ui.home.endsText
import tv.porch.app.ui.theme.PorchColors
import tv.porch.app.ui.theme.PorchFocus
import tv.porch.app.ui.theme.PorchRadius
import tv.porch.app.ui.theme.PorchSpace
import tv.porch.app.ui.theme.PorchType

/** How many of Home's "Your channels" Search shows before anything is typed. */
private const val YOURS_ROWS = 7

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(private val graph: AppGraph) : ViewModel() {
    private val queries = MutableStateFlow("")
    var query by mutableStateOf("")
        private set
    var note by mutableStateOf<String?>(null)
        private set

    /** Each result carries the query it answers, so the screen can tell "still searching" from "nothing matches". */
    val hits = queries
        .debounce(250)
        .flatMapLatest { text ->
            flow {
                emit(
                    text to if (text.isBlank()) SearchHits.Empty
                    else graph.repository.search(text, System.currentTimeMillis(), graph.folds.listing.first().families),
                )
            }
        }
        .conflate()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "" to SearchHits.Empty)

    /** Films matching the typed text, when an Xtream source exists. Its own flow, so a slow film search never holds up the channel results. */
    val films = queries
        .debounce(250)
        .flatMapLatest { text ->
            flow {
                emit(
                    if (text.isBlank()) emptyList<Film>()
                    else runCatching {
                        val source = graph.repository.sources().firstOrNull { it.kind == "xtream" }
                        if (source == null) emptyList() else graph.movies.search(source.id, text).take(FILM_CAP)
                    }.getOrDefault(emptyList()),
                )
            }
        }
        .conflate()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Shows matching the typed text, its own flow like [films]. */
    val shows = queries
        .debounce(250)
        .flatMapLatest { text ->
            flow {
                emit(
                    if (text.isBlank()) emptyList<ShowEntity>()
                    else runCatching {
                        val source = graph.repository.sources().firstOrNull { it.kind == "xtream" }
                        if (source == null) emptyList() else graph.shows.search(source.id, text).take(FILM_CAP)
                    }.getOrDefault(emptyList()),
                )
            }
        }
        .conflate()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Home's "Your channels", live, for the right-hand side while nothing is typed. Built from the same lists as Home. */
    val yours: StateFlow<HomeLists.Section?> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }.flatMapLatest { now ->
        combine(
            graph.folds.listing,
            graph.repository.observeChosenGroups(),
            graph.repository.observeOnNow(now).pacedWhile(graph.sync::importing),
            graph.repository.observeSources(),
        ) { listing, chosen, onNow, sources ->
            val mine = GroupChoice.filter(listing.channels, GroupChoice.live(chosen, listing.channels))
            HomeLists.yoursOnNow(mine, onNow, sources.associate { it.id to it.guideOffsetMinutes }, listing.families)
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun replace(value: String) {
        query = value.take(SearchKeys.MAX_LENGTH)
        queries.value = query
        note = null
    }

    fun press(key: String) = replace(SearchKeys.apply(query, key))

    fun voiceNote(text: String) {
        note = text
    }
}

@Composable
fun SearchScreen(
    graph: AppGraph,
    onTab: (String) -> Unit,
    onPlay: (List<Channel>, Channel) -> Unit,
    onFilm: (Film, Long) -> Unit = { _, _ -> },
    onEpisode: (ShowEntity, List<XtreamEpisode>, XtreamEpisode, Long) -> Unit = { _, _, _, _ -> },
) {
    val model: SearchViewModel = viewModel(factory = VmFactory { SearchViewModel(graph) })
    val quick = LocalQuickMenu.current
    val answer by model.hits.collectAsStateWithLifecycle()
    // Keep the shared channel list live while Search is open, so a query never reads a list a refresh has replaced.
    val keepChannels by remember { graph.folds.listing }.collectAsState(null)
    val hits = answer.second
    val films by model.films.collectAsStateWithLifecycle()
    val shows by model.shows.collectAsStateWithLifecycle()
    var openFilm by remember { mutableStateOf<Film?>(null) }
    var openShow by remember { mutableStateOf<ShowEntity?>(null) }
    val context = LocalContext.current
    val now = rememberNow()
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) model.replace(spoken)
        }
    }
    fun speak() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a show or a channel")
        }
        if (intent.resolveActivity(context.packageManager) == null) {
            model.voiceNote("Voice search isn't available on this device.")
        } else {
            voice.launch(intent)
        }
    }

    // Focus: A to start, Right from the grid's edge into the first result, Left back to the last key used.
    val keyFocus = remember { mutableMapOf<String, FocusRequester>() }
    fun keyRequester(label: String) = keyFocus.getOrPut(label) { FocusRequester() }
    val lastKey = remember { arrayOf("A") }
    val firstResult = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { keyRequester("A").requestFocus() }
    }
    val hasResults = hits.onNow.isNotEmpty() || hits.later.isNotEmpty() || hits.channels.isNotEmpty() || films.isNotEmpty() || shows.isNotEmpty()
    val searching = SearchKeys.isSearching(model.query, answer.first)
    val yours by model.yours.collectAsStateWithLifecycle()
    val yourRows = yours?.all?.take(YOURS_ROWS).orEmpty()
    val showYours = model.query.isBlank() && yourRows.isNotEmpty()
    val toResults = if ((model.query.isNotBlank() && hasResults && !searching) || showYours) Modifier.focusProperties { right = firstResult } else Modifier
    val toGrid = Modifier.focusProperties { left = keyRequester(lastKey[0]) }

    Column(
        Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            val mic = event.key.nativeKeyCode == KeyEvent.KEYCODE_SEARCH || event.key.nativeKeyCode == KeyEvent.KEYCODE_VOICE_ASSIST
            if (mic && event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                speak()
                true
            } else {
                mic
            }
        },
    ) {
        TopBar(
            title = "Search",
            clock = formatHeaderClockLong(now),
            tabs = Tabs.labels,
            current = "Search",
            onTab = onTab,
        )
        Row(Modifier.fillMaxSize().padding(start = PorchSpace.Margin, end = PorchSpace.Margin - PorchSpace.FocusRoom, top = PorchSpace.SearchTop)) {
            Column(Modifier.width(PorchSpace.SearchLeft)) {
                QueryField(model.query)
                Spacer(Modifier.height(PorchSpace.SearchKeyGap * 2))
                SearchKeys.GRID.chunked(7).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(PorchSpace.SearchKeyGap), modifier = Modifier.padding(bottom = PorchSpace.SearchKeyGap)) {
                        row.forEachIndexed { column, label ->
                            KeyCap(
                                label,
                                PorchSpace.SearchKey,
                                Modifier.focusRequester(keyRequester(label)).then(if (column == 6) toResults else Modifier),
                                onFocus = { lastKey[0] = label },
                            ) { model.press(label) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(PorchSpace.SearchKeyGap)) {
                    KeyCap("0", PorchSpace.SearchKey, Modifier.focusRequester(keyRequester("0")), onFocus = { lastKey[0] = "0" }) { model.press("0") }
                    for (label in listOf(SearchKeys.SPACE, SearchKeys.DELETE, SearchKeys.CLEAR)) {
                        KeyCap(
                            label,
                            PorchSpace.SearchWideKey,
                            Modifier.focusRequester(keyRequester(label)).then(if (label == SearchKeys.CLEAR) toResults else Modifier),
                            onFocus = { lastKey[0] = label },
                        ) { model.press(label) }
                    }
                }
                Spacer(Modifier.height(PorchSpace.SearchGridGap))
                // Only a voice problem is worth a line here; the remote rule is D-pad, OK and Back.
                model.note?.let { note ->
                    Text(note, style = PorchType.Hint)
                    Spacer(Modifier.height(PorchSpace.SearchHintGap))
                }
                Spacer(Modifier.height(PorchSpace.SearchSpeakGap))
                Pill("Speak", textStyle = PorchType.Meta.copy(color = Color.Unspecified)) { speak() }
            }
            Spacer(Modifier.width(PorchSpace.SearchGap - PorchSpace.FocusRoom))
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(PorchSpace.FocusRoom),
            ) {
                when {
                    showYours -> {
                        val list = remember(yours) { yours?.all.orEmpty().map { it.channels.first() }.distinctBy { it.id } }
                        Text("Your channels", style = PorchType.Section, modifier = Modifier.padding(bottom = PorchSpace.ListGap))
                        Column(verticalArrangement = Arrangement.spacedBy(PorchSpace.HomeRowGap)) {
                            yourRows.forEachIndexed { position, row ->
                                val channel = row.channels.first()
                                LiveRowItem(
                                    row = row,
                                    trailing = endsText(row),
                                    liveLabel = false,
                                    wideSlot = false,
                                    modifier = (if (position == 0) Modifier.focusRequester(firstResult) else Modifier).then(toGrid),
                                    onFocused = {},
                                    onOpenOthers = null,
                                    onPlay = { onPlay(list, channel) },
                                    onLong = { quick.open(channel) },
                                    compact = true,
                                )
                            }
                        }
                    }
                    model.query.isBlank() -> Text("Type a show or channel.", style = PorchType.Body, color = PorchColors.Muted)
                    searching -> Text("Searching…", style = PorchType.Body, color = PorchColors.Muted)
                    !hasResults -> Text("Nothing matches.", style = PorchType.Body, color = PorchColors.Muted)
                    else -> {
                        var first = true
                        fun place(): Modifier {
                            val mine = if (first) Modifier.focusRequester(firstResult) else Modifier
                            first = false
                            return mine
                        }
                        if (hits.onNow.isNotEmpty()) {
                            Group("On now", hits.onNow.size - GROUP_CAP) {
                                hits.onNow.take(GROUP_CAP).forEach { (channel, programme) ->
                                    val offset = hits.offsets[channel.sourceId] ?: 0
                                    val start = GuideClock.shift(programme.startUtc, offset)
                                    val stop = GuideClock.shift(programme.stopUtc, offset)
                                    ResultRow(
                                        channel, programme.title, "${channel.shownTitle}  ·  ${formatLeft(stop - now)}",
                                        place().then(toGrid), progressOf(start, stop, now),
                                        onLong = { quick.open(channel) },
                                    ) { onPlay(hits.onNow.map { it.first }, channel) }
                                }
                            }
                        }
                        if (hits.later.isNotEmpty()) {
                            Group("Later", hits.later.size - GROUP_CAP) {
                                hits.later.take(GROUP_CAP).forEach { (channel, programme) ->
                                    val start = GuideClock.shift(programme.startUtc, hits.offsets[channel.sourceId] ?: 0)
                                    ResultRow(
                                        channel, programme.title, "${channel.shownTitle}  ·  ${formatLaterStart(start, now)}",
                                        place().then(toGrid), null,
                                        onLong = { quick.open(channel) },
                                    ) { onPlay(hits.later.map { it.first }, channel) }
                                }
                            }
                        }
                        if (hits.channels.isNotEmpty()) {
                            Group("Channels", 0) {
                                hits.channels.chunked(2).forEach { pair ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(PorchSpace.ListGap)) {
                                        pair.forEachIndexed { index, channel ->
                                            val where = channel.number.takeIf { it > 0 }?.let { "Ch $it" } ?: channelMeta(channel)
                                            ResultRow(
                                                channel, channel.shownTitle, where,
                                                Modifier.weight(1f).then(place()).then(if (index == 0) toGrid else Modifier), null,
                                                onLong = { quick.open(channel) },
                                            ) { onPlay(hits.channels, channel) }
                                        }
                                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                        if (films.isNotEmpty()) {
                            Group("Films", 0) {
                                films.forEach { film ->
                                    TitleRow(film.name, film.year, place().then(toGrid)) { openFilm = film }
                                }
                            }
                        }
                        if (shows.isNotEmpty()) {
                            Group("Shows", 0) {
                                shows.forEach { show ->
                                    TitleRow(show.name, show.year, place().then(toGrid)) { openShow = show }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    openFilm?.let { film ->
        FilmSheet(
            graph = graph,
            film = film,
            onPlay = { startAtMs ->
                openFilm = null
                onFilm(film, startAtMs)
            },
            onClose = { openFilm = null },
            backWording = "Back closes",
        )
    }
    openShow?.let { show ->
        ShowPage(
            graph = graph,
            show = show,
            onPlay = { episodes, episode, startAtMs ->
                openShow = null
                onEpisode(show, episodes, episode, startAtMs)
            },
            onClose = { openShow = null },
            backWording = "Back returns to Search",
        )
    }
}

/** A film or show result: its title, then its year in Meta. Same row as the other results, without a channel badge. */
@Composable
private fun TitleRow(name: String, year: Int?, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(PorchRadius.Row)
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(PorchSpace.SearchRow).onFocusChanged { focused = it.hasFocus },
        shape = ClickableSurfaceDefaults.shape(shape),
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
        Column(Modifier.fillMaxSize().padding(horizontal = PorchSpace.TileGap), verticalArrangement = Arrangement.Center) {
            Text(name, style = PorchType.TileTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            year?.takeIf { it > 0 }?.let {
                Text(it.toString(), style = PorchType.Meta, color = if (focused) PorchColors.OnFocus else PorchColors.Muted, maxLines = 1)
            }
        }
    }
}

private fun channelMeta(channel: Channel): String = channel.code ?: channel.tag ?: ""

/** The frame shows all three groups at once, so On now and Later show this many rows and say how many are left. */
private const val GROUP_CAP = 4

/** Films in Search: the most a typed word shows. */
private const val FILM_CAP = 8

@Composable
private fun Group(title: String, hidden: Int, content: @Composable () -> Unit) {
    SectionHeader(title)
    Spacer(Modifier.height(PorchSpace.ListGap))
    Column(verticalArrangement = Arrangement.spacedBy(PorchSpace.ListGap)) { content() }
    if (hidden > 0) {
        Text("$hidden more", style = PorchType.Meta, color = PorchColors.Muted, modifier = Modifier.padding(top = PorchSpace.SearchMoreGap))
    }
    Spacer(Modifier.height(PorchSpace.SearchGroupGap))
}

/** The typed text, with a caret. It is not a focus stop: the grid does the typing. */
@Composable
private fun QueryField(query: String) {
    val shape = RoundedCornerShape(PorchRadius.Row)
    Box(
        Modifier
            .fillMaxWidth()
            .height(PorchSpace.SearchKey)
            .background(PorchColors.SurfaceRaised, shape)
            .padding(horizontal = PorchSpace.TileGap),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            "$query|",
            style = PorchType.Key,
            color = PorchColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

/** One key of the on-screen keyboard. The simple-mode code pad uses the same key. */
@Composable
internal fun KeyCap(label: String, width: androidx.compose.ui.unit.Dp, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    val shape = RoundedCornerShape(PorchRadius.Row)
    Surface(
        onClick = onClick,
        modifier = modifier.width(width).height(PorchSpace.SearchKey).onFocusChanged { if (it.hasFocus) onFocus() },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = PorchColors.Surface,
            contentColor = PorchColors.Text,
            focusedContainerColor = PorchColors.Focus,
            focusedContentColor = PorchColors.OnFocus,
            pressedContainerColor = PorchColors.Focus,
            pressedContentColor = PorchColors.OnFocus,
        ),
        border = ClickableSurfaceDefaults.border(),
        scale = clickScale(PorchFocus.KeyScale),
        glow = ClickableSurfaceDefaults.glow(),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = if (width == PorchSpace.SearchKey) PorchType.Key else PorchType.TileTitle,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/** A result: badge, title and meta, with a live progress bar when [track] is set. */
@Composable
private fun ResultRow(channel: Channel, title: String, meta: String, modifier: Modifier, track: Float?, onLong: () -> Unit, onClick: () -> Unit) {
    val shape = RoundedCornerShape(PorchRadius.Row)
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        onLongClick = onLong,
        modifier = modifier.fillMaxWidth().height(PorchSpace.SearchRow).onFocusChanged { focused = it.hasFocus },
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
        Row(
            Modifier.fillMaxSize().padding(horizontal = PorchSpace.TileGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelLogo(
                channel,
                Color(ChannelBadge.color(channel.name, channel.group)),
                size = PorchSpace.SearchBadge,
                fixedWidth = true,
            )
            Spacer(Modifier.width(PorchSpace.SearchBadgeGap))
            Column(Modifier.weight(1f)) {
                Text(title, style = PorchType.TileTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(meta, style = PorchType.Meta, color = if (focused) PorchColors.OnFocus else PorchColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (channel.favourite) {
                Spacer(Modifier.width(PorchSpace.TileGap))
                FavouriteStar()
            }
            if (track != null) {
                Spacer(Modifier.width(PorchSpace.TileGap))
                ProgressTrack(track, Modifier.width(PorchSpace.SearchTrack))
            }
        }
    }
}
