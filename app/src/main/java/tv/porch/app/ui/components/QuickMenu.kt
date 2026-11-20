package tv.porch.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tv.porch.app.AppGraph
import tv.porch.app.domain.Channel
import tv.porch.app.domain.ChannelBadge
import tv.porch.app.domain.ChannelFold
import tv.porch.app.domain.ChannelNames
import tv.porch.app.domain.OkHoldGuard
import tv.porch.app.domain.Programme
import tv.porch.app.domain.QuickAction
import tv.porch.app.domain.QuickMenu
import tv.porch.app.domain.SimpleMode
import tv.porch.app.player.CatchupRequest
import tv.porch.app.ui.theme.PorchColors
import tv.porch.app.ui.theme.PorchRadius
import tv.porch.app.ui.theme.PorchSpace
import tv.porch.app.ui.theme.PorchType

/** What the player adds to the menu: its pause state and the two things only it can do. */
class PlayerHooks(
    val paused: Boolean,
    val onTogglePause: () -> Unit,
    val onTracks: () -> Unit,
    /** The channel before this one in this session, or null when there is none. */
    val previous: Channel? = null,
    val onPrevious: () -> Unit = {},
    /** Plays another copy of this channel, for now. */
    val onPlayCopy: (Channel) -> Unit = {},
)

internal class MenuRequest(
    val channel: Channel,
    val player: PlayerHooks?,
    val onPlayCopy: ((Channel) -> Unit)?,
    val programme: Programme?,
    val onWatch: ((CatchupRequest?, Channel?) -> Unit)?,
)

/** The programme details sheet. [onWatch] gets the catch-up to play (null for live) and the copy that keeps it (null for this channel). */
internal class AboutRequest(val channel: Channel, val programme: Programme, val onWatch: (CatchupRequest?, Channel?) -> Unit)

/** The Other copies panel, opened from the menu. */
private class CopiesPanel(val title: String, val copies: List<Channel>, val onPlay: (Channel) -> Unit)

internal class MenuMessage(val text: String, val millis: Long)

/** Held by PorchApp. A screen calls [open] on a hold of OK; the host draws the menu and the message. */
class QuickMenuState {
    private var request by mutableStateOf<MenuRequest?>(null)
    private var message by mutableStateOf<MenuMessage?>(null)
    private var about by mutableStateOf<AboutRequest?>(null)

    /**
     * Opens the menu for [channel]. Pass [player] from the player so Pause and Audio & subtitles join the rows.
     * A screen that can start another copy of the channel passes [onPlayCopy], and Other copies joins the rows.
     * A screen that knows the programme passes it with [onWatch], and "About this programme" joins the rows.
     */
    fun open(
        channel: Channel,
        player: PlayerHooks? = null,
        onPlayCopy: ((Channel) -> Unit)? = null,
        programme: Programme? = null,
        onWatch: ((CatchupRequest?, Channel?) -> Unit)? = null,
    ) {
        request = MenuRequest(channel, player, player?.onPlayCopy ?: onPlayCopy, programme, onWatch)
    }

    /** Opens the details sheet straight away, as the guide does on OK for a programme that hasn't started. */
    fun openAbout(channel: Channel, programme: Programme, onWatch: (CatchupRequest?, Channel?) -> Unit) {
        about = AboutRequest(channel, programme, onWatch)
    }

    internal fun requestOrNull() = request
    internal fun messageOrNull() = message
    internal fun close() { request = null }
    internal fun aboutOrNull() = about
    internal fun closeAbout() { about = null }
    internal fun say(text: String, millis: Long) { message = MenuMessage(text, millis) }
    internal fun clearMessage() { message = null }
}

val LocalQuickMenu = staticCompositionLocalOf { QuickMenuState() }

