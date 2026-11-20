package tv.porch.app.domain

/** The words, label and steps for the Settings guide offset. */
object GuideOffsetText {
    const val STEP_MINUTES = 5
    const val LIMIT_MINUTES = 12 * 60

    fun sentence(minutes: Int): String = when {
        minutes == 0 -> "Guide times are used as published."
        minutes > 0 ->
            "Shows are listed ${plural(minutes)} later than your provider sends them. Use this if programmes keep starting after the guide says."
        else ->
            "Shows are listed ${plural(-minutes)} earlier than your provider sends them. Use this if programmes keep starting before the guide says."
    }

    /** "+5 min", "-10 min" or "None". */
    fun label(minutes: Int): String = when {
        minutes == 0 -> "None"
        minutes > 0 -> "+$minutes min"
        else -> "$minutes min"
    }

    /** One 5-minute step left (-1) or right (+1), kept inside the limit. */
    fun step(minutes: Int, direction: Int): Int =
        (minutes + direction * STEP_MINUTES).coerceIn(-LIMIT_MINUTES, LIMIT_MINUTES)

    private fun plural(minutes: Int) = if (minutes == 1) "1 minute" else "$minutes minutes"
}
