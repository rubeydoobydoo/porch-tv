package tv.porch.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Remembers whether channels with no guide data are shown (off until turned on), and whether copies of a channel are combined (on until turned off). */
class GuideFlags(context: Context) {
    private val prefs = context.getSharedPreferences("porch", Context.MODE_PRIVATE)
    private val flag = MutableStateFlow(prefs.getBoolean(KEY, false))

    private val combine = MutableStateFlow(prefs.getBoolean(COMBINE_KEY, true))

    private val refresh = MutableStateFlow(prefs.getBoolean(REFRESH_KEY, true))

    private val linen = MutableStateFlow(prefs.getBoolean(LINEN_KEY, false))

    val showWithoutGuide: StateFlow<Boolean> get() = flag

    /** The Linen look instead of Dark (off until turned on). */
    val linenLook: StateFlow<Boolean> get() = linen

    val combineCopies: StateFlow<Boolean> get() = combine

    /** Whether the TV's refresh rate follows the channel (on until turned off). */
    val matchRefresh: StateFlow<Boolean> get() = refresh

    fun showWithoutGuide(): Boolean = flag.value

    fun setShowWithoutGuide(value: Boolean) {
        prefs.edit().putBoolean(KEY, value).apply()
        flag.value = value
    }

    fun setCombineCopies(value: Boolean) {
        prefs.edit().putBoolean(COMBINE_KEY, value).apply()
        combine.value = value
    }

    fun setLinenLook(value: Boolean) {
        prefs.edit().putBoolean(LINEN_KEY, value).apply()
        linen.value = value
    }

    fun setMatchRefresh(value: Boolean) {
        prefs.edit().putBoolean(REFRESH_KEY, value).apply()
        refresh.value = value
    }

    private companion object {
        const val KEY = "show_without_guide"
        const val COMBINE_KEY = "combine_copies"
        const val REFRESH_KEY = "match_refresh"
        const val LINEN_KEY = "linen_look"
    }
}
