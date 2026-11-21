package tv.porch.app.domain

/** Which Settings row gets focus when the screen opens. */
object SettingsFocus {
    const val FRESH_ROW = "channels"

    /** [comingBack] is true only when Settings returns from a screen pushed on top of it. */
    fun startRow(lastRow: String?, comingBack: Boolean): String = if (comingBack) lastRow ?: FRESH_ROW else FRESH_ROW
}
