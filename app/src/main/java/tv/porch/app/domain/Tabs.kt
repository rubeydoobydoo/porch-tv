package tv.porch.app.domain

/** Top-bar tabs swap the screen: the stack is Home alone, or Home under the chosen tab, so Back from a tab goes Home. */
object Tabs {
    /** The Studio top-bar tabs, in order. */
    val labels = listOf("Home", "Full guide", "What's on now", "Sport", "24/7", "Movies", "Series", "Search", "Settings")

    /**
     * The tab a Left ([direction] -1) or Right (+1) on [focused] jumps to by wrapping round the ends of [tabs], or null
     * when focus should move as normal. Only a fresh press wraps: a held key stops at the end instead of spinning round.
     */
    fun <T> wrap(tabs: List<T>, focused: T, direction: Int, repeatCount: Int): T? = when {
        repeatCount > 0 || tabs.size < 2 -> null
        direction < 0 && focused == tabs.first() -> tabs.last()
        direction > 0 && focused == tabs.last() -> tabs.first()
        else -> null
    }

    fun <T> stackFor(home: T, tab: T): List<T> = if (tab == home) listOf(home) else listOf(home, tab)

    /** One step back. Never empties the stack: Back can be handled twice before the screen recomposes, and an empty stack has no screen to draw. */
    fun <T> back(stack: List<T>, home: T): List<T> = stack.dropLast(1).ifEmpty { listOf(home) }
}
