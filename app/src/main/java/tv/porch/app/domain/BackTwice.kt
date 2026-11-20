package tv.porch.app.domain

/**
 * Back on Home asks twice. The first press warns; a second within [windowMs] leaves.
 * Times are any steady clock in milliseconds.
 */
class BackTwice(private val windowMs: Long = 2_000L) {
    private var firstAt = NONE

    /** A Back press at [now]. True means leave; false means show the warning. */
    fun press(now: Long): Boolean {
        if (warning(now)) {
            firstAt = NONE
            return true
        }
        firstAt = now
        return false
    }

    /** True while the warning should still be on screen. */
    fun warning(now: Long): Boolean = firstAt != NONE && now - firstAt < windowMs

    private companion object {
        const val NONE = Long.MIN_VALUE
    }
}
