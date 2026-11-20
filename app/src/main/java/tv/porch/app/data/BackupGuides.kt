package tv.porch.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import tv.porch.app.domain.BackupGuideDefaults
import tv.porch.app.domain.BackupResult

/**
 * The viewer's outside guide addresses and how the last load went. Kept on the device only.
 * An address can hold a login, so it is never logged and the screens only show its host.
 */
class BackupGuides(context: Context) {
    private val prefs = context.getSharedPreferences("porch", Context.MODE_PRIVATE)
    private val saved = MutableStateFlow(BackupGuideDefaults.addresses(prefs.getString(KEY_ADDRESSES, null)))
    private val last = MutableStateFlow(readResult())

    val addresses: StateFlow<List<String>> get() = saved
    val result: StateFlow<BackupResult?> get() = last

    /** Replaces the list. An empty list also clears the last result. The guide itself changes at the next refresh. */
    fun setAddresses(list: List<String>) {
        prefs.edit().putString(KEY_ADDRESSES, list.joinToString("\n")).apply()
        saved.value = list
        if (list.isEmpty()) {
            prefs.edit().remove(KEY_AT).remove(KEY_MATCHED).remove(KEY_LOADED).remove(KEY_FAILED).apply()
            last.value = null
        }
    }

    fun record(result: BackupResult) {
        prefs.edit()
            .putLong(KEY_AT, result.at)
            .putInt(KEY_MATCHED, result.matched)
            .putInt(KEY_LOADED, result.loaded)
            .putInt(KEY_FAILED, result.failed)
            .apply()
        last.value = result
    }

    /** True once this source may hold backup rows, so a refresh knows to clean them up after Remove all. */
    @Synchronized
    fun hasRows(sourceId: Long): Boolean = sourceId.toString() in rowSources()

    @Synchronized
    fun setHasRows(sourceId: Long, value: Boolean) {
        val id = sourceId.toString()
        val next = if (value) rowSources() + id else rowSources() - id
        prefs.edit().putStringSet(KEY_SOURCES, next).apply()
    }

    private fun rowSources(): Set<String> = prefs.getStringSet(KEY_SOURCES, null).orEmpty()

    private fun readResult(): BackupResult? {
        if (!prefs.contains(KEY_AT)) return null
        return BackupResult(
            at = prefs.getLong(KEY_AT, 0L),
            matched = prefs.getInt(KEY_MATCHED, 0),
            loaded = prefs.getInt(KEY_LOADED, 0),
            failed = prefs.getInt(KEY_FAILED, 0),
        )
    }

    private companion object {
        const val KEY_ADDRESSES = "backup_guide_addresses"
        const val KEY_SOURCES = "backup_guide_sources"
        const val KEY_AT = "backup_guide_at"
        const val KEY_MATCHED = "backup_guide_matched"
        const val KEY_LOADED = "backup_guide_loaded"
        const val KEY_FAILED = "backup_guide_failed"
    }
}
