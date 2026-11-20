package tv.porch.app.domain

import java.util.Locale

/** A provider's raw name made readable: the title to show, a language or country tag, and a catalogue code. */
data class DisplayName(val title: String, val tag: String?, val code: String?)

object ChannelNames {
    // A hyphen only counts as a tag separator when a space follows, so "Sky-Fi" keeps its name.
    // Up to eight letters: with a pipe any label is a tag ("Prime|"); without one only a known code is (see withoutTags).
    private val tagPrefix = Regex("""^\s*+([A-Za-z]{2,8})\s*+([:|/\\\u2013\u2014]|-(?=\s))\s*+""")
    private val codePrefix = Regex("""^\s*+[(\[]([^)\]]+)[)\]]\s*+[|:\-]?+\s*+""")

    // Longest first: the possessive pattern below never backtracks into a shorter match ("hd" before "hdr").
    private const val QUALITY = "hdr|hevc|h265|h264|fhd|uhd|4k|hd|sd"
    private val qualityWord = Regex("""(?<![\p{L}\p{N}])(?:$QUALITY)(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)
    private val qualityOnly = Regex("""^\s*+(?:(?:$QUALITY|\d++[pi]?+|fps)\s*+)*+$""", RegexOption.IGNORE_CASE)
    private val trailingBracket = Regex("""\s*+[(\[]([^)\]]*)[)\]]\s*+$""")
    private val emptyBrackets = Regex("""\s*(?:\(\s*\)|\[\s*\])""")
    private val spaces = Regex("""\s+""")
    private val decorationRun = Regex("""[#*=\-_~\u2022]{3,}""")
    private val decorationStart = Regex("""^\s*[#*=\-_~\u2022]{3,}""")
    // Small raised letters providers use for "RAW 60fps" style decoration; wider than the brief, since \u1DA0 and \u02E2 occur in real names.
    private val modifierLetters = Regex("""[\u02B0-\u02FF\u1D2C-\u1DBF\u2070-\u209F]""")
    // Provider decoration that says nothing about the channel: "RAW", "VIP", "60fps". Capitals only for the words, so a show called "WWE Raw" keeps its name. Kept if nothing else would be left.
    private val providerNoise = Regex("""(?<![\p{L}\p{N}])(?:RAW|VIP|(?i:\d{2,3}\s*+fps))(?![\p{L}\p{N}])""")
    // "24/7 Alias" is a loop channel; the group already says 24/7, so the show's name leads.
    private val loopPrefix = Regex("""^\s*+24\s*+/\s*+7(?![\p{L}\p{N}])[\s|:\-\u2013\u2014]*+""")
    private val edges = Regex("""^[\s|:\-\u2013\u2014]+|[\s|:\-\u2013\u2014]+$""")

    // Words that stay upper-case in an all-caps name: any of these, or a three-letter word with no vowel (BBC, CNN).
    private val acronyms = setOf(
        "ABC", "AMC", "AXN", "CNBC", "ESPN", "HBO", "HGTV", "ITV", "MLB", "NBA", "NFL", "NHL", "TLC", "UFC", "USA", "WWE",
    )
    private const val VOWELS = "AEIOUY\u00C4\u00D6\u00DC\u00C9\u00C8\u00CD\u00D3\u00DA\u0130"

    // Without a pipe, a short prefix is only a tag when it is a language or country code: "BBC: World News" keeps its name.
    private val knownTags: Set<String> = LanguageTags.codes + setOf(
        "US", "UK", "GB", "AU", "CA", "NZ", "IE", "EU", "LAT", "ASIA", "AF", "IN", "PK", "BD", "LK", "MX", "BR", "AR", "CL", "CO",
        "PE", "VE", "PT", "ES", "FR", "DE", "IT", "NL", "BE", "CH", "AT", "PL", "RO", "HU", "CZ", "SK", "GR", "TR", "RU", "UA",
        "IL", "SA", "AE", "EG", "MA", "DZ", "TN", "IQ", "IR", "QA", "KW", "JP", "KR", "CN", "HK", "TW", "TH", "VN", "ID", "MY",
        "SG", "PH",
    )

    private class Cleaned(val name: DisplayName, val empty: Boolean, val decorated: Boolean)

    // The same names come round on every database emission, so each is cleaned once. Bounded; oldest drop out first.
    private val cache = object : LinkedHashMap<String, Cleaned>(1024, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cleaned>) = size > 100_000
    }

    // Cheap checks before a regex: most names have none of these, so the regex would only allocate and find nothing.
    private const val TAG_SEPARATORS = ":|/\\\u2013\u2014-"
    private const val DECORATION = "#*=-_~\u2022"
    private const val EDGE = " \t\n\r\u000B\u000C|:-\u2013\u2014"
    private fun String.hasAny(chars: String) = any { it in chars }
    private fun String.hasModifierLetter() = any { it in '\u02B0'..'\u02FF' || it in '\u1D2C'..'\u1DBF' || it in '\u2070'..'\u209F' }

    private fun withoutTags(raw: String): Pair<String?, String> {
        if (!raw.hasAny(TAG_SEPARATORS)) return null to raw
        var rest = raw
        var first: String? = null
        while (true) {
            val match = tagPrefix.find(rest) ?: break
            val letters = match.groupValues[1].uppercase(Locale.US)
            if (match.groupValues[2] != "|" && letters !in knownTags) break
            if (first == null) first = letters
            rest = rest.substring(match.range.last + 1)
        }
        return first to rest
    }

    /** Turns a provider's raw channel name into something readable. See [DisplayName]. */
    fun display(raw: String): DisplayName = clean(raw).name

    private fun clean(raw: String): Cleaned {
        synchronized(cache) { cache[raw] }?.let { return it }
        val done = compute(raw)
        synchronized(cache) { cache[raw] = done }
        return done
    }

    // A letter a provider's text lost on the way in. The letter is unknown, so the mark goes and nothing is guessed.
    private const val LOST_LETTER = '\uFFFD'

    private fun compute(provided: String): Cleaned {
        val raw = if (LOST_LETTER in provided) provided.replace(LOST_LETTER.toString(), "").ifBlank { provided } else provided
        val (tag, afterTag) = withoutTags(raw)
        val decorated = afterTag.hasAny(DECORATION) && decorationStart.containsMatchIn(afterTag)
        var rest = afterTag
        var code: String? = null
        val brackets = rest.hasAny("([")
        val codeMatch = if (brackets) codePrefix.find(rest) else null
        if (codeMatch != null && !qualityOnly.matches(codeMatch.groupValues[1])) {
            code = codeMatch.groupValues[1].trim()
            rest = rest.substring(codeMatch.range.last + 1)
        }
        if (rest.trimStart().startsWith("24")) {
            val loop = loopPrefix.find(rest)
            if (loop != null && loop.range.last + 1 < rest.length) rest = rest.substring(loop.range.last + 1)
        }
        if (rest.hasAny(DECORATION)) rest = decorationRun.replace(rest, " ")
        if (rest.hasModifierLetter()) rest = modifierLetters.replace(rest, "")
        while (rest.hasAny("([")) {
            val bracket = trailingBracket.find(rest) ?: break
            if (!qualityOnly.matches(bracket.groupValues[1])) break
            rest = rest.substring(0, bracket.range.first)
        }
        rest = qualityWord.replace(rest, " ")
        providerNoise.replace(rest, " ").let { quiet -> if (quiet.any { it.isLetterOrDigit() }) rest = quiet }
        if (rest.hasAny("([")) rest = emptyBrackets.replace(rest, " ")
        rest = spaces.replace(rest, " ").trim()
        if (rest.isNotEmpty() && (rest.first() in EDGE || rest.last() in EDGE)) rest = edges.replace(rest, "").trim()
        if (rest.isEmpty()) return Cleaned(DisplayName(raw.trim(), tag, code), empty = true, decorated = decorated)
        // Only symbols left (for example "HD+" after the quality strip): keep the name as written.
        if (rest.none { it.isLetterOrDigit() }) return Cleaned(DisplayName(raw.trim(), tag, code), empty = false, decorated = decorated)
        return Cleaned(DisplayName(upperFirst(titleCase(rest)), tag, code), empty = false, decorated = decorated)
    }

    /** All-caps names become Title Case; short words, acronyms and numbers stay as they are. */
    private fun titleCase(text: String): String {
        if (text.none { it.isLetter() } || text.any { it.isLowerCase() }) return text
        // Split on slashes too, so "AC/DC" is two short words rather than one long one.
        return text.split(' ').joinToString(" ") { chunk -> chunk.split('/').joinToString("/") { caseWord(it) } }
    }

    private fun caseWord(word: String): String {
        val keep = word.isEmpty() || word.all { it.isDigit() } || word.length <= 2 || word in acronyms ||
            (word.length == 3 && word.none { it in VOWELS })
        if (keep) return word
        // "9NOW" reads "9Now": the letters after the leading digits are the word.
        val digits = word.takeWhile { it.isDigit() }
        if (digits.isNotEmpty() && digits.length < word.length) return digits + lowerTail(word.substring(digits.length))
        return lowerTail(word)
    }

    // Per character, so Turkish dotted I does not turn into i plus a combining dot.
    private fun lowerTail(word: String): String {
        val first = word.codePointAt(0)
        val head = String(Character.toChars(first))
        val tail = StringBuilder()
        var i = head.length
        while (i < word.length) {
            val cp = word.codePointAt(i)
            tail.appendCodePoint(Character.toLowerCase(cp))
            i += Character.charCount(cp)
        }
        return head + tail
    }

    private fun upperFirst(text: String) = text.replaceFirstChar { it.uppercaseChar() }

    /** A section marker the provider uses as a channel: it starts with a run of decoration, or has nothing left once cleaned. */
    fun isDivider(raw: String): Boolean = clean(raw).let { it.decorated || it.empty }

    /** A group keeps a leading "24/7": for a group, that is what it is. */
    fun group(raw: String): DisplayName {
        val loop = loopPrefix.find(raw)?.takeIf { it.range.last + 1 < raw.length } ?: return display(raw)
        val rest = display(raw.substring(loop.range.last + 1))
        return rest.copy(title = "24/7 " + rest.title)
    }

    /**
     * A channel's name for a list: the readable title, with the heading's own label dropped when the row says it again
     * ("Example One AU" under AU). Nothing stored changes; this is computed from the raw name each time (cleaned names are cached).
     */
    fun listTitle(raw: String, heading: String? = null): String {
        val title = display(raw).title
        return if (heading == null) title else dropLabel(title, heading)
    }

    /** A group's name for a row under [heading] (a language label or "Other"): no noise, and the tag only when the heading doesn't say it. */
    fun groupRow(raw: String, heading: String? = null): String {
        val shown = group(raw)
        val title = if (heading == null) shown.title else dropLabel(shown.title, heading)
        val tag = shown.tag?.takeIf { !it.equals(heading, ignoreCase = true) }
        return if (tag != null) "$title \u00B7 $tag" else title
    }

    // A leading or trailing word equal to the label goes, unless it is the whole name.
    private fun dropLabel(title: String, label: String): String {
        val parts = title.split(' ')
        if (parts.size < 2) return title
        val rest = when {
            parts.last().equals(label, ignoreCase = true) -> parts.dropLast(1)
            parts.first().equals(label, ignoreCase = true) -> parts.drop(1)
            else -> return title
        }
        return rest.joinToString(" ")
    }

    private val noise = Regex("""\b(hd|sd|fhd|uhd|4k|hevc|h265|h264|hdr|raw)\b""")
    private val nonWord = Regex("[^a-z0-9]+")
    private val words = mapOf(
        "one" to "1",
        "two" to "2",
        "three" to "3",
        "four" to "4",
        "five" to "5",
        "six" to "6",
        "seven" to "7",
        "eight" to "8",
        "nine" to "9",
    )

    /** Lowercase, drop quality tags, turn "one" into "1", collapse punctuation. */
    fun normalize(raw: String): String {
        val lower = raw.lowercase(Locale.US).replace('&', ' ')
        val stripped = noise.replace(lower, " ")
        return stripped.split(nonWord)
            .filter { it.isNotBlank() }
            .joinToString(" ") { words[it] ?: it }
    }
}

