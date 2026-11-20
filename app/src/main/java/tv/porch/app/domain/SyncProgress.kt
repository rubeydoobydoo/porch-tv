package tv.porch.app.domain

import java.util.Locale

/** Where a refresh is up to. Home shows [line] today; the typed fields are for richer screens later. */
data class SyncProgress(
    val stage: Stage,
    val channels: Int? = null,
    val programmes: Int = 0,
    val expected: Int? = null,
    // Set while a backup guide is read. Its rows aren't on screen yet, so it says what's really happening.
    val backupLine: String? = null,
) {
    enum class Stage { Waiting, Channels, Guide, Matching, Done }

    fun line(): String = when (stage) {
        Stage.Waiting -> "Waiting for another source"
        Stage.Channels -> "Fetching channels"
        Stage.Guide ->
            if (backupLine != null) backupLine
            else if (programmes == 0 && channels != null) "Found ${count(channels)} channels. Fetching the guide"
            else if (expected == null) "Guide filling in: ${count(programmes)} programmes so far"
            else "Guide filling in: ${count(programmes)} of ${count(expected)} programmes"
        Stage.Matching -> if (channels != null) "Matching ${count(channels)} channels to the guide" else "Matching channels to the guide"
        Stage.Done -> "Up to date"
    }

    /** The same words for Home's one line, which says the work is still going. */
    fun homeLine(): String = if (stage == Stage.Done) line() else "${line()}…"

    private fun count(n: Int): String = String.format(Locale.US, "%,d", n)
}
