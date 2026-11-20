package tv.porch.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Simple mode, its 4-digit code and whether the welcome card still has to show. SharedPreferences, like [HelpFlag]:
 * a setting for this TV, not library data. The code is a lock against stray presses, not a secret, so it is kept as typed.
 */
class SimpleModeStore(context: Context) {
    private val prefs = context.getSharedPreferences("porch", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(prefs.getBoolean(ON_KEY, false))

    val on: StateFlow<Boolean> get() = state

    fun code(): String? = prefs.getString(CODE_KEY, null)

    /** True from turning simple mode on until the welcome card is dismissed. */
    fun welcomePending(): Boolean = prefs.getBoolean(WELCOME_KEY, false)

    fun turnOn(code: String) {
        prefs.edit().putBoolean(ON_KEY, true).putString(CODE_KEY, code).putBoolean(WELCOME_KEY, true).apply()
        state.value = true
    }

    fun turnOff() {
        prefs.edit().putBoolean(ON_KEY, false).remove(CODE_KEY).putBoolean(WELCOME_KEY, false).apply()
        state.value = false
    }

    fun welcomeSeen() {
        prefs.edit().putBoolean(WELCOME_KEY, false).apply()
    }

    private companion object {
        const val ON_KEY = "simple_mode"
        const val CODE_KEY = "simple_code"
        const val WELCOME_KEY = "simple_welcome"
    }
}
