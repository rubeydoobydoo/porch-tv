package tv.porch.app.domain

/** The guide hero's title and status when the focused channel has no programme to show. */
object GuideHeroText {
    /** A programme title wins. Without one, a matched channel says nothing is scheduled, but only once programmes have loaded. */
    fun title(programmeTitle: String?, matched: Boolean, programmesLoaded: Boolean, channelListTitle: String): String = when {
        programmeTitle != null -> programmeTitle
        !matched || !programmesLoaded -> channelListTitle
        else -> "Nothing scheduled here"
    }

    /** The status line for a channel with no programme. Blank while programmes load, so nothing wrong is shown. */
    fun status(matched: Boolean, programmesLoaded: Boolean): String = when {
        !matched -> AllDayText.HERO_STATUS
        !programmesLoaded -> ""
        else -> "OK plays it live"
    }
}
