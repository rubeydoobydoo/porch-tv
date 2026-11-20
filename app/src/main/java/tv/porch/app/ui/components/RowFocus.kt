package tv.porch.app.ui.components

import androidx.compose.ui.focus.FocusRequester

/**
 * Remembers which row had focus, so focus can be put back when that row goes (a programme ends, a list empties).
 * Plain fields on purpose: reading or writing them redraws nothing.
 *
 * Rows report [gained]; a tab or chip reports [chrome]. [lostRow] says the row focus was last on is no longer listed,
 * and [settle] then names the nearest row that is. [group] splits keys into lists, so the nearest row is in the same list.
 */
class RowFocus<K : Any>(private val group: (K) -> Any? = { null }) {
    private val requesters = HashMap<K, FocusRequester>()
    private var order: List<K> = emptyList()
    private var onRow: K? = null

    /** True once focus has been on a tab or chip. Start-up focus then leaves it alone. */
    var inChrome = false
        private set

    /** True once any row has had focus. */
    val anyRowFocused: Boolean get() = onRow != null

    fun requester(key: K): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    fun gained(key: K) {
        onRow = key
        inChrome = false
    }

    fun chrome() {
        onRow = null
        inChrome = true
    }

    fun lostRow(rows: List<K>): Boolean = onRow?.let { it !in rows } ?: false

    /** Takes the new [rows] as the order. Returns the row nearest the one focus was on, or null if there is none. */
    fun settle(rows: List<K>): K? {
        val before = order
        order = rows
        requesters.keys.retainAll(rows.toSet())
        val lost = onRow ?: return null
        if (lost in rows) return lost
        val g = group(lost)
        val oldSame = before.filter { group(it) == g }
        val newSame = rows.filter { group(it) == g }
        if (newSame.isNotEmpty()) return newSame[oldSame.indexOf(lost).coerceIn(0, newSame.lastIndex)]
        if (rows.isEmpty()) return null
        return rows[before.indexOf(lost).coerceIn(0, rows.lastIndex)]
    }
}

/** Asks for focus; false when the row is not composed (a lazy list has not made it yet). */
fun FocusRequester.tryFocus(): Boolean = runCatching { requestFocus() }.isSuccess
