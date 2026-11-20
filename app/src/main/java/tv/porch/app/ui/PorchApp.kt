package tv.porch.app.ui

import android.os.SystemClock
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import tv.porch.app.AppGraph
import tv.porch.app.data.SourceEntity
import tv.porch.app.domain.BackTwice
import tv.porch.app.domain.Channel
import tv.porch.app.domain.GroupChoice
import tv.porch.app.domain.Reminder
import tv.porch.app.domain.ReminderWatch
import tv.porch.app.domain.Reminders
import tv.porch.app.domain.Tabs
import tv.porch.app.domain.SimpleMode
import tv.porch.app.domain.StartScreen
import tv.porch.app.ui.components.LocalSimpleMode
import tv.porch.app.ui.simple.LeaveCard
import tv.porch.app.ui.simple.SimpleCodeScreen
import tv.porch.app.ui.simple.SimpleHome
import tv.porch.app.ui.simple.WelcomeScreen
import tv.porch.app.domain.shouldShowHelp
import tv.porch.app.player.CatchupRequest
import tv.porch.app.parse.XtreamEpisode
import tv.porch.app.player.EpisodeRequest
import tv.porch.app.player.FilmRequest
import tv.porch.app.ui.series.EpisodePlayer
import tv.porch.app.ui.series.SeriesScreen
import tv.porch.app.ui.sport.SportScreen
import tv.porch.app.ui.movies.FilmPlayer
import tv.porch.app.ui.movies.MoviesScreen
import tv.porch.app.player.PlaybackRequest
import tv.porch.app.player.TuneRequest
import tv.porch.app.ui.channels.ChannelsScreen
import tv.porch.app.ui.components.LocalQuickMenu
import tv.porch.app.ui.components.LocalReturnTile
import tv.porch.app.ui.components.QuickMenuHost
import tv.porch.app.ui.components.ReminderCard
import tv.porch.app.domain.ReturnTile
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import kotlinx.coroutines.delay
import tv.porch.app.ui.components.QuickMenuState
import tv.porch.app.ui.guide.GuideScreen
import tv.porch.app.ui.help.HelpScreen
import tv.porch.app.ui.help.PickFavouritesScreen
import tv.porch.app.ui.home.HomeScreen
import tv.porch.app.domain.NowChip
import tv.porch.app.ui.loop.LoopScreen
import tv.porch.app.ui.now.NowScreen
import tv.porch.app.ui.player.PlayerScreen
import tv.porch.app.ui.search.SearchScreen
import tv.porch.app.ui.settings.BackupGuidesScreen
import tv.porch.app.ui.settings.FollowingScreen
import tv.porch.app.ui.settings.PosterRatingsScreen
import tv.porch.app.ui.settings.SettingsScreen
import tv.porch.app.ui.setup.SetupScreen
import tv.porch.app.ui.theme.PorchColors
import tv.porch.app.ui.theme.PorchTheme
import tv.porch.app.ui.theme.LinenPalette
import tv.porch.app.ui.theme.DarkPalette

/** The top-bar label a tab route answers to, for simple mode's check. */
private fun tabLabel(route: Route): String = when (route) {
    Route.Guide -> "Full guide"
    is Route.Now -> "What's on now"
    Route.Loop -> "24/7"
    Route.Movies -> "Movies"
    Route.Series -> "Series"
    Route.Sport -> "Sport"
    Route.Search -> "Search"
    Route.Settings -> "Settings"
    Route.Home -> "Home"
    // Not a tab: simple mode never lets it through showTab.
    else -> route.toString()
}

private sealed interface Route {
    data class Setup(val phone: Boolean = false, val editId: Long? = null) : Route
    data object Home : Route
    /** [chip] is the chip Home's "See all" asked for; null keeps the viewer's last choice. */
    data class Now(val chip: NowChip? = null) : Route
    data object Loop : Route
    data object Movies : Route
    data object Series : Route
    data object Sport : Route
    data object Search : Route
    data object Guide : Route
    data object Settings : Route
    data object Channels : Route
    data object BackupGuides : Route
    data object Following : Route
    data object PosterRatings : Route
    data object Help : Route
    data object PickFavourites : Route
    data class Player(val token: Long) : Route
    data class Film(val token: Long) : Route
    data class Episode(val token: Long) : Route
    /** Simple mode's welcome card. */
    data object Welcome : Route
    /** Simple mode's code pad: choosing a code to turn it on, or typing it to turn it off. */
    data class Code(val turningOn: Boolean) : Route
}