object LanguageTags {
    /** Every two-letter language code the group names are known by. */
    val codes: Set<String> get() = words.values.toSet()

    private val prefix = Regex("""^\s*([A-Za-z]{2,3})\s*[:|/\\\u2013\u2014-]""")
    private val words = mapOf(
        "english" to "EN",
        "arabic" to "AR",
        "french" to "FR",
        "german" to "DE",
        "spanish" to "ES",
        "turkish" to "TR",
        "hindi" to "HI",
        "portuguese" to "PT",
        "italian" to "IT",
        "dutch" to "NL",
        "polish" to "PL",
        "russian" to "RU",
        "greek" to "EL",
        "hebrew" to "HE",
        "persian" to "FA",
        "farsi" to "FA",
        "urdu" to "UR",
        "chinese" to "ZH",
        "korean" to "KO",
        "japanese" to "JA",
        "swedish" to "SV",
        "norwegian" to "NO",
        "danish" to "DA",
        "finnish" to "FI",
        "romanian" to "RO",
        "hungarian" to "HU",
        "czech" to "CS",
        "ukrainian" to "UK",
        "serbian" to "SR",
        "croatian" to "HR",
        "bulgarian" to "BG",
        "thai" to "TH",
        "vietnamese" to "VI",
        "indonesian" to "ID",
        "malay" to "MS",
    )

