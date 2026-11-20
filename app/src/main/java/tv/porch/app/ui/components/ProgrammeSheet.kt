package tv.porch.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import tv.porch.app.AppGraph
import tv.porch.app.domain.ProgrammeText
import tv.porch.app.domain.Channel
import tv.porch.app.domain.FollowTerms
import tv.porch.app.domain.GuideClock
import tv.porch.app.domain.LiveRows
import tv.porch.app.domain.Programme
import tv.porch.app.domain.Reminder
import tv.porch.app.domain.Reminders
import tv.porch.app.domain.ProgrammeCard
import tv.porch.app.domain.catchupAllowed
import tv.porch.app.domain.formatTime
import tv.porch.app.player.CatchupRequest
import tv.porch.app.ui.home.channelColor
import tv.porch.app.ui.home.loadPicture
import tv.porch.app.ui.theme.PorchColors
import tv.porch.app.ui.theme.PorchRadius
import tv.porch.app.ui.theme.PorchSpace
import tv.porch.app.ui.theme.PorchType

/** What the sheet fetches once it is open: the source's guide offset, a copy that can play catch-up, what's next, and the picture's address. */
private class SheetData(val offset: Int, val recorded: Channel?, val next: List<Programme>, val pictureUrl: String?, val description: String?, val identity: String?)

/** Waiting for the shared channel list should never hold the sheet up. */
private const val COPIES_WAIT_MS = 1_500L

/** The channel name as it reads inside a pill, which does not wrap. */
private fun String.forPill(): String = if (length > 24) take(23).trimEnd() + "…" else this

/**
 * The programme details sheet: a full-screen window over the screen that opened it. Back closes it ([onClose]) and the screen
 * below keeps its focus. A watch pill closes it first, then calls [onWatch] with the catch-up to play (null for live) and the
 * copy of the channel that keeps the recording (null when it is this channel).
 */
