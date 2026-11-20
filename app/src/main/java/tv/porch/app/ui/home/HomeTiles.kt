package tv.porch.app.ui.home

import androidx.compose.ui.graphics.Color
import tv.porch.app.domain.Channel
import tv.porch.app.domain.ChannelBadge

/** The colour of a channel's badge, from its name and group. */
fun channelColor(channel: Channel): Color = Color(ChannelBadge.color(channel.name, channel.group))
