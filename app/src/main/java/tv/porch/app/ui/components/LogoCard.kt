package tv.porch.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Text
import tv.porch.app.domain.Channel
import tv.porch.app.ui.home.channelColor
import tv.porch.app.ui.theme.PorchColors
import tv.porch.app.ui.theme.PorchRadius
import tv.porch.app.ui.theme.PorchSpace
import tv.porch.app.ui.theme.PorchType

/**
 * What a programme with no picture shows, and what shows while one loads: the channel's logo, name and number on a light card.
 * A channel with no logo shows its letter badge. With no channel at all (the first load) it is a plain `Surface` block.
 * [content] draws over the card (the picture, once it has loaded).
 */
@Composable
fun LogoCard(
    channel: Channel?,
    width: Dp,
    height: Dp,
    logo: Dp = PorchSpace.PreviewLogo,
    logoOnly: Boolean = false,
    content: @Composable () -> Unit = {},
) {
    Box(
        Modifier.size(width, height).background(if (channel != null) PorchColors.LogoPlate else PorchColors.Surface, RoundedCornerShape(PorchRadius.Tile)),
        contentAlignment = Alignment.Center,
    ) {
        if (channel != null) {
            // Keyed so one channel's logo is never left showing over the next channel's name.
            key(channel.id) {
                Column(Modifier.padding(horizontal = PorchSpace.HomeTilePad), horizontalAlignment = Alignment.CenterHorizontally) {
                    ChannelLogo(channel, channelColor(channel), logo, PorchType.ScreenTitle.copy(color = Color.White), fixedWidth = true)
                    if (!logoOnly) {
                        Spacer(Modifier.height(PorchSpace.LogoCardNameGap))
                        Text(channel.shownTitle, style = PorchType.TileTitle, color = PorchColors.OnPlate, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (channel.number > 0) {
                            Spacer(Modifier.height(PorchSpace.PreviewLineGap))
                            Text("Channel ${channel.number}", style = PorchType.Meta, color = PorchColors.OnPlateMuted, maxLines = 1)
                        }
                    }
                }
            }
        }
        content()
    }
}
