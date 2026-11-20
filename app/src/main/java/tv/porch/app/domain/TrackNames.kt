package tv.porch.app.domain

/** Readable names for audio and subtitle tracks. Pure, so the player sheet and the tests share it. */
object TrackNames {
    private val languages = mapOf(
        "en" to "English", "es" to "Spanish", "fr" to "French", "de" to "German", "it" to "Italian",
        "pt" to "Portuguese", "nl" to "Dutch", "tr" to "Turkish", "ar" to "Arabic", "ru" to "Russian",
        "pl" to "Polish", "hi" to "Hindi", "ja" to "Japanese", "ko" to "Korean", "zh" to "Chinese",
        "sv" to "Swedish", "da" to "Danish", "no" to "Norwegian", "fi" to "Finnish", "el" to "Greek",
        "he" to "Hebrew", "cs" to "Czech", "hu" to "Hungarian", "ro" to "Romanian", "uk" to "Ukrainian",
        // ISO 639-2 spellings that providers also send.
        "eng" to "English", "spa" to "Spanish", "fra" to "French", "fre" to "French", "deu" to "German",
        "ger" to "German", "ita" to "Italian", "por" to "Portuguese", "nld" to "Dutch", "dut" to "Dutch",
        "tur" to "Turkish", "ara" to "Arabic", "rus" to "Russian", "pol" to "Polish", "hin" to "Hindi",
        "jpn" to "Japanese", "kor" to "Korean", "zho" to "Chinese", "chi" to "Chinese", "swe" to "Swedish",
        "dan" to "Danish", "nor" to "Norwegian", "fin" to "Finnish", "ell" to "Greek", "gre" to "Greek",
        "heb" to "Hebrew", "ces" to "Czech", "cze" to "Czech", "hun" to "Hungarian", "ron" to "Romanian",
        "rum" to "Romanian", "ukr" to "Ukrainian",
    )

    fun isKnown(language: String?): Boolean = languages.containsKey(code(language))

    private fun code(language: String?) = language?.trim()?.lowercase()?.substringBefore('-')?.substringBefore('_')

    private fun base(language: String?, index: Int): String {
        return languages[code(language)] ?: "Track $index"
    }

    fun audio(language: String?, index: Int, description: Boolean = false): String {
        val name = base(language, index)
        return if (description) "$name, audio description" else name
    }

    fun subtitle(language: String?, index: Int, hardOfHearing: Boolean = false): String {
        val name = base(language, index)
        return if (hardOfHearing) "$name, hard of hearing" else name
    }

    /** Names that repeat get the provider's label, or a number when there is none: "English", "English 2". */
    fun dedupe(names: List<String>, labels: List<String?>): List<String> {
        val seen = mutableMapOf<String, Int>()
        return names.mapIndexed { i, name ->
            if (names.count { it == name } < 2) return@mapIndexed name
            val nth = (seen[name] ?: 0) + 1
            seen[name] = nth
            val label = labels.getOrNull(i)?.takeIf { it.isNotBlank() && it != name }
            when {
                label != null -> "$name · $label"
                nth == 1 -> name
                else -> "$name $nth"
            }
        }
    }
}
