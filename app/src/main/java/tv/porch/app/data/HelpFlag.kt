package tv.porch.app.data

import android.content.Context

/** Remembers that the "How Porch works" guide has been shown once. */
class HelpFlag(context: Context) {
    private val prefs = context.getSharedPreferences("porch", Context.MODE_PRIVATE)

    fun seen(): Boolean = prefs.getBoolean(KEY, false)

    fun markSeen() {
        prefs.edit().putBoolean(KEY, true).apply()
    }

    private companion object {
        const val KEY = "help_seen"
    }
}
