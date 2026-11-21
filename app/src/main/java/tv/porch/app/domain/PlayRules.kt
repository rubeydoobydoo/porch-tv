package tv.porch.app.domain

/** When playing a channel is saved as "last played". Each save reloads the whole channel list, so save rarely. */
object PlayRules {
    /** A channel counts as played once it has been on screen this long; zapping past it doesn't count. */
    const val MARK_AFTER_MS = 10_000L

    /** False for the channel that is already the most recent one: a resume or a reconnect changes nothing. */
    fun needsMark(channelId: Long, mostRecentId: Long?): Boolean = channelId != mostRecentId

    /** Where to start after the viewer left the app and came back. Live rejoins what is on now; catch-up carries on. */
    fun resumeAtMs(catchup: Boolean, positionMs: Long): Long = if (catchup) positionMs else 0L

    /** What the player says while a channel starts. It holds the overlay up, so the name stays on screen until the picture arrives. */
    fun startWords(catchup: Boolean, tryingNote: String?): String =
        if (catchup) "Starting catch-up…" else tryingNote ?: "Starting…"

    /** What Up or Down says when a zap list has no more channels that way. Zap does not wrap. */
    const val END_OF_LIST = "End of this list"

    /**
     * Whether Up or Down at the end of the list may say [END_OF_LIST]. Yes when nothing is on the banner or the banner is
     * only the "Starting" words of a channel still buffering. Never over a drop or any other message.
     */
    fun canSayEndOfList(banner: String?, tryingNote: String?, stopped: Boolean): Boolean =
        !stopped && (banner == null || isStartMessage(banner, tryingNote))

    /** The banner while Porch tries another stream format for the channel that is starting. */
    const val TRYING_FORMAT = "Trying another stream format…"

    private fun isStartMessage(banner: String, tryingNote: String?): Boolean =
        banner == startWords(false, tryingNote) || banner == startWords(true, tryingNote) || banner == TRYING_FORMAT

    /** What the banner goes back to when [END_OF_LIST] fades: the start words if the channel was still starting, else nothing. */
    fun afterEndOfList(before: String?, tryingNote: String?): String? =
        before?.takeIf { isStartMessage(it, tryingNote) }

    /** The list position a zap lands on, or null at either end of the list. */
    fun zapTarget(pending: Int, delta: Int, size: Int): Int? = (pending + delta).takeIf { size > 0 && it in 0 until size }

    /** Programmes only count for the channel they were loaded for, so a tune never shows the last channel's programme under the new name. */
    fun <T> programmesFor(loadedFor: Long?, channelId: Long, items: List<T>): List<T> =
        if (loadedFor == channelId) items else emptyList()
}
