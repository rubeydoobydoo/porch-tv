package tv.porch.app.domain

/** A row of films on the Movies tab: the cleaned title, and every provider category that cleans to it. */
data class MovieShelf(val title: String, val categoryIds: List<String>, val chosen: Boolean)

/** What the film sheet says: its pills, the resume line (null when not started) and the length ("1 h 52 min", null when unknown). */
data class FilmSheetText(val pills: List<String>, val resumeLine: String?, val length: String?)

object Films {
    /** A film counts as started after two minutes, and until it is 95% watched. A length of 0 means the stream gave none. */
    fun started(positionMs: Long, durationMs: Long): Boolean =
        positionMs > 120_000L && (durationMs <= 0L || positionMs < 0.95 * durationMs)

    /** "0:09 / 1:30:00", or just "0:09" when the stream gave no length (0), so the overlay never shows "0:00" for it. */
    fun clockPair(positionMs: Long, durationMs: Long): String =
        if (durationMs > 0L) "${clock(positionMs)} / ${clock(durationMs)}" else clock(positionMs)

    /** The progress bar shows only when the stream gave a length; an empty track says nothing. */
    fun showsBar(durationMs: Long): Boolean = durationMs > 0L

    /** The key hints under the film panel. A stream that can't skip (no length, or it won't seek) doesn't offer it. */
    fun panelHint(paused: Boolean, canSkip: Boolean): String {
        val ok = if (paused) "OK plays" else "OK pauses"
        return if (canSkip) "$ok · Left and Right skip 10 seconds · Back closes" else "$ok · Back closes"
    }

    /** Where a skip of [stepMs] lands, or null when the stream can't seek (a seek then restarts it from 0:00). */
    fun skipTarget(positionMs: Long, stepMs: Long, durationMs: Long, seekable: Boolean): Long? {
        if (!seekable) return null
        val limit = if (durationMs > 0L) durationMs else Long.MAX_VALUE
        return (positionMs + stepMs).coerceIn(0L, limit)
    }

    /** How far through, 0 to 1. An unknown length (0) gives an empty bar. */
    fun progress(positionMs: Long, durationMs: Long): Float =
        if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    /** What to save when playback ends: the length, or where it got to when the stream gave no length, so the film counts as finished either way. */
    fun endMark(positionMs: Long, durationMs: Long): Long = if (durationMs > 0L) durationMs else positionMs

    fun finished(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && positionMs >= 0.95 * durationMs

    /**
     * Shelves in provider order. Categories with the same cleaned title merge; [country] is the TV's code.
     * A picked and an unpicked category never share a shelf, so "|FR| ACTION" stays out of "|EN| ACTION".
     */
    fun shelves(categories: List<Pair<String, String>>, country: String): List<MovieShelf> {
        val tidy = categories.map { (id, raw) -> id to withoutLeadingBars(raw) }
        val keep = StarterPick.groups(tidy.map { it.second }, country)
        val groups = LinkedHashMap<Pair<String, Boolean>, MutableList<String>>()
        for ((id, raw) in tidy) {
            val title = ChannelNames.group(raw).title.trim()
            if (title.isEmpty()) continue
            groups.getOrPut(title to (raw in keep)) { mutableListOf() }.add(id)
        }
        return groups.map { (key, ids) -> MovieShelf(key.first, ids, key.second) }
    }

    // Some providers start every category with a bar ("|EN| ACTION"), which hides the label from the starter pick.
    private fun withoutLeadingBars(raw: String): String = raw.trimStart { it == '|' || it.isWhitespace() }

    /** The chosen shelves; when the starter pick chose none, all of them so the tab is never empty. */
    fun shown(shelves: List<MovieShelf>): List<MovieShelf> =
        shelves.filter { it.chosen }.ifEmpty { shelves }

    /** h:mm:ss over an hour, else m:ss. */
    fun clock(ms: Long): String {
        val total = (ms.coerceAtLeast(0L) / 1000)
        val h = total / 3600
        val m = (total % 3600) / 60
        val sec = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    /** "41 min", "1 h 11 min", "2 h". */
    fun minutes(ms: Long): String {
        val mins = ((ms.coerceAtLeast(0L) + 30_000L) / 60_000L).toInt()
        val h = mins / 60
        val m = mins % 60
        return when {
            h == 0 -> "$m min"
            m == 0 -> "$h h"
            else -> "$h h $m min"
        }
    }

    fun sheet(positionMs: Long?, durationMs: Long?, durationSecs: Int?): FilmSheetText {
        val total = durationMs?.takeIf { it > 0 } ?: durationSecs?.takeIf { it > 0 }?.let { it * 1000L }
        val length = total?.let { minutes(it) }
        if (positionMs == null || durationMs == null || !started(positionMs, durationMs)) {
            return FilmSheetText(listOf("Play"), null, length)
        }
        return FilmSheetText(
            listOf("Resume from ${clock(positionMs)}", "Play from the start"),
            if (durationMs > 0L) "${minutes(positionMs)} in \u00b7 ${minutes(durationMs - positionMs)} left" else "${minutes(positionMs)} in",
            length,
        )
    }
}
