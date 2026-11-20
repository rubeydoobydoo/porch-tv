package tv.porch.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** The names and titles the viewer follows, kept as JSON in the `porch` preferences. */
class FollowStore(context: Context) {
    private val prefs = context.getSharedPreferences("porch", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(String.serializer())
    private val items = MutableStateFlow(read())

    val follows: StateFlow<List<String>> get() = items

    fun add(term: String) {
        val clean = term.trim()
        if (clean.isEmpty() || items.value.any { it.equals(clean, ignoreCase = true) }) return
        write(items.value + clean)
    }

    fun remove(term: String) = write(items.value.filterNot { it.equals(term, ignoreCase = true) })

    private fun write(list: List<String>) {
        prefs.edit().putString(KEY, json.encodeToString(serializer, list)).apply()
        items.value = list
    }

    // A damaged value is no follows, never a crash on start.
    private fun read(): List<String> =
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    private companion object {
        const val KEY = "follows"
    }
}
