package tv.porch.app.domain

/** Is the guide stale? A guide that is failing leaves most matched channels with nothing on now. */
object GuideHealth {
    /** Too few matched channels say nothing either way. */
    const val MIN_MATCHED = 20

    /** Out of date when at least [MIN_MATCHED] channels are matched to the guide and under a quarter of them have something on now. */
    fun outOfDate(matched: Int, onNow: Int): Boolean = matched >= MIN_MATCHED && onNow * 4 < matched
}
