package tv.porch.app.domain

/** Backoff for a dropped stream. 401 and 403 stop the loop so a refused login is not hammered. */
object RetryPolicy {
    const val MAX_ATTEMPTS: Int = 6

    fun delayMs(attempt: Int): Long {
        val shift = (attempt - 1).coerceIn(0, 5)
        return (1_000L shl shift).coerceAtMost(30_000L)
    }

    fun isAuthFailure(httpCode: Int?): Boolean = httpCode == 401 || httpCode == 403

    /** What the player says while it waits to try again: which try is next, out of how many. */
    fun reconnectingText(attempt: Int, allowed: Int): String = "Reconnecting… try $attempt of $allowed"
}
