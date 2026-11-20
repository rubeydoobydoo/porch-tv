package tv.porch.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import tv.porch.app.domain.Channel
import tv.porch.app.ui.home.channelColor
import tv.porch.app.ui.theme.PorchColors
import tv.porch.app.ui.theme.PorchRadius
import tv.porch.app.ui.theme.PorchSpace
import tv.porch.app.ui.theme.PorchType

/**
 * A side panel titled with a programme and "Watch on", listing the channels that carry it. OK on one calls [onPlay].
 * Back closes it ([onClose]); the screen below keeps the focus it had, because the panel is its own window.
 * Used by Live now, and meant for Sport too. Other copies uses it too, with its own [heading] and [rowLabel].
 */
@Composable
fun WatchOnPanel(
    title: String,
    channels: List<Channel>,
    onPlay: (Channel) -> Unit,
    onClose: () -> Unit,
    heading: String = "Watch on",
    rowLabel: (Channel) -> String = { channel -> listOfNotNull(channel.shownTitle, channel.number.takeIf { it > 0 }?.let { "Ch $it" }).joinToString("   ") },
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().backGoesBack(), contentAlignment = Alignment.CenterEnd) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .width(PorchSpace.SidePanelWidth)
                    // Solid, not frosted: one panel, but the Chromecast is short on GPU.
                    .background(PorchColors.Surface, RoundedCornerShape(topStart = PorchRadius.Panel, bottomStart = PorchRadius.Panel))
                    .padding(PorchSpace.PanelPad),
            ) {
                Text(title, style = PorchType.ScreenTitle, color = PorchColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(heading, style = PorchType.Body, color = PorchColors.Muted)
                Spacer(Modifier.height(PorchSpace.TileGap))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(PorchSpace.ListGap)) {
                    itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
                        WatchOnRow(rowLabel(channel), Modifier.then(if (index == 0) Modifier.focusRequester(first) else Modifier)) { onPlay(channel) }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchOnRow(label: String, modifier: Modifier, onClick: () -> Unit) {
    // A Pill is the button rule: card fill, white fill and dark text when focused.
    Pill(
        label = label,
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    )
}
