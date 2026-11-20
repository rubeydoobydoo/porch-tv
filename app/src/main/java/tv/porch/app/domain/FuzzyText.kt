package tv.porch.app.domain

/** Close-spelling match for search, tried only when an exact search finds nothing. */
object FuzzyText {
    private const val MIN_LETTERS = 4

    /** Too short a word has too many near neighbours to be useful. */
    fun worthTrying(needle: String): Boolean = words(needle).any { it.length >= MIN_LETTERS }

    /** True when every word typed is the same as, or close to, a word in [text]. */
    fun matches(needle: String, text: String): Boolean {
        val typed = words(needle)
        if (typed.isEmpty()) return false
        val found = words(text)
        return typed.all { word -> found.any { close(word, it) } }
    }

    private fun close(typed: String, word: String): Boolean {
        if (typed == word) return true
        // Short words match exactly or not at all, and a slip in the first letter is rare enough to skip the work.
        if (typed.length < MIN_LETTERS || typed[0] != word[0]) return false
        val allowed = if (typed.length <= 5) 1 else 2
        if (kotlin.math.abs(typed.length - word.length) > allowed) return false
        return distance(typed, word) <= allowed
    }

    /** Edits between two words, counting a swap of neighbouring letters as one. */
    private fun distance(a: String, b: String): Int {
        var older = IntArray(b.length + 1)
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var best = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) best = minOf(best, older[j - 2] + 1)
                current[j] = best
            }
            val spare = older
            older = previous
            previous = current
            current = spare
        }
        return previous[b.length]
    }

    private fun words(text: String): List<String> = text.lowercase().split(' ', '-', ':', ',', '.', '|').filter { it.isNotEmpty() }
}
