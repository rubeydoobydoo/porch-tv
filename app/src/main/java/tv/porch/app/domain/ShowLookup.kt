package tv.porch.app.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.Normalizer

/**
 * Finding a show's poster on TVmaze: what to search for, and what to take from the answer. Pure; the network part is
 * `ShowPosters`.
 */
object ShowLookup {
    private val brackets = Regex("""\([^)]*\)|\[[^\]]*\]|\{[^}]*\}""")
    private val loopLabel = Regex("""(?<![\p{L}\p{N}])24\s*/\s*7(?![\p{L}\p{N}])""")
    private val channelWords = Regex(
        """(?<![\p{L}\p{N}])(?:hd|fhd|uhd|sd|4k|channel|marathon)(?![\p{L}\p{N}])""",
        RegexOption.IGNORE_CASE,
    )
    private val spaces = Regex("""\s+""")
    private val edges = Regex("""^[\s|:\-–—]+|[\s|:\-–—]+$""")

    // "Season 3", "S3", "Series 12" at the end; and a lone digit, which is a copy number ("American Dad 3").
    // Two digits stay, so "Ben 10" keeps its name.
    private val seasonWord = Regex("""\s+(?:season|series|s)\s*\d{1,2}$""", RegexOption.IGNORE_CASE)
    private val copyNumber = Regex("""\s+\d$""")

    // Letters spaced out one by one ("A N T Farm") are one word. A lone "A" or "I" in a title is left alone.
    private val spacedLetters = Regex("""(?<![\p{L}\p{N}])\p{L}(?: \p{L})+(?![\p{L}\p{N}])""")

    private val combining = Regex("""\p{Mn}+""")
    private val notAlphanumeric = Regex("""[^\p{L}\p{N}]""")

    /** "A N T Farm" as "ANT Farm": runs of two or more single letters are joined. */
    fun joinSpacedLetters(text: String): String = spacedLetters.replace(text) { it.value.replace(" ", "") }

    /** What to search TVmaze for, from a cleaned show name. Empty when nothing is left to search for. */
    fun searchText(name: String): String {
        var text = brackets.replace(joinSpacedLetters(name), " ")
        text = loopLabel.replace(text, " ")
        text = channelWords.replace(text, " ")
        text = edges.replace(spaces.replace(text, " ").trim(), "").trim()
        text = seasonWord.replace(text, "")
        text = copyNumber.replace(text, "")
        return edges.replace(text, "").trim()
    }

    /** What a TVmaze show answer gives: the https poster address and the IMDb id, each null when missing or unusable. */
    class Found(val poster: String?, val imdb: String?)

    /**
     * Reads a TVmaze show answer. Null when the show found isn't the one asked for (the search is fuzzy, and a wrong
     * poster is worse than none). Throws when [body] isn't JSON, so the caller can tell a bad answer from "no poster".
     */
    fun found(body: String, searchText: String): Found? {
        val show = Json.parseToJsonElement(body.trim()) as? JsonObject ?: return null
        val name = text(show["name"]) ?: return null
        if (!sameShow(name, searchText)) return null
        val medium = text((show["image"] as? JsonObject)?.get("medium"))?.trim()
        val poster = medium?.takeIf { it.startsWith("https://", ignoreCase = true) && it.length > "https://".length }
        return Found(poster, Rpdb.imdbId(text((show["externals"] as? JsonObject)?.get("imdb"))))
    }

    fun posterUrl(body: String, searchText: String): String? = found(body, searchText)?.poster

    fun imdbId(body: String, searchText: String): String? = found(body, searchText)?.imdb

    private fun text(element: Any?): String? = (element as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun key(name: String): String {
        val plain = combining.replace(Normalizer.normalize(name, Normalizer.Form.NFD), "")
        val words = plain.lowercase().split(' ').filter { it.isNotEmpty() }
        val trimmed = if (words.size > 1 && words[0] == "the") words.drop(1) else words
        return notAlphanumeric.replace(trimmed.joinToString(""), "")
    }

    /** The same name, or one that is the other plus a subtitle ("Taskmaster" and "Taskmaster NZ" count; short names must match exactly). */
    private fun sameShow(found: String, asked: String): Boolean {
        val a = key(found)
        val b = key(asked)
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        val (short, long) = if (a.length <= b.length) a to b else b to a
        return short.length >= 5 && long.startsWith(short)
    }
}
