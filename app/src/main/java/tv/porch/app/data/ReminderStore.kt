package tv.porch.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import tv.porch.app.domain.Reminder
import tv.porch.app.domain.Reminders

/** The programmes the viewer asked to be reminded about, kept as JSON in the `porch` preferences. */
class ReminderStore(context: Context) {
    private val prefs = context.getSharedPreferences("porch", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Reminder.serializer())
    private val items = MutableStateFlow(read())

    val reminders: StateFlow<List<Reminder>> get() = items

    fun has(sourceId: Long, identity: String, startMs: Long): Boolean = Reminders.has(items.value, sourceId, identity, startMs)

    /** On becomes off and off becomes on. */
    fun toggle(reminder: Reminder) {
        write(if (has(reminder.sourceId, reminder.identity, reminder.startMs)) Reminders.remove(items.value, reminder) else Reminders.add(items.value, reminder))
    }

    fun remove(reminder: Reminder) = write(Reminders.remove(items.value, reminder))

    fun prune(nowMs: Long) {
        val kept = Reminders.prune(items.value, nowMs)
        if (kept.size != items.value.size) write(kept)
    }

    private fun write(list: List<Reminder>) {
        prefs.edit().putString(KEY, json.encodeToString(serializer, list)).apply()
        items.value = list
    }

    // A damaged value is no reminders, never a crash on start.
    private fun read(): List<Reminder> =
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    private companion object {
        const val KEY = "reminders"
    }
}
