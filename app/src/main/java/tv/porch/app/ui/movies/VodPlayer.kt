@file:OptIn(UnstableApi::class)

package tv.porch.app.ui.movies

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.porch.app.domain.Films
import tv.porch.app.ui.components.tryFocus
import tv.porch.app.ui.theme.LocalMotionEnabled
import tv.porch.app.ui.theme.PorchColors
import tv.porch.app.ui.theme.PorchMotion
import tv.porch.app.ui.theme.PorchRadius
import tv.porch.app.ui.theme.PorchSpace
import tv.porch.app.ui.theme.PorchType

private const val SKIP_MS = 10_000L
private const val OVERLAY_MS = 4_000L
private const val SAVE_EVERY_MS = 30_000L
private val BarWidth = 300.dp

/** Saving a resume point must finish even as the screen leaves, so it does not run in the screen's scope. */
private val saver = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * Plays one film or episode on its own player. Left and Right skip 10 s (a held key repeats), OK shows the panel and then
 * pauses or plays, Back leaves. [save] is called every 30 s while playing, on pause, on stop, on leaving and at the end.
 * A null [title] means there is nothing to play, and the failure text shows at once. [url] gives the address, or null when
 * it can't be built. [failedText] is what the failure screen says.
 */
@Composable
internal fun VodPlayer(
    title: String?,
    startAtMs: Long,
    failedText: String,
    url: suspend () -> String?,
    save: suspend (positionMs: Long, durationMs: Long) -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val exit by rememberUpdatedState(onExit)
    val saveNow by rememberUpdatedState(save)
    val urlNow by rememberUpdatedState(url)
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var failed by remember { mutableStateOf(title == null) }
    var paused by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf(true) }
    var cantSkip by remember { mutableStateOf(false) }
    var nonce by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableLongStateOf(startAtMs) }
    var durationMs by remember { mutableLongStateOf(0L) }
    val rootFocus = remember { FocusRequester() }

    fun save(exo: ExoPlayer) {
        if (title == null) return
        // A stream with no length still saves where it got to; its length is 0.
        val duration = exo.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
        val position = exo.currentPosition
        if (duration == 0L && position <= 0L) return
        saver.launch { runCatching { saveNow(position, duration) } }
    }

    DisposableEffect(context) {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Porch/1.0 (Android TV)")
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)
        // If a hardware decoder fails to start, try the next one down (the software decoder) before giving up.
        val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        val exo = ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
        exo.setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            true,
        )
        exo.playWhenReady = true
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                paused = !playWhenReady
                if (!playWhenReady) save(exo)
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    // The end saves the final position, which clears the point at 95% or more.
                    // With no length from the stream, where it got to stands in for it.
                    val known = exo.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
                    val end = Films.endMark(exo.currentPosition, known)
                    if (title != null && end > 0L) {
                        saver.launch { runCatching { saveNow(end, end) } }
                    }
                    exit()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                failed = true
            }
        }
        exo.addListener(listener)
        player = exo
        onDispose {
            exo.removeListener(listener)
            if (exo.playbackState != Player.STATE_ENDED) save(exo)
            exo.release()
            player = null
        }
    }

    // Leaving the app ends the sound and frees the provider's connection; the point is saved first.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player?.let { exo ->
                    save(exo)
                    exo.pause()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The address is worked out again when the film or episode changes, so a new one on the same player loads its own stream.
    var address by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(player, title) {
        val exo = player ?: return@LaunchedEffect
        // Drop whatever was playing first, so it can't keep going (or save its position under the new title) while the address is worked out.
        exo.stop()
        exo.clearMediaItems()
        address = null
        positionMs = startAtMs
        durationMs = 0L
        if (title == null) {
            failed = true
            return@LaunchedEffect
        }
        val resolved = urlNow()
        failed = resolved == null
        address = resolved
    }
    LaunchedEffect(player, address) {
        val exo = player ?: return@LaunchedEffect
        val stream = address ?: return@LaunchedEffect
        if (startAtMs > 0L) exo.setMediaItem(MediaItem.fromUri(stream), startAtMs) else exo.setMediaItem(MediaItem.fromUri(stream))
        exo.prepare()
        exo.play()
    }

    LaunchedEffect(player) {
        val exo = player ?: return@LaunchedEffect
        while (true) {
            delay(SAVE_EVERY_MS)
            if (exo.isPlaying) save(exo)
        }
    }

    // The panel shows on OK and on a skip, and fades after four seconds. It stays up while paused.
    LaunchedEffect(nonce, paused) {
        overlay = true
        if (!paused) {
            delay(OVERLAY_MS)
            overlay = false
        }
    }
    LaunchedEffect(overlay, player) {
        val exo = player ?: return@LaunchedEffect
        while (overlay) {
            positionMs = exo.currentPosition
            durationMs = exo.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
            delay(500)
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        rootFocus.tryFocus()
    }

    BackHandler(enabled = true) { exit() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { event ->
                val exo = player
                if (event.type != KeyEventType.KeyDown || exo == null || failed) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft, Key.DirectionRight -> {
                        val step = if (event.key == Key.DirectionLeft) -SKIP_MS else SKIP_MS
                        val length = exo.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
                        val target = Films.skipTarget(exo.currentPosition, step, length, exo.isCurrentMediaItemSeekable)
                        if (target == null) {
                            // A stream with no length can't seek: seeking would restart it from 0:00.
                            cantSkip = true
                        } else {
                            cantSkip = false
                            exo.seekTo(target)
                            positionMs = target
                        }
                        nonce++
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (event.nativeKeyEvent.repeatCount == 0) {
                            if (!overlay) {
                                nonce++
                            } else if (exo.playWhenReady) {
                                exo.pause()
                            } else {
                                exo.play()
                                nonce++
                            }
                        }
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
        if (failed) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(failedText, style = PorchType.Body, color = PorchColors.Text)
                Spacer(Modifier.height(PorchSpace.ListGap))
                Text("Back closes the player", style = PorchType.Hint)
            }
        } else {
            val motion = LocalMotionEnabled.current
            AnimatedVisibility(
                visible = overlay,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = if (motion) fadeIn(tween(PorchMotion.PanelMs)) else EnterTransition.None,
                exit = if (motion) fadeOut(tween(PorchMotion.PanelMs)) else ExitTransition.None,
            ) {
                Column(
                    Modifier
                        .padding(PorchSpace.Margin)
                        .fillMaxWidth()
                        .background(PorchColors.Surface, RoundedCornerShape(PorchRadius.Panel))
                        .padding(PorchSpace.PanelPad),
                ) {
                    Text(title.orEmpty(), style = PorchType.PlayerTitle, color = PorchColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(PorchSpace.ListGap))
                    Text(Films.clockPair(positionMs, durationMs), style = PorchType.Meta, maxLines = 1)
                    if (cantSkip) {
                        Spacer(Modifier.height(PorchSpace.ListGap))
                        Text("This film can't skip.", style = PorchType.Body, color = PorchColors.Text)
                    }
                    Spacer(Modifier.height(PorchSpace.ListGap))
                    ResumeBar(if (durationMs > 0L) positionMs.toFloat() / durationMs else 0f, BarWidth)
                }
            }
        }
    }
}
