package tv.porch.app.domain

/** Which Back key events count as a press. */
object BackKey {
    /** The first key-down only, so holding Back steps back once instead of walking out of the app. */
    fun fires(keyDown: Boolean, repeatCount: Int): Boolean = keyDown && repeatCount == 0
}
