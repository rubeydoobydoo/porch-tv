package tv.porch.app.domain

/** What a channel with no guide match shows: one bar all day, so a 24/7 loop still has a row. */
object AllDayText {
    const val HERO_STATUS = "On all day · OK plays"

    fun bar(title: String): String = "$title · on all day"
}
