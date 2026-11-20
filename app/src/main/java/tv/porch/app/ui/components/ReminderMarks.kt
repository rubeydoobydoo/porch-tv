package tv.porch.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import tv.porch.app.AppGraph
import tv.porch.app.R
import tv.porch.app.domain.Channel
import tv.porch.app.domain.Reminder
import tv.porch.app.domain.Reminders
import tv.porch.app.ui.theme.PorchSpace

/**
 * Which channels, at which start times, have a reminder set. Reminders name a channel by source and identity, which the
 * screens' channels don't carry, so [identities] maps the channels' ids to theirs.
 */
@Stable
class ReminderMarks(private val reminders: List<Reminder>, private val identities: Map<Long, String>) {
    fun has(channel: Channel, startMs: Long): Boolean {
        val identity = identities[channel.id] ?: return false
        return Reminders.has(reminders, channel.sourceId, identity, startMs)
    }

    fun has(channels: List<Channel>, startMs: Long): Boolean = channels.any { has(it, startMs) }

    companion object {
        val None = ReminderMarks(emptyList(), emptyMap())
    }
}

/** The marks for the reminders now set. They update when the reminder store changes. */
@Composable
fun rememberReminderMarks(graph: AppGraph): ReminderMarks {
    val reminders by graph.reminders.reminders.collectAsState()
    var identities by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    // A refresh gives channels new ids, so the ids are found again whenever the channel list changes too.
    val listing by graph.folds.listing.collectAsState(null)
    LaunchedEffect(reminders, listing) {
        identities = reminders.mapNotNull { r ->
            graph.repository.channelByIdentity(r.sourceId, r.identity)?.let { it.id to r.identity }
        }.toMap()
    }
    return remember(reminders, identities) { ReminderMarks(reminders, identities) }
}

/** The small bell: [PorchSpace.ReminderMark] tall, in the colour it is given (Accent at rest, OnFocus when focused). */
@Composable
fun ReminderBell(color: Color, modifier: Modifier = Modifier) {
    Image(
        painterResource(R.drawable.ic_bell),
        contentDescription = "Reminder set",
        modifier = modifier.size(PorchSpace.ReminderMark),
        colorFilter = ColorFilter.tint(color),
    )
}
