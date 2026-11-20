@file:OptIn(UnstableApi::class)

package tv.porch.app.ui.player

import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import tv.porch.app.domain.OverlayText
import tv.porch.app.domain.TrackNames
import tv.porch.app.ui.components.ChannelLogo
import tv.porch.app.ui.components.LiveChip
import tv.porch.app.ui.components.LocalQuickMenu
import tv.porch.app.ui.components.PlayerHooks
import tv.porch.app.ui.home.channelColor
import tv.porch.app.ui.theme.LocalMotionEnabled
import tv.porch.app.ui.theme.PorchFocus
import tv.porch.app.ui.theme.PorchMotion
import tv.porch.app.ui.theme.PorchRadius
import tv.porch.app.ui.theme.PorchSpace
import tv.porch.app.ui.theme.PorchType
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import androidx.media3.common.ParserException
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel as EventChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import tv.porch.app.AppGraph
import tv.porch.app.data.SourceEntity
import tv.porch.app.domain.Airing
import tv.porch.app.domain.Channel
import tv.porch.app.domain.ChannelFold
import tv.porch.app.domain.GuideClock
import tv.porch.app.domain.Programme
import tv.porch.app.domain.PlayRules
import tv.porch.app.domain.RefreshMatch
import tv.porch.app.domain.RetryPolicy
import tv.porch.app.domain.SimpleMode
import tv.porch.app.domain.StreamHealth
import tv.porch.app.domain.catchupMinutes
import tv.porch.app.domain.catchupStart
import tv.porch.app.domain.formatLeft
import tv.porch.app.domain.formatTime
import tv.porch.app.domain.progressOf
import tv.porch.app.player.CatchupRequest
import tv.porch.app.parse.XtreamUrls
import tv.porch.app.ui.components.Pill
import tv.porch.app.ui.components.ProgressTrack
import tv.porch.app.ui.components.clickBorder
import androidx.compose.ui.focus.onFocusChanged
import tv.porch.app.ui.components.clickScale
import tv.porch.app.ui.components.rememberNow
import tv.porch.app.ui.theme.PorchColors
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface

