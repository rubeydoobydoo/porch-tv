package tv.porch.app.domain

/** What a viewer can follow from a programme's title, and whether a follow fits a title. */
object FollowTerms {
    private const val MAX_NAMES = 3
    private const val MIN_LETTERS = 3
    private val separators = Regex("""\s+(?:v|vs|vs\.)\s+|:|\s+[-–—]\s+|,""", RegexOption.IGNORE_CASE)

    /** The names found in [title], at most three, then the whole trimmed title last. A title with no names offers only itself. */
    fun choices(title: String): List<String> {
        val whole = title.trim()
        if (whole.isEmpty()) return emptyList()
        val names = whole.split(separators)
            .map { it.trim() }
            .filter { piece -> piece.count { it.isLetter() } >= MIN_LETTERS }
            .distinctBy { it.lowercase() }
            .filterNot { it.equals(whole, ignoreCase = true) }
            .take(MAX_NAMES)
        return names + whole
    }

    private const val CLOSE_MIN_LETTERS = 6
    private val notWord = Regex("[^\\p{L}\\p{N}]+")

    /** The lower-case words of [text], apostrophes dropped. Read once and pass to [matchesWords] when one text is checked many times. */
    fun words(text: String): List<String> =
        text.lowercase().replace("'", "").replace("\u2019", "").split(notWord).filter { it.isNotEmpty() }

    /** True when [title] holds the whole words of [term] in order. Only a word of 6+ letters may be a close spelling ("Matildas" finds "Matilda's"). */
    fun matches(term: String, title: String): Boolean = matchesWords(words(term), words(title))

    /** [matches], on [words] already read. */
    fun matchesWords(wanted: List<String>, found: List<String>): Boolean {
        if (wanted.isEmpty() || wanted.size > found.size) return false
        return (0..found.size - wanted.size).any { start ->
            wanted.indices.all { i ->
                val word = found[start + i]
                wanted[i] == word || (wanted[i].length >= CLOSE_MIN_LETTERS && FuzzyText.matches(wanted[i], word))
            }
        }
    }

    /** The first follow that fits [title], or null. */
    fun firstMatch(follows: List<String>, title: String): String? = follows.firstOrNull { matches(it, title) }
}
