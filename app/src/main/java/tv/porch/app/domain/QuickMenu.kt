package tv.porch.app.domain

/** One row of the quick menu that opens when OK is held on a channel. */
enum class QuickAction { LastChannel, Pause, Play, Tracks, About, AddFavourite, RemoveFavourite, OtherCopies, HideGroup }

/** What the quick menu offers and says. The screens only draw it. */
object QuickMenu {
    /**
     * The rows in order. The player adds Pause or Play and Audio & subtitles above the browse rows, and
     * "Back to <channel>" above those when [hasPrevious]. [otherCopies] is how many other copies of the channel there are.
     * That count can arrive after the menu opens, so its row goes below the favourite row: the row in focus doesn't move.
     * [hasProgramme] adds "About this programme" as the first browse row.
     */
    fun rows(channel: Channel, inPlayer: Boolean, paused: Boolean, hasPrevious: Boolean = false, otherCopies: Int = 0, hasProgramme: Boolean = false): List<QuickAction> = buildList {
        if (inPlayer) {
            if (hasPrevious) add(QuickAction.LastChannel)
            add(if (paused) QuickAction.Play else QuickAction.Pause)
            add(QuickAction.Tracks)
        }
        if (hasProgramme) add(QuickAction.About)
        add(if (channel.favourite) QuickAction.RemoveFavourite else QuickAction.AddFavourite)
        if (otherCopies > 0) add(QuickAction.OtherCopies)
        if (channel.group.isNotBlank()) add(QuickAction.HideGroup)
    }

    /** [previousName] is the channel "Back to" goes to. [groupName] and [groupCount] say which group Hide takes away and how big it is. */
    fun label(action: QuickAction, previousName: String? = null, otherCopies: Int = 0, groupName: String? = null, groupCount: Int? = null): String = when (action) {
        QuickAction.LastChannel -> "Back to ${previousName.orEmpty().shortened()}".trimEnd()
        QuickAction.Pause -> "Pause"
        QuickAction.Play -> "Play"
        QuickAction.Tracks -> "Audio & subtitles"
        QuickAction.About -> "About this programme"
        QuickAction.AddFavourite -> "Add to Favourites"
        QuickAction.RemoveFavourite -> "Remove from Favourites"
        QuickAction.OtherCopies -> "Other copies ($otherCopies)"
        QuickAction.HideGroup -> when {
            groupName.isNullOrBlank() -> "Hide this group"
            groupCount == null -> "Hide ${groupName.shortened()}"
            // The count takes room, so the name gets less: the longest label stays near 32 characters.
            else -> "Hide ${groupName.shortened(MAX_NAME_WITH_COUNT)} ($groupCount channel${if (groupCount == 1) "" else "s"})"
        }
    }

    // A pill does not wrap; keep a long channel name inside the sheet.
    private fun String.shortened(max: Int = MAX_NAME): String = if (length > max) take(max - 1).trimEnd() + "…" else this

    private const val MAX_NAME = 24
    private const val MAX_NAME_WITH_COUNT = 16

    /** The line shown at the bottom after a pick, or null when the pick shows its own panel or changes the picture. */
    fun message(action: QuickAction): String? = when (action) {
        QuickAction.AddFavourite -> "Added to Favourites"
        QuickAction.RemoveFavourite -> "Removed from Favourites"
        QuickAction.HideGroup -> "Group hidden. Restore it in Settings > Channels."
        else -> null
    }

    /** How long [message] stays up. The hide line is longer, so it gets longer. */
    fun messageMillis(action: QuickAction): Long = if (action == QuickAction.HideGroup) 4_000L else 2_000L
}

/**
 * Stops the key-up of the hold that opened the menu from picking the first row.
 * Feed it every OK key event the menu sees; it says whether to swallow the event.
 * Everything is swallowed until a fresh press (a key-down that is not a repeat) arrives.
 */
class OkHoldGuard {
    private var fresh = false

    fun swallows(down: Boolean, repeatCount: Int): Boolean {
        if (down) {
            if (repeatCount == 0) fresh = true
            return !fresh
        }
        val swallow = !fresh
        fresh = false
        return swallow
    }
}