@Composable
fun PlayerScreen(graph: AppGraph, token: Long, onExit: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var pendingIndex by remember { mutableIntStateOf(0) }
    var index by remember { mutableIntStateOf(0) }
    var primed by remember { mutableStateOf(false) }
    // When the viewer last changed channel (a zap or a reminder's Watch), for the "tune ready" timing line. 0 when none is waiting.
    val tunedAt = remember { longArrayOf(0L) }
    // Counts tunes from a reminder card. The card held focus, so each one puts focus back on this screen.
    var cardTunes by remember { mutableIntStateOf(0) }
    // Another copy playing in place of the listed channel, for now. A zap leaves it behind.
    var copy by remember { mutableStateOf<Channel?>(null) }
    // Copies that have failed since the viewer last chose. It only grows, so moving on always ends.
    val triedCopies = remember { HashSet<Long>() }
    var tryingNote by remember { mutableStateOf<String?>(null) }
    // True from a drop until the stream plays again or stops for good: the overlay then offers Retry in place of Pause.
    var reconnecting by remember { mutableStateOf(false) }
    // The listed channel that a copy is standing in for because it failed. Null when the viewer chose the copy, or none is playing.
    var fellBackFrom by remember { mutableStateOf<Long?>(null) }
    var zapFolded by remember { mutableStateOf(false) }
    var catchup by remember { mutableStateOf<CatchupRequest?>(null) }
    var catchupOn by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf<SourceEntity?>(null) }
    var offsets by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    var loadedUpcoming by remember { mutableStateOf<List<Programme>>(emptyList()) }
    var upcomingFor by remember { mutableStateOf<Long?>(null) }
    var others by remember { mutableStateOf<List<Airing>>(emptyList()) }
    var overlay by remember { mutableStateOf(true) }
    var sideFocus by remember { mutableStateOf(false) }
    var tracksOpen by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf<String?>(null) }
    var stopReason by remember { mutableStateOf<StopReason?>(null) }
    val stopped = stopReason != null
    var retryNonce by remember { mutableIntStateOf(0) }
    var missing by remember { mutableStateOf(false) }
    var trackVersion by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    val okHold = remember { OkHold() }
    val quick = LocalQuickMenu.current
    val rootFocus = remember { FocusRequester() }
    val sideFocusRequester = remember { FocusRequester() }
    val sheetFocus = remember { FocusRequester() }
    val audioFocus = remember { FocusRequester() }
    val messageFocus = remember { FocusRequester() }
    val now = rememberNow()

    DisposableEffect(context) {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Porch/1.0 (Android TV)")
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(2_000, 20_000, 800, 1_500)
            .build()
        // If a hardware decoder fails to start, try the next one down (the software decoder) before giving up.
        val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        val exo = ExoPlayer.Builder(context, renderers)
            .setLoadControl(load)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
        exo.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true,
        )
        exo.playWhenReady = true
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                trackVersion++
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                paused = !playWhenReady
            }
        }
        exo.addListener(listener)
        player = exo
        onDispose {
            exo.removeListener(listener)
            exo.release()
            player = null
        }
    }

    // Leaving the app (Home, another app, the TV sleeping) ends the stream, so it stops making sound and frees the provider's one connection. Compose is paused while away, so this acts on the player directly, and coming back restarts through retryNonce.
    val lifecycleOwner = LocalLifecycleOwner.current
    val away = remember { booleanArrayOf(false) }
    var resumeAtMs by remember { mutableStateOf(0L) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                away[0] = true
                player?.let { exo ->
                    resumeAtMs = PlayRules.resumeAtMs(catchup = catchupOn && catchup != null, positionMs = exo.currentPosition)
                    exo.stop()
                }
            } else if (event == Lifecycle.Event.ON_START && away[0]) {
                away[0] = false
                retryNonce++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(token) {
        val request = graph.playback.get(token)
        if (request == null || request.channelIds.isEmpty()) {
            missing = true
            return@LaunchedEffect
        }
        val loaded = graph.repository.channels(request.channelIds)
        if (loaded.isEmpty()) {
            missing = true
            return@LaunchedEffect
        }
        val startId = request.channelIds.getOrNull(request.index)
        val start = loaded.indexOfFirst { it.id == startId }.let { if (it < 0) 0 else it }
        channels = loaded
        copy = request.copyId?.let { graph.repository.channel(it) }
        catchup = request.catchup
        catchupOn = request.catchup != null
        pendingIndex = start
        index = start
        primed = true
    }

    LaunchedEffect(pendingIndex, primed) {
        if (!primed) return@LaunchedEffect
        if (pendingIndex == index) return@LaunchedEffect
        delay(250)
        index = pendingIndex
        copy = null
        triedCopies.clear()
        tryingNote = null
        fellBackFrom = null
    }

    LaunchedEffect(overlay) {
        if (!overlay) sideFocus = false
    }

    LaunchedEffect(banner) {
        if (banner != null) overlay = true // a drop or a catch-up start is worth a word on screen
    }

    LaunchedEffect(paused) {
        if (paused) overlay = true // a paused picture keeps its overlay so the way back is on screen
    }

    LaunchedEffect(overlay, sideFocus, tracksOpen, banner, index, paused) {
        if (overlay && !sideFocus && !tracksOpen && banner == null && !paused) {
            delay(5_000)
            overlay = false
        }
    }

    val listedChannel = channels.getOrNull(index)
    val channel = copy ?: listedChannel
    // Only what was loaded for the channel on screen: during a tune the old channel's programme must not sit under the new name.
    val upcoming = PlayRules.programmesFor(upcomingFor, channel?.id ?: -1L, loadedUpcoming)
    // Remember where the viewer has been, for "Back to" in the quick menu. A copy is the same channel.
    LaunchedEffect(listedChannel?.id) {
        listedChannel?.let { graph.lastChannel.played(it) }
    }
    // Subscribed for the screen's whole life, so the shared list is kept and "others" never offers a channel a refresh removed.
    val listedNow by remember { graph.folds.listing }.collectAsState(null)
    // Zap across one copy of each channel. Done once, and only when the list arrives, so a long list never delays the picture.
    // The list is a key: a channel added while the fold ran cancels it, and it starts again on the longer list. Done is set only once a fold has landed.
    LaunchedEffect(primed, listedNow, channels) {
        val listing = listedNow ?: return@LaunchedEffect
        if (!primed || zapFolded) return@LaunchedEffect
        val before = channels
        val at = before.getOrNull(pendingIndex)?.id ?: return@LaunchedEffect
        val folded = withContext(Dispatchers.Default) { ChannelFold.fold(before, listing.families, keep = at) }
        // The viewer can tune or zap while that ran. A zap may have landed on a copy that's gone, so it keeps the fold's own place.
        if (channels !== before) return@LaunchedEffect
        zapFolded = true
        if (folded.size == before.size) return@LaunchedEffect
        val wanted = before.getOrNull(pendingIndex)?.id
        val place = folded.indexOfFirst { it.id == wanted }.takeIf { it >= 0 } ?: folded.indexOfFirst { it.id == at }
        if (pendingIndex != index) {
            copy = null
            triedCopies.clear()
            tryingNote = null
            fellBackFrom = null
        }
        channels = folded
        index = place
        pendingIndex = place
    }
    LaunchedEffect(player, channel?.id, retryNonce, primed) {
        val exo = player ?: return@LaunchedEffect
        val current = channel ?: return@LaunchedEffect
        val listed = listedChannel ?: return@LaunchedEffect
        if (!primed) return@LaunchedEffect
        val src = graph.repository.source(current.sourceId)
        source = src
        val useCatchup = catchupOn && catchup != null
        stopReason = null
        reconnecting = false
        banner = PlayRules.startWords(useCatchup, tryingNote)
        var attempt = 0
        var swapped = false
        while (true) {
            val url = playbackUrl(current, src, if (useCatchup) catchup else null, swapped)
            if (url == null) {
                banner = "Catch-up isn't available on this channel."
                stopReason = StopReason.CatchupUnavailable
                tunedAt[0] = 0L
                return@LaunchedEffect
            }
            if (away[0]) { tunedAt[0] = 0L; return@LaunchedEffect }
            exo.stop()
            val startAt = resumeAtMs
            resumeAtMs = 0L
            // The plain call on live: a start position of 0 would land at the back of an HLS window, not on what is on now.
            if (startAt > 0L) exo.setMediaItem(MediaItem.fromUri(url), startAt) else exo.setMediaItem(MediaItem.fromUri(url))
            exo.prepare()
            exo.play()
            var failure = awaitReady(exo)
            if (away[0]) { tunedAt[0] = 0L; return@LaunchedEffect }
            if (failure == null && tunedAt[0] > 0L) {
                // Time only, never the channel or its address: Ruben reads it in logcat to compare builds on the TV.
                Log.i("Porch", "tune ready ${SystemClock.elapsedRealtime() - tunedAt[0]} ms")
                tunedAt[0] = 0L
            }
            if (failure == null) {
                // After a fallback the copy that works is the one to lead next time: marking the dead one would put it on top and make every later tune wait through its retries.
                val markId = if (fellBackFrom == listed.id) current.id else listed.id
                // A child of this effect, so zapping away within the wait cancels it.
                launch {
                    delay(PlayRules.MARK_AFTER_MS)
                    // The listed channel, even when a viewer-chosen copy is playing, so playing a copy doesn't change which copy is chosen next time.
                    graph.repository.markPlayed(markId)
                    // Played stably, so the copies that failed earlier get a fresh chance if this one drops later. A copy that keeps failing never gets here, so the walk stays bounded.
                    triedCopies.clear()
                }
                tryingNote = null
                banner = null
                stopReason = null
                reconnecting = false
                attempt = 0
                failure = awaitDrop(exo, live = !useCatchup)
                if (away[0]) return@LaunchedEffect
            }
            val code = failure?.let { httpCode(it) }
            if (RetryPolicy.isAuthFailure(code)) {
                tryingNote = null
                banner = "Login was refused ($code). Porch will not keep retrying."
                stopReason = StopReason.LoginRefused(code)
                tunedAt[0] = 0L
                return@LaunchedEffect
            }
            if (!swapped && failure != null && isFormatError(failure) && url.substringBefore('?').endsWith(".ts", ignoreCase = true)) {
                swapped = true
                banner = "Trying another stream format…"
                continue
            }
            attempt++
            val copies = listedNow?.families?.copiesOf(listed.id).orEmpty()
            val spare = ChannelFold.nextCopy(copies, triedCopies + current.id, catchup = useCatchup) != null
            val allowed = when {
                triedCopies.isNotEmpty() -> ChannelFold.COPY_ATTEMPTS
                spare -> ChannelFold.ASKED_COPY_ATTEMPTS
                else -> RetryPolicy.MAX_ATTEMPTS
            }
            if (attempt > allowed) {
                // Retries used up: the family's next copy, if it has one. Changing channel restarts this effect on that copy.
                triedCopies += current.id
                val next = ChannelFold.nextCopy(copies, triedCopies, catchup = useCatchup)
                if (next != null) {
                    tryingNote = "Trying another copy of ${listed.shownTitle}"
                    copy = next.takeIf { it.id != listed.id }
                    fellBackFrom = listed.id
                    return@LaunchedEffect
                }
                tryingNote = null
                banner = "The stream dropped. Press Retry to try again."
                stopReason = StopReason.Dropped
                tunedAt[0] = 0L
                return@LaunchedEffect
            }
            banner = RetryPolicy.reconnectingText(attempt, allowed)
            reconnecting = true
            overlay = true
            delay(RetryPolicy.delayMs(attempt))
        }
    }

    // Time the pictures of whatever settles on screen, then let the TV follow. Starts afresh on every channel, so a quick zap past never switches it.
    val activity = LocalActivity.current
    val view = LocalView.current
    val matchRefresh by graph.guideFlags.matchRefresh.collectAsStateWithLifecycle()
    DisposableEffect(player, channel?.id, retryNonce, matchRefresh) {
        val exo = player
        val window = activity?.window
        if (!matchRefresh) {
            window?.let { it.attributes = it.attributes.apply { preferredDisplayModeId = 0 } }
        }
        val listener = if (exo == null || window == null || !matchRefresh) {
            null
        } else {
            var last = -1L
            val gaps = ArrayList<Long>(RefreshMatch.GAPS_TO_MEASURE)
            lateinit var timing: VideoFrameMetadataListener
            timing = VideoFrameMetadataListener { presentationUs, _, _, _ ->
                if (last >= 0L) gaps += presentationUs - last
                last = presentationUs
                if (gaps.size == RefreshMatch.GAPS_TO_MEASURE) {
                    val fps = RefreshMatch.fps(gaps)
                    view.post {
                        exo.clearVideoFrameMetadataListener(timing)
                        val display = view.display ?: return@post
                        val current = display.mode
                        val modes = display.supportedModes.map { RefreshMatch.Mode(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate) }
                        val here = RefreshMatch.Mode(current.modeId, current.physicalWidth, current.physicalHeight, current.refreshRate)
                        val id = fps?.let { RefreshMatch.pick(it, here, modes) } ?: return@post
                        window.attributes = window.attributes.apply { preferredDisplayModeId = id }
                    }
                }
            }
            exo.setVideoFrameMetadataListener(timing)
            timing
        }
        onDispose { if (listener != null && exo != null) exo.clearVideoFrameMetadataListener(listener) }
    }

    LaunchedEffect(channel?.id, source?.guideOffsetMinutes) {
        val current = channel ?: return@LaunchedEffect
        val loaded = graph.repository.upcoming(current.id, System.currentTimeMillis())
        loadedUpcoming = loaded
        upcomingFor = current.id
    }

    LaunchedEffect(channel?.id, listedNow) {
        val current = channel ?: return@LaunchedEffect
        val listing = listedNow ?: return@LaunchedEffect
        val programmes = graph.repository.observeOnNow(System.currentTimeMillis()).first()
        offsets = graph.repository.sources().associate { it.id to it.guideOffsetMinutes }
        val byKey = programmes.groupBy { it.sourceId to it.channelKey }
        others = withContext(Dispatchers.Default) {
            ChannelFold.foldGuided(SimpleMode.offered(listing.channels, graph.simpleMode.on.value), listing.families).mapNotNull { item ->
                if (item.id == current.id || listing.families.sameFamily(item.id, current.id)) return@mapNotNull null
                val programme = byKey[item.sourceId to item.matchedXmltvId]?.firstOrNull() ?: return@mapNotNull null
                Airing(item, programme)
            }.sortedWith(compareByDescending<Airing> { it.channel.favourite }.thenBy { it.channel.number }).take(8)
        }
    }

    LaunchedEffect(primed, missing, overlay, sideFocus, tracksOpen, stopped, others, cardTunes) {
        if (!primed && !missing) return@LaunchedEffect
        delay(40)
        val target = when {
            tracksOpen -> sheetFocus
            sideFocus -> sideFocusRequester
            stopped -> messageFocus
            overlay -> audioFocus
            else -> rootFocus
        }
        try {
            target.requestFocus()
        } catch (_: IllegalStateException) {
            try {
                rootFocus.requestFocus()
            } catch (_: IllegalStateException) {
                // The screen is leaving.
            }
        }
    }

    fun zap(delta: Int) {
        if (channels.isEmpty()) return
        val next = PlayRules.zapTarget(pendingIndex, delta, channels.size)
        if (next == null) {
            // The end of the list: say so, then let it go. Never over a drop or a start already being worded.
            if (banner == null && stopReason == null) {
                banner = PlayRules.END_OF_LIST
                overlay = true
                scope.launch {
                    delay(2_000)
                    if (banner == PlayRules.END_OF_LIST) banner = null
                }
            }
            return
        }
        // Back onto the channel already on screen inside the wait: nothing restarts, so nothing to time.
        tunedAt[0] = if (next == index) 0L else SystemClock.elapsedRealtime()
        catchupOn = false
        pendingIndex = next
        overlay = true
        banner = null
        stopReason = null
    }

    fun leaveCopy() {
        copy = null
        triedCopies.clear()
        tryingNote = null
        fellBackFrom = null
    }

    fun tune(item: Channel) {
        // The list holds one copy of each channel, so the item may be a sibling of the entry that stands for it.
        val families = listedNow?.families
        val at = channels.indexOfFirst { it.id == item.id || families?.sameFamily(it.id, item.id) == true }
        catchupOn = false
        if (at == pendingIndex) leaveCopy()
        if (at >= 0) {
            pendingIndex = at
        } else {
            channels = channels + item
            pendingIndex = channels.lastIndex
        }
        sideFocus = false
        overlay = true
        banner = null
        stopReason = null
    }

    // Watch on a reminder card while this player is open: a zap to that channel on this same player, with the card's list in place of this one so Up and Down carry on from there.
    LaunchedEffect(Unit) {
        snapshotFlow { primed || missing }.first { it }
        graph.playback.collectTunes { request ->
            // From the moment the request is handled, so the channel load counts.
            val started = SystemClock.elapsedRealtime()
            val loaded = graph.repository.channels(request.channelIds)
            if (loaded.isEmpty()) return@collectTunes
            val startId = request.channelIds.getOrNull(request.index)
            val start = loaded.indexOfFirst { it.id == startId }.let { if (it < 0) 0 else it }
            // Read the state here: this effect lives as long as the screen, so the plain vals below it hold the first frame's values.
            val same = loaded[start].id == (copy ?: channels.getOrNull(index))?.id
            // Already on it but stopped, or on its catch-up: it starts again live, as Retry would. Already on it live, nothing reloads.
            val restart = same && (stopReason != null || (catchupOn && catchup != null))
            // Timed only when a stream starts.
            if (!same || restart) tunedAt[0] = started
            leaveCopy()
            catchupOn = false
            // A new list, so it is folded to one copy of each channel like the first one was.
            zapFolded = false
            channels = loaded
            // No key-repeat wait: a card press is one choice, not a run of presses.
            pendingIndex = start
            index = start
            missing = false
            primed = true
            sideFocus = false
            overlay = true
            banner = null
            if (restart) retryNonce++
            stopReason = null
            cardTunes++
        }
    }

    /** Another copy from Other copies, in place of the listed channel. Only for now: nothing saved changes. */
    fun playCopy(item: Channel) {
        leaveCopy()
        copy = item.takeIf { it.id != channels.getOrNull(pendingIndex)?.id }
        catchupOn = false
        overlay = true
        banner = null
        stopReason = null
    }

    fun rewind() {
        val exo = player
        overlay = true
        if (exo == null || !exo.isCurrentMediaItemSeekable) {
            banner = "This stream can't rewind."
            return
        }
        exo.seekTo((exo.currentPosition - 30_000L).coerceAtLeast(0L))
    }

    /** Starts over from the channel asked for. Catch-up stays on the copy that holds the recording. */
    fun retryNow() {
        val recordingCopy = copy.takeIf { catchupOn && catchup != null }
        leaveCopy()
        copy = recordingCopy
        stopReason = null
        banner = "Reconnecting…"
        retryNonce++
    }

    fun togglePause() {
        val exo = player ?: return
        if (exo.playWhenReady) exo.pause() else exo.play()
        overlay = true
    }

    /** Plays a programme as catch-up on this channel, or a recording kept by another copy. Same as a catch-up the player was opened with. */
    fun playCatchup(request: CatchupRequest, recorded: Channel?) {
        leaveCopy()
        copy = recorded
        catchup = request
        catchupOn = true
        overlay = true
        banner = null
        stopReason = null
        retryNonce++
    }

    /** From the details sheet. Live is what the channel is on already, unless the viewer is watching a recording. */
    fun watchFromSheet(request: CatchupRequest?, recorded: Channel?) {
        when {
            request != null -> playCatchup(request, recorded)
            catchupOn -> {
                catchupOn = false
                retryNonce++
            }
        }
    }

    /** Hold OK: the quick menu, with Pause or Play and Audio & subtitles above the favourite and hide rows. */
    fun openMenu() {
        // While a copy stands in for a failed channel, the menu is about the channel the viewer picked: its favourite state, not the stand-in's.
        val current = (if (copy != null && fellBackFrom == listedChannel?.id) listedChannel else channel) ?: return
        overlay = true
        quick.open(
            current,
            PlayerHooks(
                paused,
                onTogglePause = { togglePause() },
                onTracks = { tracksOpen = true },
                previous = graph.lastChannel.previous(),
                // Looked up afresh: the saved stand-in may be out of date. Playing it makes this one the previous, so it flips back.
                onPrevious = { graph.lastChannel.previous()?.let { back -> scope.launch { graph.repository.channel(back.id)?.let { tune(it) } } } },
                onPlayCopy = { playCopy(it) },
            ),
            programme = upcoming.firstOrNull { GuideClock.isOnNow(it.startUtc, it.stopUtc, System.currentTimeMillis(), source?.guideOffsetMinutes ?: 0) },
            // No recorded copy means the sheet's own channel keeps it, and that can be a copy the viewer chose.
            onWatch = { request, recorded -> watchFromSheet(request, recorded ?: current.takeIf { it.id != listedChannel?.id }) },
        )
    }

    /** OK in the main view: a short press shows the overlay, then pauses or plays; a hold opens the quick menu. */
    fun onOk(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        val at = SystemClock.uptimeMillis()
        if (event.type == KeyEventType.KeyDown) {
            if (event.nativeKeyEvent.repeatCount == 0) {
                okHold.downAt = at
                okHold.fired = false
            } else if (okHold.downAt != 0L && !okHold.fired) { // a repeat only comes once the key is held past the system threshold
                okHold.fired = true
                openMenu()
                okHold.downAt = 0L // the menu window takes the rest of this hold, key-up included
            }
            return true
        }
        if (okHold.downAt == 0L) return true // an OK key-up with no matching key-down here must not click the focused Audio pill
        val held = okHold.fired || at - okHold.downAt >= OK_HOLD_MS
        if (held) {
            if (!okHold.fired) openMenu()
        } else if (!overlay) {
            overlay = true
        } else {
            if (reconnecting) retryNow() else togglePause() // the overlay's Pause (or Retry) button; Audio & subtitles is on Menu or Info and in the hold menu
        }
        okHold.downAt = 0L
        okHold.fired = false
        return true
    }

    BackHandler(enabled = tracksOpen || sideFocus || (overlay && channel != null && !stopped)) {
        when {
            tracksOpen -> tracksOpen = false
            sideFocus -> sideFocus = false
            paused -> togglePause() // Back on a paused picture resumes; the overlay then hides on its own
            else -> overlay = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { event ->
                val isOk = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                if (isOk && !tracksOpen && !sideFocus && !stopped) return@onPreviewKeyEvent onOk(event)
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (tracksOpen) {
                    return@onPreviewKeyEvent when (event.key) {
                        Key.Menu, Key.Info -> {
                            tracksOpen = false
                            true
                        }
                        else -> false
                    }
                }
                if (stopped && !sideFocus) {
                    // The message card owns Left, Right and OK; the channel can still change.
                    return@onPreviewKeyEvent when (event.key) {
                        Key.DirectionUp, Key.ChannelUp -> {
                            zap(-1)
                            true
                        }
                        Key.DirectionDown, Key.ChannelDown -> {
                            zap(1)
                            true
                        }
                        Key.Menu, Key.Info -> {
                            tracksOpen = true
                            true
                        }
                        else -> false
                    }
                }
                if (sideFocus) {
                    return@onPreviewKeyEvent when (event.key) {
                        Key.DirectionLeft -> {
                            sideFocus = false
                            true
                        }
                        Key.Menu, Key.Info -> {
                            tracksOpen = true
                            true
                        }
                        else -> false
                    }
                }
                when (event.key) {
                    Key.DirectionUp, Key.ChannelUp -> {
                        zap(-1)
                        true
                    }
                    Key.DirectionDown, Key.ChannelDown -> {
                        zap(1)
                        true
                    }
                    Key.DirectionRight -> {
                        sideFocus = true
                        overlay = true
                        true
                    }
                    Key.DirectionLeft -> {
                        rewind()
                        true
                    }
                    Key.MediaPlayPause -> {
                        togglePause()
                        true
                    }
                    Key.Menu, Key.Info -> {
                        tracksOpen = true
                        overlay = true
                        true
                    }
                    else -> false
                }
            }
            .focusable(),
    ) {
        val exo = player
        if (exo != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        keepScreenOn = true
                        isFocusable = false
                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        this.player = exo
                    }
                },
                update = { it.player = exo },
            )
        }
        if (missing) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nothing to play.", color = Color.White)
                Spacer(Modifier.height(12.dp))
                Pill("Back") { onExit() }
            }
        }
        if (channel != null) {
            val offset = source?.guideOffsetMinutes ?: 0
            val inCatchup = catchupOn && catchup != null
            val current = upcoming.firstOrNull { GuideClock.isOnNow(it.startUtc, it.stopUtc, now, offset) }
            val next = upcoming.firstOrNull { GuideClock.shift(it.startUtc, offset) > now }
            AnimatedVisibility(
                visible = overlay && !sideFocus && !tracksOpen && !stopped,
                enter = panelEnter(slide = false),
                exit = panelExit(slide = false),
            ) {
                Overlay(
                    channel = channel,
                    catchup = if (inCatchup) catchup else null,
                    current = current,
                    next = next,
                    offset = offset,
                    now = now,
                    banner = banner,
                    paused = paused,
                    audioFocus = audioFocus,
                    reconnecting = reconnecting,
                    onTogglePause = { if (reconnecting) retryNow() else togglePause() },
                    player = exo,
                )
            }
            AnimatedVisibility(
                visible = sideFocus && !tracksOpen,
                enter = panelEnter(slide = false),
                exit = panelExit(slide = false),
            ) {
                Box(Modifier.fillMaxSize()) {
                    WatchingPill(
                        channel = channel,
                        programme = if (inCatchup) catchup?.title else current?.title,
                        live = !inCatchup,
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = PorchSpace.Margin, bottom = PorchSpace.Margin),
                    )
                }
            }
            AnimatedVisibility(
                visible = sideFocus && !tracksOpen,
                modifier = Modifier.align(Alignment.CenterEnd),
                enter = panelEnter(slide = true),
                exit = panelExit(slide = true),
            ) {
                SidePanel(
                    others = others,
                    offsets = offsets,
                    now = now,
                    firstFocus = sideFocusRequester,
                    onTune = ::tune,
                    onClose = { sideFocus = false },
                )
            }
            val reason = stopReason
            if (reason != null && !sideFocus && !tracksOpen) {
                MessageCard(
                    channel = channel,
                    title = when (reason) {
                        is StopReason.LoginRefused -> "Login was refused."
                        StopReason.CatchupUnavailable -> "Catch-up isn't available on this channel."
                        StopReason.Dropped -> "The stream dropped."
                    },
                    body = when (reason) {
                        is StopReason.LoginRefused -> (if (reason.code != null) "Error code ${reason.code}. " else "") + "Porch will not keep retrying."
                        StopReason.CatchupUnavailable -> null
                        StopReason.Dropped -> "Press Retry to try again. If it keeps dropping, the provider may be busy; try another channel."
                    },
                    retry = reason == StopReason.Dropped,
                    firstFocus = messageFocus,
                    onRetry = ::retryNow,
                    onElse = { sideFocus = true },
                    onHome = onExit,
                )
            }
        }
        AnimatedVisibility(
            visible = tracksOpen && exo != null,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = panelEnter(slide = true),
            exit = panelExit(slide = true),
        ) {
            if (exo != null) TrackPanel(exo, trackVersion, sheetFocus, Modifier)
        }
    }

    LaunchedEffect(primed, missing) {
        if (primed || missing) {
            try {
                rootFocus.requestFocus()
            } catch (_: IllegalStateException) {
                // The screen is leaving.
            }
        }
    }
}

