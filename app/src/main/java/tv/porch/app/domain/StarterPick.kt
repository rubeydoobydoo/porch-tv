package tv.porch.app.domain

import java.util.Locale

/**
 * The groups a new source starts with, so Home never opens on all 49,000 channels. It is only a starting
 * point: the viewer changes it on the Channels screen. The TV's country is read by the caller, not here.
 */
object StarterPick {
    private val home = setOf("AU", "NZ", "UK", "US", "EN", "GB")

    // Labels that describe the content, not a language or country, so the group is kept.
    private val content = setOf(
        "VOD", "PPV", "REPLAY", "CATCHUP", "SERIES", "MOVIES", "SPORT", "SPORTS", "NEWS", "KIDS", "MUSIC", "DOCS",
        "HD", "FHD", "UHD", "4K", "SD", "SKY", "BBC",
    )
    private val radio = Regex("""(?<![\p{L}\p{N}])radio(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)

    // A label is a short run of letters, digits, / or + before a | or a colon: "AU:", "EXYU|", "24/7 |".
    private val label = Regex("""^\s*([A-Za-z0-9/+]{2,8})\s*[|:]""")
    private val loop = Regex("""^\s*24\s*/\s*7(?![\p{L}\p{N}])""")

    /** The names in [groups] to start with. [country] is the TV's two-letter country code, or blank. */
    fun groups(groups: Collection<String>, country: String): Set<String> {
        val own = country.trim().uppercase(Locale.US)
        return groups.filterTo(LinkedHashSet()) { keep(it, own) }
    }

    private fun keep(group: String, own: String): Boolean {
        if (radio.containsMatchIn(group)) return false
        val word = label.find(group)?.groupValues?.get(1)?.uppercase(Locale.US)
        if (word != null) return word in home || word in content || word == "24/7" || (own.isNotEmpty() && word == own)
        if (loop.containsMatchIn(group)) return true
        // No label: a language word ("Hindi Movies") is judged like a label; anything else is kept.
        val tag = LanguageTags.tagOf(group) ?: return true
        return tag in home || (own.isNotEmpty() && tag == own)
    }

    /** Only a source's first channel save picks. A later refresh, or a source that already has picks, never does. */
    fun shouldPick(hadChannels: Boolean, hasPicks: Boolean): Boolean = !hadChannels && !hasPicks
}