    private val fullNames = mapOf(
        "AU" to "Australia", "UK" to "United Kingdom", "US" to "United States", "NZ" to "New Zealand", "CA" to "Canada",
        "IE" to "Ireland", "FR" to "France", "DE" to "Germany", "ES" to "Spain", "IT" to "Italy", "NL" to "Netherlands",
        "AR" to "Arabic", "TR" to "Turkey", "PT" to "Portugal", "PL" to "Poland", "IN" to "India",
        // Country codes providers use, and the longer tags seen with them. AF is left out: it could mean Africa or Afghanistan.
        "AFG" to "Afghanistan", "AFR" to "Africa", "AL" to "Albania", "AM" to "Armenia", "AT" to "Austria", "AZ" to "Azerbaijan",
        "BD" to "Bangladesh", "BE" to "Belgium", "BG" to "Bulgaria", "BR" to "Brazil", "CH" to "Switzerland", "CL" to "Chile",
        "CN" to "China", "CO" to "Colombia", "CZ" to "Czechia", "DK" to "Denmark", "DZ" to "Algeria", "EG" to "Egypt",
        "FI" to "Finland", "GR" to "Greece", "HK" to "Hong Kong", "HR" to "Croatia", "HU" to "Hungary", "ID" to "Indonesia",
        "IL" to "Israel", "IQ" to "Iraq", "IR" to "Iran", "JP" to "Japan", "KR" to "South Korea", "KW" to "Kuwait",
        "LK" to "Sri Lanka", "MA" to "Morocco", "MX" to "Mexico", "MY" to "Malaysia", "NG" to "Nigeria", "NO" to "Norway",
        "PE" to "Peru", "PH" to "Philippines", "PK" to "Pakistan", "QA" to "Qatar", "RO" to "Romania", "RS" to "Serbia",
        "RU" to "Russia", "SA" to "Saudi Arabia", "SE" to "Sweden", "SG" to "Singapore", "SK" to "Slovakia", "TH" to "Thailand",
        "TN" to "Tunisia", "TW" to "Taiwan", "UA" to "Ukraine", "AE" to "United Arab Emirates", "VE" to "Venezuela",
        "VN" to "Vietnam", "ZA" to "South Africa",
    )

