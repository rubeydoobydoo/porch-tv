package tv.porch.app.domain

/**
 * The channel the player was on before the current one, for this run of the app. Playing the previous one makes
 * the current one the previous one, so choosing "Back to" again flips back.
 */
class LastChannel {
    private var current: Channel? = null
    private var before: Channel? = null

    /** The player is now on [channel]. Saying so again for the same channel changes nothing. */
    fun played(channel: Channel) {
        val now = current
        if (now?.id == channel.id) return
        before = now
        current = channel
    }

    fun previous(): Channel? = before

    /** The channel the player is on, or was last on. */
    fun current(): Channel? = current
}