/** The quick menu panel and the short message that follows a pick. Sits over the whole app. */
@Composable
fun QuickMenuHost(graph: AppGraph, state: QuickMenuState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val request = state.requestOrNull()
    var panel by remember { mutableStateOf<CopiesPanel?>(null) }
    if (request != null) {
        // Read the channel afresh: a list built earlier may not know a favourite that was just set.
        val channel by produceState<Channel?>(null, request) { value = graph.repository.channel(request.channel.id) ?: request.channel }
        val shown = channel
        // From the shared list, which the screen below is already keeping. If it's still loading, the row joins when it's ready.
        val copies by produceState(emptyList<Channel>(), request) {
            if (request.onPlayCopy != null) value = graph.folds.listing.first().families.othersOf(request.channel.id)
        }
        // Like the copies: the name shows at once and the count joins the same row when it arrives, so no row moves.
        val groupCount by produceState<Int?>(null, shown?.sourceId, shown?.group) {
            val channelNow = shown
            if (channelNow != null && channelNow.group.isNotBlank()) value = graph.repository.groupSize(channelNow.sourceId, channelNow.group)
        }
        if (shown != null) {
            QuickMenuDialog(
                channel = shown,
                otherCopies = copies.size,
                groupCount = groupCount,
                inPlayer = request.player != null,
                hasProgramme = request.programme != null && request.onWatch != null,
                paused = request.player?.paused ?: false,
                previous = request.player?.previous,
                onClose = state::close,
                onPick = { action ->
                    state.close()
                    when (action) {
                        QuickAction.LastChannel -> request.player?.onPrevious?.invoke()
                        QuickAction.Pause, QuickAction.Play -> request.player?.onTogglePause()
                        QuickAction.Tracks -> request.player?.let { it.onTracks() }
                        QuickAction.About -> if (request.programme != null && request.onWatch != null) state.openAbout(shown, request.programme, request.onWatch)
                        QuickAction.AddFavourite -> scope.launch { graph.repository.setFavourite(shown.id, true) }
                        QuickAction.RemoveFavourite -> scope.launch { graph.repository.setFavourite(shown.id, false) }
                        QuickAction.OtherCopies -> request.onPlayCopy?.let { panel = CopiesPanel(shown.shownTitle, copies, it) }
                        QuickAction.HideGroup -> scope.launch { graph.repository.setGroupHidden(shown.sourceId, shown.group, true) }
                    }
                    QuickMenu.message(action)?.let { state.say(it, QuickMenu.messageMillis(action)) }
                },
            )
        }
    }
    panel?.let { open ->
        WatchOnPanel(
            title = open.title,
            channels = open.copies,
            heading = "Other copies",
            rowLabel = ChannelFold::copyLabel,
            onPlay = { copy ->
                panel = null
                open.onPlay(copy)
            },
            onClose = { panel = null },
        )
    }
    state.aboutOrNull()?.let { open ->
        ProgrammeSheet(
            graph = graph,
            channel = open.channel,
            programme = open.programme,
            onWatch = open.onWatch,
            onClose = state::closeAbout,
        )
    }
    val message = state.messageOrNull()
    if (message != null) {
        LaunchedEffect(message) {
            delay(message.millis)
            state.clearMessage()
        }
        Box(modifier.padding(bottom = PorchSpace.Margin), contentAlignment = Alignment.BottomCenter) {
            Text(
                message.text,
                style = PorchType.Body,
                color = PorchColors.Bg,
                modifier = Modifier
                    // Light on dark, like a focused row, so it reads over any tile or video.
                    .background(PorchColors.Text, RoundedCornerShape(PorchRadius.Panel))
                    .padding(horizontal = PorchSpace.PanelPad, vertical = PorchSpace.TileGap),
            )
        }
    }
}

@Composable
private fun QuickMenuDialog(
    channel: Channel,
    otherCopies: Int,
    groupCount: Int?,
    inPlayer: Boolean,
    hasProgramme: Boolean,
    paused: Boolean,
    previous: Channel?,
    onClose: () -> Unit,
    onPick: (QuickAction) -> Unit,
) {
    val simple = LocalSimpleMode.current
    val rows = remember(channel, inPlayer, hasProgramme, paused, previous, otherCopies, simple) {
        SimpleMode.menuRows(QuickMenu.rows(channel, inPlayer, paused, hasPrevious = previous != null, otherCopies = otherCopies, hasProgramme = hasProgramme), simple)
    }
    val groupName = remember(channel.group) { ChannelNames.group(channel.group).title.takeIf { channel.group.isNotBlank() } }
    val first = remember { FocusRequester() }
    val guard = remember { OkHoldGuard() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    // Back closes the dialog; the window below keeps the focus it had.
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .backGoesBack()
                .width(PorchSpace.SheetWidth)
                .background(PorchColors.Surface, RoundedCornerShape(PorchRadius.Panel))
                .padding(PorchSpace.PanelPad)
                .onPreviewKeyEvent { event ->
                    val ok = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                    ok && guard.swallows(event.type == KeyEventType.KeyDown, event.nativeKeyEvent.repeatCount)
                },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelLogo(channel, Color(ChannelBadge.color(channel.name, channel.group)), PorchSpace.PanelBadge)
                Spacer(Modifier.width(PorchSpace.TileGap))
                Text(channel.shownTitle, style = PorchType.ScreenTitle, color = PorchColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(PorchSpace.TileGap))
            Column(verticalArrangement = Arrangement.spacedBy(PorchSpace.ListGap)) {
                rows.forEachIndexed { index, action ->
                    Pill(
                        QuickMenu.label(action, previous?.shownTitle, otherCopies, groupName = groupName, groupCount = groupCount),
                        modifier = Modifier.fillMaxWidth().then(if (index == 0) Modifier.focusRequester(first) else Modifier),
                    ) { onPick(action) }
                }
            }
        }
    }
}

/** A small star for a favourite channel, drawn next to its name or number. */
@Composable
fun FavouriteStar(modifier: Modifier = Modifier) {
    Text("★", style = PorchType.Meta, color = PorchColors.Accent, maxLines = 1, modifier = modifier)
}
