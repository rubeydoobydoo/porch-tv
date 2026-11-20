package tv.porch.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.porch.app.domain.ProgrammeText
import tv.porch.app.domain.LiveRows
import tv.porch.app.ui.home.channelColor
import tv.porch.app.ui.theme.PorchColors
import tv.porch.app.ui.theme.PorchFocus
import tv.porch.app.ui.theme.PorchRadius
import tv.porch.app.ui.theme.PorchSpace
import tv.porch.app.ui.theme.PorchType

private val RowHeight = 42.dp
private val RowShape = RoundedCornerShape(PorchRadius.Row)

/**
 * One programme: badge, title, channel name (and how many more), then the end or start time on the right.
 * Shared by Live now and Home.
 *
 * [compact] is the Home look, for a half-width column: a shorter row, and the channel name sits after the title only
 * if it fits whole, so a long title is never cut to make room for it. The time slot then wraps its content.
 */
@Composable
fun LiveRowItem(
    row: LiveRows.Row,
    trailing: String?,
    liveLabel: Boolean,
    wideSlot: Boolean,
    modifier: Modifier,
    onFocused: (Boolean) -> Unit,
    /** Right on a folded row calls this. Null leaves Right alone, so it moves on as usual. */
    onOpenOthers: (() -> Unit)?,
    onPlay: () -> Unit,
    onLong: () -> Unit,
    compact: Boolean = false,
    /** A reminder is set for this programme: the bell shows ahead of the time. */
    reminder: Boolean = false,
) {
    val channel = row.channels.first()
    val shownTitle = ProgrammeText.title(row.title)
    val others = row.channels.size - 1
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onPlay,
        onLongClick = onLong,
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) PorchSpace.HomeRowHeight else RowHeight)
            .landsOnReturn()
            .onFocusChanged {
                focused = it.hasFocus
                onFocused(it.hasFocus)
            }
            .onPreviewKeyEvent { event ->
                if (onOpenOthers != null && others > 0 && event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                    onOpenOthers()
                    true
                } else {
                    false
                }
            },
        shape = ClickableSurfaceDefaults.shape(RowShape),
        // No fill at rest; the Focus fill and dark text when focused. Solid colours only.
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = PorchColors.Text,
            focusedContainerColor = PorchColors.Focus,
            focusedContentColor = PorchColors.OnFocus,
            pressedContainerColor = PorchColors.Focus,
            pressedContentColor = PorchColors.OnFocus,
        ),
        border = ClickableSurfaceDefaults.border(),
        scale = clickScale(PorchFocus.RowScale),
        glow = ClickableSurfaceDefaults.glow(),
    ) {
        val quiet = if (focused) PorchColors.OnFocus else PorchColors.Muted
        Row(
            Modifier.fillMaxSize().padding(horizontal = if (compact) PorchSpace.TileGap else PorchSpace.PanelPad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A fixed slot, so titles line up whatever the badge's width.
            Box(Modifier.width(36.dp), contentAlignment = Alignment.CenterStart) {
                ChannelLogo(channel, channelColor(channel), 26.dp, PorchType.Meta.copy(color = Color.White), fixedWidth = true)
            }
            Spacer(Modifier.width(PorchSpace.TileGap))
            val channelLine: @Composable (Modifier) -> Unit = { lineModifier ->
                Row(lineModifier, verticalAlignment = Alignment.CenterVertically) {
                    if (channel.favourite) {
                        FavouriteStar()
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        channel.shownTitle + if (others > 0) "  +$others channel${if (others == 1) "" else "s"}" else "",
                        style = PorchType.Meta,
                        color = quiet,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (compact) {
                // A row titled with the channel's own name already says which channel it is.
                val repeats = row.channels.size == 1 && row.title.equals(channel.shownTitle, ignoreCase = true)
                TitleThenChannel(
                    modifier = Modifier.weight(1f),
                    title = { Text(shownTitle, style = PorchType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    channel = if (repeats) null else ({ channelLine(Modifier) }),
                )
            } else {
                Text(shownTitle, Modifier.weight(0.55f), style = PorchType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(PorchSpace.TileGap))
                channelLine(Modifier.weight(0.45f))
            }
            val slot = if (compact) Modifier.padding(start = PorchSpace.TileGap) else Modifier.width(if (wideSlot) 150.dp else 92.dp)
            Row(slot, horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                // A match that is on now says so, in the live colour, ahead of its end time.
                if (liveLabel) {
                    Text("LIVE", style = PorchType.LiveLabel, color = if (focused) PorchColors.LiveOnLight else PorchColors.LiveOnDark, maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                }
                if (reminder) {
                    ReminderBell(if (focused) PorchColors.OnFocus else PorchColors.Accent)
                    Spacer(Modifier.width(PorchSpace.ListGap))
                }
                // In a narrow column LIVE says enough, and the title gets the room.
                if (trailing != null && !(compact && liveLabel)) Text(trailing, style = PorchType.Meta, color = quiet, maxLines = 1)
            }
        }
    }
}

/** The title takes the width it needs; the channel follows only if all of it fits in what is left. */
@Composable
private fun TitleThenChannel(modifier: Modifier, title: @Composable () -> Unit, channel: (@Composable () -> Unit)?) {
    Layout(
        content = {
            Box { title() }
            if (channel != null) Box { channel() }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val max = constraints.maxWidth
        val gap = PorchSpace.TileGap.roundToPx()
        val titlePlaced = measurables[0].measure(Constraints(maxWidth = max))
        val channelPlaced = measurables.getOrNull(1)?.let { m ->
            val natural = m.maxIntrinsicWidth(Constraints.Infinity)
            if (titlePlaced.width + gap + natural <= max) m.measure(Constraints(maxWidth = natural)) else null
        }
        val height = maxOf(titlePlaced.height, channelPlaced?.height ?: 0)
        layout(max, height) {
            titlePlaced.placeRelative(0, (height - titlePlaced.height) / 2)
            channelPlaced?.placeRelative(titlePlaced.width + gap, (height - channelPlaced.height) / 2)
        }
    }
}
