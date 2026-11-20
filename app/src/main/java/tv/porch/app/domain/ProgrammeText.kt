package tv.porch.app.domain

/** Programme titles and synopses as a viewer should read them: a guide's own noise is left out. Nothing stored changes. */
object ProgrammeText {
    private val unknownMark = Regex("""\s*\(\?\)\s*$""")
    private val leadingDate = Regex(
        """^\s*(mon|tues?|wed(nes)?|thu(rs)?|fri|sat(ur)?|sun)(day)?\s+\d{1,2}\s+[a-z]+\s+\d{4}\.\s+""",
        RegexOption.IGNORE_CASE,
    )

    /** Without a trailing " (?)", a leading "LIVE: " or a trailing " - <full date>". The LIVE and date rules are [LiveRows.shownTitle]'s. */
    fun title(raw: String): String = LiveRows.shownTitle(raw).replace(unknownMark, "").trim()

    /** Without a leading date sentence such as "Tuesday 3 November 2026. ". Null and blank stay null. */
    fun synopsis(raw: String?): String? = raw?.takeIf { it.isNotBlank() }?.replace(leadingDate, "")?.takeIf { it.isNotBlank() }
}
