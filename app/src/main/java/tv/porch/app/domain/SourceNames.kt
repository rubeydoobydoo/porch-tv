package tv.porch.app.domain

/** Default source names. Two sources from one server would both be named after its host. */
object SourceNames {
    /** The cleaned name when the person typed one; else [cleaned] (the host), numbered when [existing] already has it. */
    fun resolve(typed: String, cleaned: String, existing: List<String>): String =
        if (typed.isBlank()) unique(cleaned, existing) else cleaned

    /** [wanted] when no name in [existing] matches it (ignoring case), else "wanted 2", "wanted 3" and so on. */
    fun unique(wanted: String, existing: List<String>): String {
        val taken = existing.map { it.trim().lowercase() }.toSet()
        if (wanted.trim().lowercase() !in taken) return wanted
        var n = 2
        while ("$wanted $n".lowercase() in taken) n++
        return "$wanted $n"
    }
}