@Composable
fun PorchApp(graph: AppGraph, openChannel: Long? = null, onOpenHandled: () -> Unit = {}) {
    // A launcher card is why Porch was opened: read live, so the cold-start resume below can see it.
    val cardChannel by rememberUpdatedState(openChannel)
    val sources by produceState<List<SourceEntity>?>(null, graph) {
        graph.repository.observeSources().collect { value = it }
    }
    var stack by remember { mutableStateOf<List<Route>>(emptyList()) }
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(sources) {
        val list = sources ?: return@LaunchedEffect
        if (!ready) {
            // A cold start with something played before opens straight on it; Back goes to Home.
            // A card from the launcher wins: the last channel must not start first and then be swapped.
            // Simple mode does the same, but a welcome card not yet dismissed comes first.
            val simpleOn = graph.simpleMode.on.value
            var resume = if (list.isEmpty() || cardChannel != null) null else graph.repository.resumePoint()
            if (simpleOn && resume != null) {
                // Up and Down must stay on the favourites list, in the order the simple Home shows it, not the last channel's playlist group.
                val lastId = resume.first[resume.second]
                val listing = graph.folds.listing.first()
                val chosen = graph.repository.observeChosenGroups().first()
                val mine = GroupChoice.filter(listing.channels, GroupChoice.live(chosen, listing.channels))
                resume = SimpleMode.coldStart(mine, listing.families, lastId)
            }
            stack = when (StartScreen.pick(list.isNotEmpty(), resume != null, cardChannel != null, simpleOn, graph.simpleMode.welcomePending())) {
                StartScreen.Start.Setup -> listOf(Route.Setup())
                StartScreen.Start.Home -> listOf(Route.Home)
                StartScreen.Start.Welcome -> listOf(Route.Home, Route.Welcome)
                StartScreen.Start.LastChannel -> listOf(Route.Home, Route.Player(graph.playback.put(PlaybackRequest(resume!!.first, resume.second, null))))
            }
            ready = true
        } else if (list.isEmpty() && stack.lastOrNull() !is Route.Setup) {
            graph.homeSnapshot.delete()
            stack = listOf(Route.Setup())
        }
    }

    // One-shot: wait for the first channels, then for bare Home, show the guide once, and stop watching.
    LaunchedEffect(ready) {
        // Simple mode has its own welcome card, and the tour's screens are out of its reach.
        if (!ready || graph.help.seen() || graph.simpleMode.on.value) return@LaunchedEffect
        val count = graph.repository.observeChannels().first { it.isNotEmpty() }.size
        snapshotFlow { stack }.first { it == listOf<Route>(Route.Home) }
        if (shouldShowHelp(graph.help.seen(), count)) {
            graph.help.markSeen()
            // Only a new install gets here. Got it on the guide lands on the picker.
            stack = stack + Route.PickFavourites + Route.Help
        }
    }

    if (!ready) {
        Box(Modifier.fillMaxSize().background(PorchColors.Bg), contentAlignment = Alignment.Center) {
            Text("Porch", color = PorchColors.Text)
        }
        return
    }

    BackHandler(enabled = stack.size > 1) {
        // The remote's Back leaves the player here when its overlay is closed; simple mode lands on the favourites list.
        stack = SimpleMode.back(stack, Route.Home, graph.simpleMode.on.value) { it is Route.Player }
    }

    // Back on Home asks twice: the first press warns for two seconds, a second one in that time leaves.
    // In simple mode it asks "Leave Porch?" instead.
    val exitWarning = remember { mutableStateOf(false) }
    val leaveAsk = remember { mutableStateOf(false) }
    val backTwice = remember { BackTwice() }
    val onHome = stack.size == 1 && stack.last() is Route.Home
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val exitCallback = remember {
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (SimpleMode.homeBack(graph.simpleMode.on.value) == SimpleMode.HomeBack.AskLeave) {
                    leaveAsk.value = true
                } else if (backTwice.press(SystemClock.uptimeMillis())) {
                    // Hand this press back to the system, which leaves the app as it always did.
                    isEnabled = false
                    dispatcher?.onBackPressed()
                    isEnabled = true
                } else {
                    exitWarning.value = true
                }
            }
        }
    }
    SideEffect { exitCallback.isEnabled = onHome }
    DisposableEffect(dispatcher) {
        dispatcher?.addCallback(exitCallback)
        onDispose { exitCallback.remove() }
    }
    LaunchedEffect(exitWarning.value) {
        if (exitWarning.value) {
            delay(2_000)
            exitWarning.value = false
        }
    }
    LaunchedEffect(onHome) {
        if (!onHome) {
            exitWarning.value = false
            leaveAsk.value = false
        }
    }

    /** Leaves the app the way the system's own Back does. */
    fun leaveApp() {
        exitCallback.isEnabled = false
        dispatcher?.onBackPressed()
        exitCallback.isEnabled = true
    }

    // Each screen keeps its scroll while it waits in the stack, and forgets it once it leaves.
    val saved = rememberSaveableStateHolder()
    var known by remember { mutableStateOf(emptyList<Route>()) }
    LaunchedEffect(stack) {
        known.filter { it !in stack }.forEach { saved.removeState(it.toString()) }
        known = stack
    }
    val returnTile = remember { ReturnTile() }
    val playing = stack.last() is Route.Player || stack.last() is Route.Film || stack.last() is Route.Episode
    LaunchedEffect(playing) {
        if (!playing) {
            delay(1_000)
            returnTile.landed()
        }
    }

    fun push(route: Route) {
        stack = stack + route
    }

    /** A top-bar tab swaps the screen: Back from any tab goes Home, Back from Home leaves the app. */
    fun showTab(route: Route) {
        // Simple mode opens only Home and the guide, whichever screen asks.
        if (!SimpleMode.allows(tabLabel(route), graph.simpleMode.on.value)) return
        stack = Tabs.stackFor(Route.Home, route)
    }

    fun home() {
        stack = listOf(Route.Home)
    }

    fun openTab(tab: String) {
        when (tab) {
            "Home" -> home()
            "Full guide" -> showTab(Route.Guide)
            "What's on now" -> showTab(Route.Now())
            "24/7" -> showTab(Route.Loop)
            "Movies" -> showTab(Route.Movies)
            "Series" -> showTab(Route.Series)
            "Sport" -> showTab(Route.Sport)
            "Search" -> showTab(Route.Search)
            "Settings" -> showTab(Route.Settings)
        }
    }

    fun playFilm(film: tv.porch.app.data.Film, startAtMs: Long) {
        push(Route.Film(graph.filmPlayback.put(FilmRequest(film, startAtMs))))
    }

    fun playEpisode(show: tv.porch.app.data.ShowEntity, episodes: List<XtreamEpisode>, episode: XtreamEpisode, startAtMs: Long) {
        push(Route.Episode(graph.episodePlayback.put(EpisodeRequest(show, episodes, episode, startAtMs))))
    }

    fun play(channels: List<Channel>, channel: Channel, catchup: CatchupRequest? = null, copy: Channel? = null) {
        if (channels.isEmpty()) return
        val ids = channels.map { it.id }
        val index = ids.indexOf(channel.id).coerceAtLeast(0)
        val token = graph.playback.put(PlaybackRequest(ids, index, catchup, copy?.id))
        returnTile.leaving()
        push(Route.Player(token))
    }

    val quickMenu = remember { QuickMenuState() }
    val scope = rememberCoroutineScope()

    /** Plays [channel] with its neighbours in the shared list, so channel up and down work. A player already on top tunes in place. */
    suspend fun watch(channel: Channel) {
        // Not the whole list: the player loads every id it's given, and 40,000 kept the picture waiting over 10 seconds.
        // The list on hand is used at once; the wait is only for a first list that hasn't arrived yet.
        val listed = (graph.folds.now() ?: withTimeoutOrNull(1_500L) { graph.folds.listing.first() })?.channels.orEmpty()
        val at = listed.indexOfFirst { it.id == channel.id }
        val list = if (at < 0) listOf(channel) else listed.subList((at - 100).coerceAtLeast(0), (at + 101).coerceAtMost(listed.size))
        when (ReminderWatch.action(playerOnTop = stack.lastOrNull() is Route.Player)) {
            // The open player changes channel in place, as a zap does: no new player to build, and Back stays where it was.
            ReminderWatch.Action.Tune -> graph.playback.tune(TuneRequest(list.map { it.id }, list.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)))
            ReminderWatch.Action.Open -> play(list, channel)
        }
    }

    // A card on the launcher's Porch row. A channel that has gone (a refresh renumbers them) leaves the viewer on Home.
    LaunchedEffect(ready, openChannel) {
        val id = openChannel ?: return@LaunchedEffect
        if (!ready) return@LaunchedEffect
        // With no channels yet, Setup stays where it is.
        if (stack.lastOrNull() !is Route.Setup) {
            val channel = graph.repository.channel(id)
            if (channel == null) {
                home()
            } else {
                quickMenu.closeAbout()
                quickMenu.close()
                watch(channel)
            }
        }
        onOpenHandled()
    }

    // Reminders fire only while Porch is on screen: a check now, on each return to the app, and every 20 seconds.
    var reminderCard by remember { mutableStateOf<Pair<Reminder, Channel>?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Each time Porch comes on screen, or the player closes, an old guide updates in the background. Never while watching.
    LaunchedEffect(ready, playing, lifecycle) {
        lifecycle.currentStateFlow.map { it.isAtLeast(Lifecycle.State.STARTED) }.distinctUntilChanged().collect { onScreen ->
            graph.playback.watching = playing && onScreen
            if (ready && onScreen && !playing) graph.sync.refreshStale()
        }
    }
    val stackNow by rememberUpdatedState(stack)
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val store = graph.reminders
                store.prune(System.currentTimeMillis())
                if (reminderCard == null) {
                    for (due in Reminders.due(store.reminders.value, System.currentTimeMillis())) {
                        // A refresh gives channels new ids, so the reminder finds its channel by source and identity.
                        val channel = graph.repository.channelByIdentity(due.sourceId, due.identity)
                        // Another copy of the same channel counts as watching it, and so does watching a recording of it.
                        val families = withTimeoutOrNull(1_500L) { graph.folds.listing.first().families }
                        val playingId = graph.lastChannel.current()?.id
                        val watchingIt = channel != null && stackNow.lastOrNull() is Route.Player && playingId != null &&
                            (playingId == channel.id || families?.sameFamily(playingId, channel.id) == true)
                        if (channel == null || watchingIt) {
                            // Gone, or already on it: nothing to offer, and nothing left to remind.
                            store.remove(due)
                            continue
                        }
                        reminderCard = due to channel
                        break
                    }
                }
                delay(20_000)
            }
        }
    }
    val simple by graph.simpleMode.on.collectAsStateWithLifecycle()
    // The player stays dark over video in either look. Only the top route is ever on screen, so one field is enough.
    val linen by graph.guideFlags.linenLook.collectAsStateWithLifecycle()
    val lookLinen = linen && stack.last() !is Route.Player && stack.last() !is Route.Film && stack.last() !is Route.Episode
    PorchColors.palette = if (lookLinen) LinenPalette else DarkPalette
    key(lookLinen) { PorchTheme {
    CompositionLocalProvider(LocalQuickMenu provides quickMenu, LocalReturnTile provides returnTile, LocalSimpleMode provides simple) {
        Box(Modifier.fillMaxSize().background(PorchColors.Bg)) {
            val current = stack.last()
            saved.SaveableStateProvider(current.toString()) { when (val route = current) {
                is Route.Setup -> SetupScreen(
                    graph = graph,
                    startPhone = route.phone,
                    onTab = { tab -> openTab(tab) },
                    // An edit goes back to Settings, where the source row shows the refresh running.
                    onDone = { if (route.editId != null) stack = Tabs.back(stack, Route.Home) else home() },
                    onCancel = if (stack.size > 1) ({ stack = Tabs.back(stack, Route.Home) }) else null,
                    editId = route.editId,
                )
                Route.Home -> if (simple) SimpleHome(
                    graph = graph,
                    onTab = { tab -> openTab(tab) },
                    onGuide = { showTab(Route.Guide) },
                    onPlay = { channels, channel -> play(channels, channel) },
                    onWatch = { channels, channel, catchup, copy -> play(channels, channel, catchup, copy) },
                ) else HomeScreen(
                    graph = graph,
                    onWhatsOn = { chip -> showTab(Route.Now(chip)) },
                    onGuide = { showTab(Route.Guide) },
                    onLoop = { showTab(Route.Loop) },
                    onMovies = { showTab(Route.Movies) },
                    onSeries = { showTab(Route.Series) },
                    onSport = { showTab(Route.Sport) },
                    onSearch = { showTab(Route.Search) },
                    onSettings = { showTab(Route.Settings) },
                    onPlay = { channels, channel -> play(channels, channel) },
                    onWatch = { channels, channel, catchup, copy -> play(channels, channel, catchup, copy) },
                    exitWarning = exitWarning,
                )
                is Route.Now -> NowScreen(
                    graph = graph,
                    startChip = route.chip,
                    onTab = { tab ->
                        when (tab) {
                            "Home" -> home()
                            "Full guide" -> showTab(Route.Guide)
                            "24/7" -> showTab(Route.Loop)
                            "Movies" -> showTab(Route.Movies)
                            "Series" -> showTab(Route.Series)
                            "Sport" -> showTab(Route.Sport)
                            "Search" -> showTab(Route.Search)
                            "Settings" -> showTab(Route.Settings)
                        }
                    },
                    onPlay = { channels, channel -> play(channels, channel) },
                    onWatch = { channels, channel, catchup, copy -> play(channels, channel, catchup, copy) },
                )
                Route.Loop -> LoopScreen(
                    graph = graph,
                    onTab = { tab -> openTab(tab) },
                    onPlay = { channels, channel -> play(channels, channel) },
                )
                Route.Movies -> MoviesScreen(
                    graph = graph,
                    onTab = { tab -> openTab(tab) },
                    onPlay = { film, startAtMs -> playFilm(film, startAtMs) },
                )
                Route.Sport -> SportScreen(graph = graph, onTab = { tab -> openTab(tab) }, onPlay = { channels, channel -> play(channels, channel) })
                Route.Series -> SeriesScreen(
                    graph = graph,
                    onTab = { tab -> openTab(tab) },
                    onPlay = { show, episodes, episode, startAtMs -> playEpisode(show, episodes, episode, startAtMs) },
                )
                Route.Search -> SearchScreen(
                    graph = graph,
                    onFilm = { film, startAtMs -> playFilm(film, startAtMs) },
                    onEpisode = { show, episodes, episode, startAtMs -> playEpisode(show, episodes, episode, startAtMs) },
                    onTab = { tab ->
                        when (tab) {
                            "Home" -> home()
                            "Full guide" -> showTab(Route.Guide)
                            "What's on now" -> showTab(Route.Now())
                            "24/7" -> showTab(Route.Loop)
                            "Movies" -> showTab(Route.Movies)
                            "Series" -> showTab(Route.Series)
                            "Sport" -> showTab(Route.Sport)
                            "Settings" -> showTab(Route.Settings)
                        }
                    },
                    onPlay = { channels, channel -> play(channels, channel) },
                )
                Route.Guide -> GuideScreen(
                    graph = graph,
                    onTab = { tab ->
                        when (tab) {
                            "Home" -> home()
                            "What's on now" -> showTab(Route.Now())
                            "24/7" -> showTab(Route.Loop)
                            "Movies" -> showTab(Route.Movies)
                            "Series" -> showTab(Route.Series)
                            "Sport" -> showTab(Route.Sport)
                            "Search" -> showTab(Route.Search)
                            "Settings" -> showTab(Route.Settings)
                        }
                    },
                    onPlay = { channels, channel, catchup, copy -> play(channels, channel, catchup, copy) },
                )
                Route.Settings -> SettingsScreen(
                    graph = graph,
                    onTab = { tab -> openTab(tab) },
                    onAdd = { push(Route.Setup()) },
                    onEdit = { id -> push(Route.Setup(editId = id)) },
                    onPhone = { push(Route.Setup(phone = true)) },
                    onChannels = { push(Route.Channels) },
                    onBackupGuides = { push(Route.BackupGuides) },
                    onFollowing = { push(Route.Following) },
                    onPickFavourites = { push(Route.PickFavourites) },
                    onPosterRatings = { push(Route.PosterRatings) },
                    onHelp = { push(Route.Help) },
                    onSimpleMode = { push(Route.Code(turningOn = true)) },
                )
                Route.Welcome -> WelcomeScreen(onDone = {
                    graph.simpleMode.welcomeSeen()
                    home()
                })
                is Route.Code -> SimpleCodeScreen(
                    turningOn = route.turningOn,
                    saved = graph.simpleMode.code(),
                    // Ruben hands the remote over on the welcome card.
                    onTurnOn = { code ->
                        graph.simpleMode.turnOn(code)
                        stack = listOf(Route.Home, Route.Welcome)
                    },
                    // Back where simple mode is turned on, so it is plain that it is off.
                    onTurnOff = {
                        graph.simpleMode.turnOff()
                        stack = listOf(Route.Home, Route.Settings)
                    },
                )
                Route.Help -> HelpScreen(onTab = { tab -> openTab(tab) }, onDone = { stack = Tabs.back(stack, Route.Home) })
                Route.PickFavourites -> PickFavouritesScreen(graph, onTab = { tab -> openTab(tab) }, onDone = { stack = Tabs.back(stack, Route.Home) })
                Route.Channels -> ChannelsScreen(graph)
                Route.BackupGuides -> BackupGuidesScreen(graph, onTab = { tab -> openTab(tab) })
                Route.Following -> FollowingScreen(graph, onTab = { tab -> openTab(tab) })
                Route.PosterRatings -> PosterRatingsScreen(graph, onTab = { tab -> openTab(tab) })
                is Route.Film -> FilmPlayer(graph, route.token) {
                    stack = Tabs.back(stack, Route.Home)
                }
                is Route.Episode -> EpisodePlayer(graph, route.token) {
                    stack = Tabs.back(stack, Route.Home)
                }
                is Route.Player -> PlayerScreen(graph, route.token) {
                    // Simple mode always lands on the favourites list, even when the guide opened the channel.
                    stack = SimpleMode.afterPlayer(stack, Route.Home, graph.simpleMode.on.value)
                }
            } }
            QuickMenuHost(graph, quickMenu, Modifier.fillMaxSize())
            if (leaveAsk.value && onHome) {
                LeaveCard(
                    onStay = { leaveAsk.value = false },
                    onLeave = {
                        leaveAsk.value = false
                        leaveApp()
                    },
                    onSimpleOff = {
                        leaveAsk.value = false
                        push(Route.Code(turningOn = false))
                    },
                )
            }
            reminderCard?.let { (reminder, channel) ->
                ReminderCard(
                    channel = channel,
                    reminder = reminder,
                    onWatch = {
                        reminderCard = null
                        graph.reminders.remove(reminder)
                        // The sheet and menu belong to the screen being left.
                        quickMenu.closeAbout()
                        quickMenu.close()
                        scope.launch { watch(channel) }
                    },
                    onDismiss = {
                        reminderCard = null
                        graph.reminders.remove(reminder)
                    },
                )
            }
        }
    }
    } }
}
