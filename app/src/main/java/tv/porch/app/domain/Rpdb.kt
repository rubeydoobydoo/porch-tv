package tv.porch.app.domain

import kotlin.math.abs

/**
 * RatingPosterDB: posters with the rating drawn on, found by IMDb id. Optional; it needs the viewer's own key.
 * Pure. The key is a secret: it is only ever put in the address, and the address is never logged or shown.
 */
object Rpdb {
    private val imdb = Regex("""tt\d{6,10}""")
    private val keyShape = Regex("""[A-Za-z0-9_\-]{3,64}""")

    /** The id as IMDb writes it ("tt" then digits), or null for anything else. */
    fun imdbId(raw: String?): String? = raw?.takeIf { imdb.matches(it) }

    /** The key trimmed, or null when it is blank or not made of letters, digits, "-" and "_". */
    fun cleanKey(raw: String?): String? = raw?.trim()?.takeIf { keyShape.matches(it) }

    /** The poster address for [key] and an IMDb id, or null when either is unusable. */
    fun posterUrl(key: String?, imdbId: String?): String? {
        val good = cleanKey(key) ?: return null
        val id = imdbId(imdbId) ?: return null
        return "https://api.ratingposterdb.com/$good/imdb/poster-default/$id.jpg"
    }

    const val MIN_WIDTH = 50
    private const val SAME_SHARE = 0.97
    private const val TOLERANCE = 24

    /**
     * RPDB can answer 200 with an empty picture when it has no artwork. That is an image under [MIN_WIDTH] wide, or
     * one that is nearly all one colour (97% of the sampled pixels within a small step of the average).
     */
    fun looksBlank(width: Int, pixels: IntArray): Boolean {
        if (width < MIN_WIDTH || pixels.isEmpty()) return true
        var r = 0L
        var g = 0L
        var b = 0L
        for (p in pixels) {
            r += (p shr 16) and 0xFF
            g += (p shr 8) and 0xFF
            b += p and 0xFF
        }
        val mr = (r / pixels.size).toInt()
        val mg = (g / pixels.size).toInt()
        val mb = (b / pixels.size).toInt()
        val near = pixels.count {
            abs(((it shr 16) and 0xFF) - mr) <= TOLERANCE &&
                abs(((it shr 8) and 0xFF) - mg) <= TOLERANCE &&
                abs((it and 0xFF) - mb) <= TOLERANCE
        }
        return near >= pixels.size * SAME_SHARE
    }
}