@Composable
internal fun ProgrammeSheet(
    graph: AppGraph,
    channel: Channel,
    programme: Programme,
    onWatch: (CatchupRequest?, Channel?) -> Unit,
    onClose: () -> Unit,
) {
    val data by produceState<SheetData?>(null, channel.id, programme.id) {
        val now = System.currentTimeMillis()
        val offset = graph.repository.source(channel.sourceId)?.guideOffsetMinutes ?: 0
        val copies = withTimeoutOrNull(COPIES_WAIT_MS) { graph.folds.listing.first().families.othersOf(channel.id) }.orEmpty()
        val start = GuideClock.shift(programme.startUtc, offset)
        val recorded = (listOf(channel) + copies).firstOrNull { it.xtreamStreamId != null && catchupAllowed(it.archiveDays, start, now) }
        val next = graph.repository.upcoming(channel.id, maxOf(now, GuideClock.shift(programme.stopUtc, offset)), 4).filter { it.id != programme.id }.take(3)
        // A failed lookup leaves the logo block and the description the list already had.
        val detail = runCatching { graph.repository.programmeDetail(programme.id) }.getOrNull()
        val identity = runCatching { graph.repository.channelIdentity(channel.id) }.getOrNull()
        value = SheetData(offset, recorded, next, detail?.iconUrl, detail?.description ?: programme.description, identity)
    }
    val ready = data
    val first = remember { FocusRequester() }
    // When the buttons change (Remind me drops out at the five-minute mark), focus goes back to the first one, and an OK
    // pressed just before the change is ignored so it can't land on a different button.
    var shownKinds by remember { mutableStateOf<List<ProgrammeCard.Kind>?>(null) }
    var changedAt by remember { mutableLongStateOf(0L) }
    val context = LocalContext.current
    val density = LocalDensity.current
    val width = with(density) { PorchSpace.DetailPictureWidth.roundToPx() }
    val height = with(density) { PorchSpace.DetailPictureHeight.roundToPx() }
    var picture by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(ready?.pictureUrl) {
        picture = ready?.pictureUrl?.let { loadPicture(context, it, width, height) }
    }
    val now = rememberNow()
    val reminders by graph.reminders.reminders.collectAsState()
    val follows by graph.follows.follows.collectAsState()
    // "Follow what?" is open: Back closes it before it closes the sheet.
    var choosing by remember(programme.id) { mutableStateOf(false) }
    // The Back that closed the choices must not also close the sheet when it comes back up.
    var swallowBackUp by remember { mutableStateOf(false) }
    var choosingAt by remember { mutableLongStateOf(0L) }
    val followFocus = remember { FocusRequester() }
    val choiceFocus = remember { FocusRequester() }
    var wasChoosing by remember { mutableStateOf(false) }
    LaunchedEffect(choosing) {
        if (choosing) {
            withFrameNanos { }
            runCatching { choiceFocus.requestFocus() }
        } else if (wasChoosing) {
            withFrameNanos { }
            runCatching { followFocus.requestFocus() }
        }
        wasChoosing = choosing
    }
    val choices = remember(programme.title) { FollowTerms.choices(programme.title) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Back && event.type == KeyEventType.KeyUp && swallowBackUp) {
                        swallowBackUp = false
                        true
                    } else if (choosing && event.key == Key.Back) {
                        if (event.type == KeyEventType.KeyDown) {
                            choosing = false
                            swallowBackUp = true
                        }
                        true
                    } else {
                        false
                    }
                }
                .backGoesBack().background(PorchColors.Bg).padding(PorchSpace.Margin),
        ) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(PorchSpace.Margin),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DetailPicture(channel, picture)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PorchSpace.ListGap)) {
                        key(channel.id) {
                            ChannelLogo(channel, channelColor(channel), PorchSpace.PreviewSmallLogo, PorchType.Meta.copy(color = Color.White), fixedWidth = true)
                        }
                        Text(channel.shownTitle, style = PorchType.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(PorchSpace.HeroTitleAir))
                    Text(ProgrammeText.title(programme.title), style = PorchType.ScreenTitle, color = PorchColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (ready != null) {
                        Spacer(Modifier.height(PorchSpace.HeroTitleAir))
                        val line = ProgrammeCard.whenLine(programme.startUtc, programme.stopUtc, now, ready.offset)
                        Text(
                            buildAnnotatedString {
                                if (line.live) {
                                    withStyle(SpanStyle(color = PorchColors.LiveOnDark, fontWeight = FontWeight.Bold)) { append("Live") }
                                    append(line.text.removePrefix("Live"))
                                } else {
                                    append(line.text)
                                }
                            },
                            style = PorchType.Meta,
                            maxLines = 1,
                        )
                    }
                    val description = ProgrammeText.synopsis(ready?.description ?: programme.description)
                    if (!description.isNullOrBlank()) {
                        Spacer(Modifier.height(PorchSpace.TileGap))
                        Text(description, style = PorchType.Body, color = PorchColors.TextSecondary, maxLines = 7, overflow = TextOverflow.Ellipsis)
                    }
                    if (ready != null) {
                        Spacer(Modifier.height(PorchSpace.TileGap))
                        val catchup = CatchupRequest(programme.startUtc, programme.stopUtc, programme.title)
                        val start = GuideClock.shift(programme.startUtc, ready.offset)
                        val identity = ready.identity
                        val reminder = identity?.let { Reminder(channel.sourceId, it, programme.title, start, GuideClock.shift(programme.stopUtc, ready.offset)) }
                        val reminderOn = identity?.let { Reminders.has(reminders, channel.sourceId, it, start) }
                        val followPill = ProgrammeCard.FollowPill(choices, FollowTerms.firstMatch(follows, programme.title))
                        val actions = ProgrammeCard.actions(programme.startUtc, programme.stopUtc, now, ready.offset, ready.recorded != null, channel.shownTitle.forPill(), reminderOn, followPill)
                        val kinds = actions.map { it.kind }
                        LaunchedEffect(kinds) {
                            val before = shownKinds
                            shownKinds = kinds
                            if (before != null && before != kinds) changedAt = SystemClock.uptimeMillis()
                            withFrameNanos { }
                            runCatching { first.requestFocus() }
                        }
                        Row(
                            Modifier.onPreviewKeyEvent { event ->
                                val ok = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                                ok && ProgrammeCard.settling(changedAt, SystemClock.uptimeMillis())
                            },
                            horizontalArrangement = Arrangement.spacedBy(PorchSpace.TileGap),
                        ) {
                            actions.forEachIndexed { index, action ->
                              key(action.kind) {
                                Pill(action.label, selected = action.chosen, modifier = if (index == 0) Modifier.focusRequester(first) else if (action.kind == ProgrammeCard.Kind.Follow) Modifier.focusRequester(followFocus) else Modifier) {
                                    // The reminder pill keeps the sheet open, and focus on itself.
                                    if (action.kind == ProgrammeCard.Kind.Remind) {
                                        reminder?.let { graph.reminders.toggle(it) }
                                        return@Pill
                                    }
                                    // So does the follow pill: it unfollows at once, saves a lone title, or opens the choices.
                                    if (action.kind == ProgrammeCard.Kind.Follow) {
                                        val current = followPill.following
                                        when {
                                            current != null -> graph.follows.remove(current)
                                            choices.size == 1 -> graph.follows.add(choices.first())
                                            else -> {
                                                choosingAt = SystemClock.uptimeMillis()
                                                choosing = true
                                            }
                                        }
                                        return@Pill
                                    }
                                    onClose()
                                    when (action.kind) {
                                        ProgrammeCard.Kind.Remind, ProgrammeCard.Kind.Follow -> Unit
                                        ProgrammeCard.Kind.WatchLive -> onWatch(null, null)
                                        ProgrammeCard.Kind.WatchCatchup -> onWatch(catchup, ready.recorded?.takeIf { it.id != channel.id })
                                    }
                                }
                              }
                            }
                        }
                        if (choosing && choices.size > 1) {
                            Spacer(Modifier.height(PorchSpace.TileGap))
                            Text("Follow what?", style = PorchType.Section, color = PorchColors.Text)
                            Spacer(Modifier.height(PorchSpace.ListGap))
                            Row(horizontalArrangement = Arrangement.spacedBy(PorchSpace.TileGap)) {
                                choices.forEachIndexed { index, term ->
                                    Pill(
                                        if (index == choices.lastIndex) "Whole title" else term.forPill(),
                                        selected = follows.any { it.equals(term, ignoreCase = true) },
                                        modifier = if (index == 0) Modifier.focusRequester(choiceFocus) else Modifier,
                                    ) {
                                        // The OK that opened the choices is still coming up as a key-up.
                                        if (ProgrammeCard.settling(choosingAt, SystemClock.uptimeMillis())) return@Pill
                                        graph.follows.add(term)
                                        choosing = false
                                    }
                                }
                            }
                        }
                        if (ready.next.isNotEmpty()) {
                            Spacer(Modifier.height(PorchSpace.Margin))
                            NextOn(channel, ready.next, ready.offset)
                        }
                    }
                }
            }
        }
    }
}

/** The picture, over the logo card. Slow, missing and failed all look the same. */
@Composable
private fun DetailPicture(channel: Channel, image: ImageBitmap?) {
    LogoCard(channel, PorchSpace.DetailPictureWidth, PorchSpace.DetailPictureHeight) {
        if (image != null) {
            Image(image, contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(PorchRadius.Tile)), contentScale = ContentScale.Crop)
        }
    }
}

/** Decoration only: not focusable. */
@Composable
private fun NextOn(channel: Channel, next: List<Programme>, offset: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(PorchSpace.ListGap)) {
        Text("Next on ${channel.shownTitle}", style = PorchType.Section, color = PorchColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        next.forEach { programme ->
            Text(
                "${formatTime(GuideClock.shift(programme.startUtc, offset))}  ${ProgrammeText.title(programme.title)}",
                style = PorchType.Meta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
