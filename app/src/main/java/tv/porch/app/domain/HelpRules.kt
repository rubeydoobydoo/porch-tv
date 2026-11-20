package tv.porch.app.domain

/** The guide shows itself once, the first time the library has channels. After that it lives in Settings. */
fun shouldShowHelp(seen: Boolean, channelCount: Int): Boolean = !seen && channelCount > 0
