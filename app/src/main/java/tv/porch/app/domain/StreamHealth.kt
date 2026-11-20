package tv.porch.app.domain

/**
 * What a player event means once a stream has started. Pure, so it is tested without ExoPlayer.
 *
 * A server can drop a viewer three ways: with an error, by closing the connection cleanly (the
 * player then thinks the programme ended), or by going quiet and leaving the player buffering.
 * All three are a drop on a live stream. On catch-up only an error counts: reaching the end is
 * normal, and a slow catch-up server should not send the viewer back to the programme start.
 */
object StreamHealth {
    /** Buffering this long with no new picture counts as a stall. */
    const val STALL_LIMIT_MS: Long = 15_000L

    enum class State { IDLE, BUFFERING, READY, ENDED }

    enum class Drop { ERROR, ENDED, STALLED }

    fun drop(state: State, hasError: Boolean, live: Boolean, bufferingForMs: Long): Drop? = when {
        hasError -> Drop.ERROR
        state == State.ENDED && live -> Drop.ENDED
        state == State.BUFFERING && live && bufferingForMs >= STALL_LIMIT_MS -> Drop.STALLED
        else -> null
    }
}
