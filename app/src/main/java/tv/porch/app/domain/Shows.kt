package tv.porch.app.domain

import tv.porch.app.parse.XtreamEpisode
import tv.porch.app.parse.XtreamSeason

/** What a finished or partly watched episode does to a show's "continue" tile. */
sealed interface ShowProgressChange {
    /** Remember where the viewer is in this episode. */
    data class Keep(val episodeId: Long, val positionMs: Long, val durationMs: Long) : ShowProgressChange

    /** The episode is done: the tile moves to this one. */
    data class Next(val episode: XtreamEpisode) : ShowProgressChange

    /** The last episode is done: the tile goes. */
    data object Clear : ShowProgressChange

    /** Sampled for under two minutes: nothing changes. */
    data object Ignore : ShowProgressChange
}

object Shows {
    private val drama = setOf("drama", "crime", "thriller", "mystery", "detective", "police")
    private val comedy = setOf("comedy", "comedies", "sitcom", "sitcoms")
    private val lifestyle = setOf("lifestyle", "reality", "food", "cooking", "home", "garden", "travel", "property", "renovation", "makeover")
    private val words = Regex("[\\p{L}\\p{N}]+")

    /** 0 drama and crime, 1 comedy, 2 lifestyle, 3 the rest. Whole words, any case. */
    fun genreRank(title: String): Int {
        val found = words.findAll(title.lowercase()).map { it.value }.toSet()
        return when {
            found.any { it in drama } -> 0
            found.any { it in comedy } -> 1
            found.any { it in lifestyle } -> 2
            else -> 3
        }
    }

    const val KIDS_SHELF = "Kids & cartoons"
    private val kids = setOf("kids", "kid", "children", "childrens", "family", "cartoon", "cartoons", "animation", "animated", "junior")

    /**
     * One shelf per cleaned title, drama first, then comedy, then lifestyle; provider order holds within a rank.
     * Grouping is [Films.shelves]. Two or more picked kids, cartoon or animation shelves become one [KIDS_SHELF].
     */
    fun shelves(categories: List<Pair<String, String>>, country: String): List<MovieShelf> {
        val shelves = Films.shelves(categories, country)
        val kidsShelves = shelves.filter { it.chosen && isKids(it.title) }
        val merged = if (kidsShelves.size < 2) shelves else {
            val one = MovieShelf(KIDS_SHELF, kidsShelves.flatMap { it.categoryIds }, true)
            shelves.mapNotNull { shelf -> if (shelf !in kidsShelves) shelf else one.takeIf { shelf === kidsShelves.first() } }
        }
        return merged.sortedBy { genreRank(it.title) }
    }

    private fun isKids(title: String): Boolean = words.findAll(title.lowercase()).any { it.value in kids }

    /** [episodes] are flattened in play order. */
    fun afterWatch(episodes: List<XtreamEpisode>, episodeId: Long, positionMs: Long, durationMs: Long): ShowProgressChange {
        if (Films.finished(positionMs, durationMs)) {
            val index = episodes.indexOfFirst { it.id == episodeId }
            val next = if (index < 0) null else episodes.getOrNull(index + 1)
            return if (next == null) ShowProgressChange.Clear else ShowProgressChange.Next(next)
        }
        return if (positionMs > 120_000L) ShowProgressChange.Keep(episodeId, positionMs, durationMs) else ShowProgressChange.Ignore
    }

    /** The season holding that episode, else the first season's number. */
    fun startSeason(seasons: List<XtreamSeason>, upToEpisodeId: Long?): Int =
        seasons.firstOrNull { season -> season.episodes.any { it.id == upToEpisodeId } }?.number
            ?: seasons.firstOrNull()?.number ?: 1

    /** "Crime · 2019 · 3 seasons · Rated 7.4", leaving out what the provider doesn't give. */
    fun meta(genre: String?, year: Int?, seasons: Int, rating: Double?): String = listOfNotNull(
        genre?.trim()?.takeIf { it.isNotEmpty() },
        year?.takeIf { it > 0 }?.toString(),
        seasons.takeIf { it > 0 }?.let { if (it == 1) "1 season" else "$it seasons" },
        rating?.takeIf { it > 0.0 }?.let { String.format(java.util.Locale.ROOT, "Rated %.1f", it) },
    ).joinToString(" \u00b7 ")

    /** The line on the episode you are up to: how far in, or "Up next" when it hasn't been started. */
    fun episodeNote(positionMs: Long): String =
        if (positionMs > 0L) "${Films.minutes(positionMs)} in" else "Up next"

    /** "Show name · S1 E3 · Episode title" for the player's panel. */
    fun playerTitle(show: String, season: Int, number: Int, title: String): String =
        "$show \u00b7 S$season E$number \u00b7 $title"
}