@Composable
private fun panelEnter(slide: Boolean): EnterTransition = when {
    !LocalMotionEnabled.current -> EnterTransition.None
    slide -> slideInHorizontally(tween(PorchMotion.PanelMs)) { it }
    else -> fadeIn(tween(PorchMotion.PanelMs))
}

@Composable
private fun panelExit(slide: Boolean): ExitTransition = when {
    !LocalMotionEnabled.current -> ExitTransition.None
    slide -> slideOutHorizontally(tween(PorchMotion.PanelMs)) { it }
    else -> fadeOut(tween(PorchMotion.PanelMs))
}

private val PanelShape = RoundedCornerShape(topStart = PorchRadius.Panel, bottomStart = PorchRadius.Panel)

/** A panel row: no fill at rest, the solid Focus fill with OnFocus text when focused, like every other row. */
@Composable
private fun panelRowColors(container: Color) = ClickableSurfaceDefaults.colors(
    containerColor = container,
    contentColor = PorchColors.Text,
    focusedContainerColor = PorchColors.Focus,
    focusedContentColor = PorchColors.OnFocus,
    pressedContainerColor = PorchColors.Focus,
    pressedContentColor = PorchColors.OnFocus,
)

@Composable
private fun Overlay(
    channel: Channel,
    catchup: CatchupRequest?,
    current: Programme?,
    next: Programme?,
    offset: Int,
    now: Long,
    banner: String?,
    paused: Boolean,
    audioFocus: FocusRequester,
    reconnecting: Boolean,
    onTogglePause: () -> Unit,
    player: ExoPlayer?,
    modifier: Modifier = Modifier,
) {
    val title = catchup?.title ?: current?.title ?: channel.shownTitle
    val start = when {
        catchup != null -> catchup.startUtc
        current != null -> GuideClock.shift(current.startUtc, offset)
        else -> null
    }
    val stop = when {
        catchup != null -> catchup.stopUtc
        current != null -> GuideClock.shift(current.stopUtc, offset)
        else -> null
    }
    val progress = when {
        catchup != null && player != null && stop != null && start != null -> {
            val span = (stop - start).coerceAtLeast(1L)
            (player.currentPosition.toFloat() / span.toFloat()).coerceIn(0f, 1f)
        }
        current != null && start != null && stop != null -> progressOf(start, stop, now)
        else -> 0f
    }
    val scrim = PorchColors.ScrimStops.map { PorchColors.Scrim.copy(alpha = it) }
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(PorchSpace.ScrimHeight)
                .background(Brush.verticalGradient(0f to scrim[0], 0.45f to scrim[1], 1f to scrim[2])),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = PorchSpace.Margin, end = PorchSpace.Margin, bottom = PorchSpace.TileGap),
        ) {
            banner?.let {
                Text(it, style = PorchType.Body, color = PorchColors.Text)
                Spacer(Modifier.height(PorchSpace.TileGap))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PorchSpace.TileGap)) {
                if (catchup == null) LiveChip() else Text("Catch-up", style = PorchType.Meta)
                ChannelLogo(channel, channelColor(channel), PorchSpace.PlayerBadge, PorchType.TileTitle)
                Text(channel.shownTitle, style = PorchType.TileTitle, color = PorchColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("· ${channel.number}", style = PorchType.TileTitle, color = PorchColors.Muted, maxLines = 1)
            }
            Spacer(Modifier.height(PorchSpace.TileGap))
            Text(title, style = PorchType.PlayerTitle, color = PorchColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = OverlayText.of(start, stop, catchup != null, now)
            if (line.isNotEmpty()) {
                Spacer(Modifier.height(PorchSpace.ListGap))
                Text(line, style = PorchType.Body, color = PorchColors.TextSecondary)
                Spacer(Modifier.height(PorchSpace.ListGap))
                ProgressTrack(progress, Modifier.fillMaxWidth(), fill = if (catchup != null) PorchColors.Muted else PorchColors.Live)
            }
            Spacer(Modifier.height(PorchSpace.TileGap))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(PorchSpace.NextGap)) {
                    if (next != null) {
                        Text("Next on this channel", style = PorchType.Body.copy(fontWeight = FontWeight.SemiBold), color = PorchColors.Text, maxLines = 1)
                        Text(formatTime(GuideClock.shift(next.startUtc, offset)), style = PorchType.Body, color = PorchColors.Muted, maxLines = 1)
                        Text(next.title, style = PorchType.Body, color = PorchColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                // The Pause button holds focus while the overlay is up, so OK pauses without a hold. While reconnecting it is Retry.
                Pill(if (reconnecting) "Retry" else if (paused) "Play" else "Pause", modifier = Modifier.focusRequester(audioFocus), onClick = onTogglePause)
            }
            Spacer(Modifier.height(PorchSpace.TileGap))
            Row(horizontalArrangement = Arrangement.spacedBy(PorchSpace.HomeTileGap)) {
                for (hint in listOf(if (reconnecting) "OK tries again · Up / Down change channel" else if (paused) "Hold OK for menu" else "OK pauses · Up / Down change channel", "Left rewinds", "Right: what else is on", "Back hides this")) {
                    Text(hint, style = PorchType.Hint, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SidePanel(
    others: List<Airing>,
    offsets: Map<Long, Int>,
    now: Long,
    firstFocus: FocusRequester,
    onTune: (Channel) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(PorchSpace.SidePanelWidth)
            .fillMaxHeight()
            .background(PorchColors.SidePanel, PanelShape)
            .padding(PorchSpace.PanelPad),
    ) {
        Text("What else is on", style = PorchType.ScreenTitle, color = PorchColors.Text)
        Text("Favourites first · OK switches", style = PorchType.Meta)
        Spacer(Modifier.height(PorchSpace.TileGap))
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(PorchSpace.ListGap)) {
            if (others.isEmpty()) {
                Text("Nothing else is listed right now.", style = PorchType.Body, color = PorchColors.Muted)
                Pill("Back to the picture", modifier = Modifier.focusRequester(firstFocus), onClick = onClose)
            } else {
                others.forEachIndexed { itemIndex, airing ->
                    val itemOffset = offsets[airing.channel.sourceId] ?: 0
                    val startAt = GuideClock.shift(airing.programme.startUtc, itemOffset)
                    val stopAt = GuideClock.shift(airing.programme.stopUtc, itemOffset)
                    SideCard(
                        channel = airing.channel,
                        title = airing.programme.title,
                        subtitle = "${airing.channel.shownTitle} · ${formatLeft(stopAt - now)}",
                        progress = progressOf(startAt, stopAt, now),
                        modifier = if (itemIndex == 0) Modifier.focusRequester(firstFocus) else Modifier,
                        onClick = { onTune(airing.channel) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SideCard(
    channel: Channel,
    title: String,
    subtitle: String,
    progress: Float,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(PorchRadius.Tile)
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.hasFocus },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = panelRowColors(container = PorchColors.Surface),
        border = ClickableSurfaceDefaults.border(),
        scale = clickScale(),
        glow = ClickableSurfaceDefaults.glow(),
    ) {
        Row(Modifier.padding(PorchSpace.HomeTilePad), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(PorchSpace.PanelBadgeSlot), contentAlignment = Alignment.Center) {
                ChannelLogo(channel, channelColor(channel), PorchSpace.PanelBadge, PorchType.TileTitle)
            }
            Spacer(Modifier.width(PorchSpace.HomeTilePad))
            Column(Modifier.weight(1f)) {
                Text(title, style = PorchType.TileTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = PorchType.Meta, color = if (focused) PorchColors.OnFocus else PorchColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(PorchSpace.ListGap))
                // On the Focus fill the track is Line, so the Live fill still reads.
                ProgressTrack(progress, track = if (focused) PorchColors.Line else PorchColors.Faint)
            }
        }
    }
}

/** Bottom-left while the panel is open: what is playing now. */
@Composable
private fun WatchingPill(channel: Channel, programme: String?, live: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(PorchColors.Surface, RoundedCornerShape(PorchRadius.Row))
            .padding(horizontal = PorchSpace.HomeTilePad, vertical = PorchSpace.ListGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PorchSpace.HomeTilePad),
    ) {
        if (live) LiveChip()
        ChannelLogo(channel, channelColor(channel), PorchSpace.ChipBadge, PorchType.LiveLabel)
        Text(
            "Watching ${channel.shownTitle}" + (programme?.let { " · $it" } ?: ""),
            style = PorchType.Body,
            color = PorchColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The dimmed picture and a centred card when playback has stopped for good. */
@Composable
private fun MessageCard(
    channel: Channel,
    title: String,
    body: String?,
    retry: Boolean,
    firstFocus: FocusRequester,
    onRetry: () -> Unit,
    onElse: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(PorchColors.Dim)) {
        Row(
            Modifier.align(Alignment.TopStart).padding(start = PorchSpace.CardBadgeLeft, top = PorchSpace.CardBadgeTop),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PorchSpace.TileGap),
        ) {
            ChannelLogo(channel, channelColor(channel), PorchSpace.ChipBadge, PorchType.LiveLabel)
            Text("${channel.shownTitle} · ${channel.number}", style = PorchType.Body, color = PorchColors.TextSecondary, maxLines = 1)
        }
        Column(
            Modifier
                .align(Alignment.Center)
                .width(PorchSpace.MessageCardWidth)
                .background(PorchColors.Surface, RoundedCornerShape(PorchRadius.Panel))
                .padding(PorchSpace.CardPad),
        ) {
            Text(title, style = PorchType.ScreenTitle, color = PorchColors.Text)
            if (body != null) {
                Spacer(Modifier.height(PorchSpace.ListGap))
                Text(body, style = PorchType.Body, color = PorchColors.TextSecondary)
            }
            Spacer(Modifier.height(PorchSpace.RowGap))
            Row(horizontalArrangement = Arrangement.spacedBy(PorchSpace.ListGap)) {
                var first = true
                fun focusFirst(): Modifier = if (first) { first = false; Modifier.focusRequester(firstFocus) } else Modifier
                if (retry) Pill("Retry", plain = true, restColor = PorchColors.TextSecondary, modifier = focusFirst(), onClick = onRetry)
                Pill("What else is on", plain = true, restColor = PorchColors.TextSecondary, modifier = focusFirst(), onClick = onElse)
                Pill("Back to Home", plain = true, restColor = PorchColors.TextSecondary, onClick = onHome)
            }
        }
    }
}

private const val OK_HOLD_MS = 500L

/** Why playback stopped for good. The banner text stays for the overlay line; the message card builds from this. */
private sealed class StopReason {
    object Dropped : StopReason()
    data class LoginRefused(val code: Int?) : StopReason()
    object CatchupUnavailable : StopReason()
}

private class OkHold {
    var downAt = 0L
    var fired = false
}

private class TrackRow(val label: String, val selected: Boolean, val pick: () -> Unit)

@Composable
private fun SheetRow(row: TrackRow, modifier: Modifier) {
    val shape = RoundedCornerShape(PorchRadius.Tile)
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = row.pick,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.hasFocus },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = panelRowColors(container = Color.Transparent),
        border = ClickableSurfaceDefaults.border(),
        scale = clickScale(),
        glow = ClickableSurfaceDefaults.glow(),
    ) {
        Row(
            Modifier.padding(horizontal = PorchSpace.TileGap, vertical = PorchSpace.SheetRowPadY),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(PorchSpace.TrackDot)
                    .clip(CircleShape)
                    .then(
                        if (row.selected) Modifier.background(if (focused) PorchColors.OnFocus else PorchColors.Text)
                        else Modifier.border(PorchFocus.Ring, PorchColors.Line, CircleShape),
                    ),
            )
            Spacer(Modifier.width(PorchSpace.TileGap))
            Text(row.label, style = PorchType.Body, color = if (focused) PorchColors.OnFocus else if (row.selected) PorchColors.Text else PorchColors.TextSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun TrackPanel(player: ExoPlayer, version: Int, firstFocus: FocusRequester, modifier: Modifier) {
    val tracks = player.currentTracks
    @Suppress("UNUSED_VARIABLE")
    val redraw = version
    val audioRaw = mutableListOf<TrackRow>()
    val audioLabels = mutableListOf<String?>()
    var number = 0
    tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.forEach { group ->
        for (index in 0 until group.length) {
            number++
            val format = group.getTrackFormat(index)
            val described = format.roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO != 0
            val name = if (!TrackNames.isKnown(format.language) && !format.label.isNullOrBlank()) format.label!! else TrackNames.audio(format.language, number, described)
            audioLabels += format.label
            audioRaw += TrackRow(name, group.isTrackSelected(index)) {
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                    .build()
            }
        }
    }
    val audioNames = TrackNames.dedupe(audioRaw.map { it.label }, audioLabels)
    val audio = audioRaw.mapIndexed { i, row -> TrackRow(audioNames[i], row.selected, row.pick) }
    val textOff = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
    val anyText = tracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
    val subtitleLabels = mutableListOf<String?>(null)
    val subtitlesRaw = mutableListOf(
        TrackRow("Off", textOff || !anyText) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .build()
        },
    )
    number = 0
    tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.forEach { group ->
        for (index in 0 until group.length) {
            number++
            val format = group.getTrackFormat(index)
            val hearing = format.roleFlags and (C.ROLE_FLAG_CAPTION or C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND) != 0
            val name = if (!TrackNames.isKnown(format.language) && !format.label.isNullOrBlank()) format.label!! else TrackNames.subtitle(format.language, number, hearing)
            subtitleLabels += format.label
            subtitlesRaw += TrackRow(name, group.isTrackSelected(index) && !textOff) {
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                    .build()
            }
        }
    }
    val subtitleNames = TrackNames.dedupe(subtitlesRaw.map { it.label }, subtitleLabels)
    val subtitles = subtitlesRaw.mapIndexed { i, row -> TrackRow(subtitleNames[i], row.selected, row.pick) }
    Column(
        modifier
            .width(PorchSpace.SheetWidth)
            .fillMaxHeight()
            .background(PorchColors.SidePanel, PanelShape)
            .padding(PorchSpace.PanelPad),
    ) {
        Text("Audio & subtitles", style = PorchType.ScreenTitle, color = PorchColors.Text)
        Text("OK picks · Back closes", style = PorchType.Meta)
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(PorchSpace.TileGap))
            Text("Audio", style = PorchType.Meta, modifier = Modifier.padding(horizontal = PorchSpace.TileGap))
            if (audio.isEmpty()) Text("No extra audio tracks.", style = PorchType.Body, color = PorchColors.Muted, modifier = Modifier.padding(PorchSpace.TileGap))
            audio.forEachIndexed { i, row -> SheetRow(row, if (i == 0) Modifier.focusRequester(firstFocus) else Modifier) }
            Spacer(Modifier.height(PorchSpace.TileGap))
            Text("Subtitles", style = PorchType.Meta, modifier = Modifier.padding(horizontal = PorchSpace.TileGap))
            subtitles.forEachIndexed { i, row -> SheetRow(row, if (i == 0 && audio.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier) }
        }
    }
}

private fun playbackUrl(
    channel: Channel,
    source: SourceEntity?,
    catchup: CatchupRequest?,
    hls: Boolean,
): String? {
    if (catchup != null) {
        val server = source?.xtreamServer
        val user = source?.xtreamUsername
        val pass = source?.xtreamPassword
        val streamId = channel.xtreamStreamId
        if (source?.kind != "xtream" || server.isNullOrBlank() || user.isNullOrBlank() || pass.isNullOrBlank() || streamId == null) {
            return null
        }
        return XtreamUrls.catchup(
            server,
            user,
            pass,
            streamId,
            catchupStart(catchup.startUtc, source.guideOffsetMinutes),
            catchupMinutes(catchup.startUtc, catchup.stopUtc),
            XtreamUrls.zoneOf(source.serverTimeZone),
        )
    }
    val raw = channel.streamUrl
    if (!hls) return raw
    val base = raw.substringBefore('?')
    if (!base.endsWith(".ts", ignoreCase = true)) return raw
    val swapped = base.dropLast(3) + ".m3u8"
    val query = raw.substringAfter('?', missingDelimiterValue = "")
    return if (query.isEmpty()) swapped else "$swapped?$query"
}

private fun isFormatError(error: PlaybackException): Boolean {
    if (error.cause is ParserException) return true
    return error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
        error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
        error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ||
        error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED
}

private fun httpCode(error: PlaybackException): Int? {
    var cause: Throwable? = error
    while (cause != null) {
        if (cause is HttpDataSource.InvalidResponseCodeException) return cause.responseCode
        cause = cause.cause
    }
    return null
}

private suspend fun awaitReady(player: Player): PlaybackException? = suspendCancellableCoroutine { cont ->
    val existing = player.playerError
    if (existing != null) {
        cont.resume(existing)
        return@suspendCancellableCoroutine
    }
    if (player.playbackState == Player.STATE_READY) {
        cont.resume(null)
        return@suspendCancellableCoroutine
    }
    val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            player.removeListener(this)
            if (cont.isActive) cont.resume(error)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY && player.playerError == null) {
                player.removeListener(this)
                if (cont.isActive) cont.resume(null)
            }
        }
    }
    player.addListener(listener)
    cont.invokeOnCancellation { player.removeListener(listener) }
}

/**
 * Waits for the playing stream to drop. Resumes with the error when there is one, or with null when
 * a live stream ends cleanly or wants to play but sits buffering past [StreamHealth.STALL_LIMIT_MS].
 * Catch-up only drops on an error: its end is normal, and a slow catch-up server must not send the
 * viewer back to the programme start.
 */
private suspend fun awaitDrop(player: Player, live: Boolean): PlaybackException? {
    val events = EventChannel<Unit>(EventChannel.CONFLATED)
    val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            events.trySend(Unit)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            events.trySend(Unit)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            events.trySend(Unit)
        }
    }
    player.addListener(listener)
    try {
        var bufferingSince: Long? = null
        while (true) {
            val now = SystemClock.elapsedRealtime()
            val state = player.playbackState
            // The stall clock runs only on live while the player wants to play. A paused player can
            // sit in BUFFERING as long as it likes, and catch-up never stalls out, so neither may let
            // the timeout below reach zero and spin.
            val waiting = live && state == Player.STATE_BUFFERING && player.playWhenReady
            bufferingSince = if (waiting) bufferingSince ?: now else null
            val bufferingFor = bufferingSince?.let { now - it } ?: 0L
            val drop = StreamHealth.drop(healthState(state), player.playerError != null, live, bufferingFor)
            if (drop != null) return player.playerError
            if (bufferingSince != null) {
                withTimeoutOrNull(StreamHealth.STALL_LIMIT_MS - bufferingFor) { events.receive() }
            } else {
                events.receive()
            }
        }
    } finally {
        player.removeListener(listener)
    }
}

private fun healthState(playbackState: Int): StreamHealth.State = when (playbackState) {
    Player.STATE_BUFFERING -> StreamHealth.State.BUFFERING
    Player.STATE_READY -> StreamHealth.State.READY
    Player.STATE_ENDED -> StreamHealth.State.ENDED
    else -> StreamHealth.State.IDLE
}