    /** A tag's full name where it is known, otherwise the tag as it is. */
    fun nameOf(tag: String): String = fullNames[tag.uppercase(Locale.US)] ?: tag

    /** A short language tag at the start of a group name, such as "EN:" or "AR |". */
    fun tagOf(group: String): String? {
        prefix.find(group)?.let { return it.groupValues[1].uppercase(Locale.US) }
        val first = group.trim().substringBefore(' ').substringBefore(':').lowercase(Locale.US)
        return words[first]
    }

    /** Groups with no tag stay visible, so an unlabelled local group is not hidden by accident. */
    fun allows(group: String, filter: String): Boolean {
        val tag = tagOf(group) ?: return true
        return tag.equals(filter, ignoreCase = true)
    }
}

object ChannelBadge {
    private val plusWord = Regex("""\bplus\b""", RegexOption.IGNORE_CASE)
    private val longNumber = Regex("""\b(\d{2,3})\b""")
    private val letterThenDigit = Regex("""^([A-Za-z]).*?\b(\d)\b""")
    private val spaces = Regex("\\s+")

    fun letters(name: String): String {
        val clean = name.trim()
        if (clean.isEmpty()) return "?"
        if (plusWord.containsMatchIn(clean)) {
            val letter = clean.firstOrNull { it.isLetter() } ?: return "M+"
            return letter.uppercaseChar() + "+"
        }
        val number = longNumber.find(clean)
        if (number != null) return number.groupValues[1]
        val single = letterThenDigit.find(clean)
        if (single != null) return single.groupValues[1].uppercase() + single.groupValues[2]
        val words = clean.split(spaces).filter { it.any { ch -> ch.isLetter() } }
        if (words.size >= 2) return (words[0].take(1) + words[1].take(1)).uppercase()
        return words.firstOrNull()?.take(3)?.uppercase() ?: clean.take(2).uppercase()
    }

