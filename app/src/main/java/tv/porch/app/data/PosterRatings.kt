package tv.porch.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import tv.porch.app.domain.Rpdb
import java.util.concurrent.ConcurrentHashMap

/**
 * The viewer's own RatingPosterDB key, and how it is doing this session. Kept on the TV only.
 * The key is a secret: it is never logged, never shown, and never leaves the device except inside the poster address.
 * Nothing here returns the key to a screen; the screens only see whether one is saved.
 */
class PosterRatings(context: Context) {
    private val prefs = context.getSharedPreferences("porch", Context.MODE_PRIVATE)

    @Volatile
    private var key: String? = Rpdb.cleanKey(prefs.getString(KEY_RPDB, null))
    private val saved = MutableStateFlow(key != null)
    private val line = MutableStateFlow<String?>(null)

    // Session only: shows RPDB had nothing for, and how many 403s have come in a row.
    private val missed = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var forbidden = 0
    @Volatile private var rejected = false
    @Volatile private var found = 0

    /** True while a key is saved. */
    val on: StateFlow<Boolean> get() = saved

    /** The last result, in words, for the Poster ratings screen. Null until something has happened. */
    val result: StateFlow<String?> get() = line

    /** Saves [raw] and returns true, or removes the key when it is blank. False when it isn't a usable key. */
    fun set(raw: String?): Boolean {
        if (raw.isNullOrBlank()) {
            remove()
            return true
        }
        val clean = Rpdb.cleanKey(raw) ?: return false
        prefs.edit().putString(KEY_RPDB, clean).apply()
        key = clean
        reset()
        saved.value = true
        return true
    }

    fun remove() {
        prefs.edit().remove(KEY_RPDB).apply()
        key = null
        reset()
        saved.value = false
    }

    /** The rating poster address for this IMDb id, or null when there is no key, it was refused, or RPDB had nothing. */
    fun addressFor(imdbId: String?): String? {
        if (rejected || imdbId == null || imdbId in missed) return null
        return Rpdb.posterUrl(key, imdbId)
    }

    /** True while asking RPDB makes sense (a key is saved and it hasn't been refused this session). */
    fun active(): Boolean = key != null && !rejected

    fun gotPoster() {
        forbidden = 0
        found++
        line.value = if (found == 1) "Working. 1 poster found." else "Working. $found posters found."
    }

    /** RPDB answered but had no usable picture (a 404, or a blank one): don't ask for this show again this session. */
    fun noPoster(imdbId: String) {
        forbidden = 0
        missed.add(imdbId)
    }

    /** The call failed some other way (no network, a timeout): don't ask for this show again this session. */
    fun failed(imdbId: String) {
        missed.add(imdbId)
    }

    /** A 403. Three in a row means the key is wrong, so the session stops asking. */
    fun refused(imdbId: String) {
        missed.add(imdbId)
        if (++forbidden >= REFUSALS_IN_A_ROW && key != null) {
            rejected = true
            line.value = "Key not accepted."
        }
    }

    private fun reset() {
        missed.clear()
        forbidden = 0
        rejected = false
        found = 0
        line.value = null
    }

    private companion object {
        const val KEY_RPDB = "rpdb_key"
        const val REFUSALS_IN_A_ROW = 3
    }
}
