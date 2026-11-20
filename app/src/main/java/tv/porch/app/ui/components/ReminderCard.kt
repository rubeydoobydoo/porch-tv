package tv.porch.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.porch.app.domain.ProgrammeText
import tv.porch.app.domain.Channel
import tv.porch.app.domain.ChannelBadge
import tv.porch.app.domain.LiveRows
import tv.porch.app.domain.OkHoldGuard
import tv.porch.app.domain.Reminder
import tv.porch.app.domain.Reminders
import tv.porch.app.ui.theme.PorchColors
import tv.porch.app.ui.theme.PorchRadius
import tv.porch.app.ui.theme.PorchSpace
import tv.porch.app.ui.theme.PorchType

/** How long the card waits before it goes away by itself. */
private const val CARD_MS = 60_000L

/**
 * "Starts in 5 minutes" over whatever is on screen. [onWatch] and [onDismiss] both end it; the caller clears the reminder.
 * OK is held back until a fresh press, so a remote in mid-press on the screen below can't pick Watch.
 */
@Composable
fun ReminderCard(channel: Channel, reminder: Reminder, onWatch: () -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    val guard = remember { OkHoldGuard() }
    val now = rememberNow()
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    // Counts only while the app is on screen, and starts again when the viewer comes back.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(reminder, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            delay(CARD_MS)
            onDismiss()
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // A dialog's window dims everything behind it. This card sits on the picture as a solid card, so no dim.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }
        Box(Modifier.fillMaxSize().backGoesBack().padding(PorchSpace.Margin), contentAlignment = Alignment.BottomEnd) {
            Column(
                Modifier
                    .width(PorchSpace.ReminderCardWidth)
                    .background(PorchColors.Surface, RoundedCornerShape(PorchRadius.Panel))
                    .padding(PorchSpace.PanelPad)
                    .onPreviewKeyEvent { event ->
                        val ok = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                        ok && guard.swallows(event.type == KeyEventType.KeyDown, event.nativeKeyEvent.repeatCount)
                    },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PorchSpace.TileGap)) {
                    ChannelLogo(channel, Color(ChannelBadge.color(channel.name, channel.group)), PorchSpace.PanelBadge)
                    Text(ProgrammeText.title(reminder.title), style = PorchType.Section, color = PorchColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(PorchSpace.ListGap))
                Text(Reminders.cardLine(reminder, now, channel.shownTitle), style = PorchType.Meta, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(PorchSpace.TileGap))
                Row(horizontalArrangement = Arrangement.spacedBy(PorchSpace.TileGap)) {
                    Pill("Watch", modifier = Modifier.focusRequester(first), onClick = onWatch)
                    Pill("Not now", onClick = onDismiss)
                }
            }
        }
    }
}
