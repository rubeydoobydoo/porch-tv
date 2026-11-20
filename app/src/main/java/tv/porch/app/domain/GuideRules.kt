package tv.porch.app.domain

/** A refreshed guide with no programmes is treated as a failed download, so the last good guide stays. */
object GuideRules {
    /** Only say the last guide was kept when there was one. */
    fun emptyMessage(hadGuide: Boolean): String =
        if (hadGuide) "The guide had no programmes. Kept the last one." else "The guide had no programmes."

    fun accepts(programmeCount: Int): Boolean = programmeCount > 0
}