    /** Contrast of white text on this 0xAARRGGBB colour, by the WCAG 2 formula (1.0 to 21.0). */
    fun contrastWithWhite(rgb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((rgb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        val luminance = 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        return 1.05 / (luminance + 0.05)
    }

    /** Colours for named kinds of channel, in the order news, sport, movie, docu, kids, music, weather, fun. */
    val keywordColours: List<Long> = listOf(
        0xFF3A6FD8,
        0xFF2E9A5B,
        0xFFD2602E,
        0xFF2A8C9A,
        0xFFB9507A,
        0xFFB3761A,
        0xFF3E8FC4,
        0xFFB9507A,
    )

    /** Colours for channels with no kind keyword; the name picks one. */
    val palette: List<Long> = listOf(
        0xFF3A6FD8,
        0xFF2E9A5B,
        0xFFB3761A,
        0xFFD2602E,
        0xFF2A8C9A,
        0xFFB9507A,
        0xFFA9773A,
        0xFF3E8FC4,
        0xFF6C7FA8,
    )

    fun color(name: String, group: String): Long {
        val hay = "$group $name".lowercase(Locale.US)
        val k = keywordColours
        return when {
            "news" in hay -> k[0]
            "sport" in hay || "racing" in hay -> k[1]
            "movie" in hay || "cinema" in hay -> k[2]
            "docu" in hay -> k[3]
            "kid" in hay || "toon" in hay || "cartoon" in hay -> k[4]
            "music" in hay -> k[5]
            "weather" in hay -> k[6]
            "fun" in hay -> k[7]
            else -> palette[Math.floorMod(name.lowercase(Locale.US).hashCode(), palette.size)]
        }
    }
}
