package tv.porch.app.domain

/** The lines on the How Porch works page. Each fits one line in a help column. */
object HelpText {
    const val MAX_LINE = 44

    val columns: List<Pair<String, List<String>>> = listOf(
        "The remote" to listOf(
            "Arrows move. OK picks or plays.",
            "Back goes one step back.",
            "Hold OK on a channel for its quick menu.",
            "Hold Mic to search by voice.",
        ),
        "Home" to listOf(
            "Four lists: sport, favourites, recent, soon.",
            "OK on a row plays it straight away.",
            "Hold OK on a row for the quick menu.",
            "See all opens the full list.",
        ),
        "The player" to listOf(
            "Up and Down change channel.",
            "OK pauses. Hold OK for sound and favourite.",
            "Left rewinds, where the channel allows it.",
            "Right shows what else is on.",
        ),
    )
}
