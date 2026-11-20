package tv.porch.app.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** A second way in to the list: what a channel is about, across every provider group. */
enum class Kind(val label: String) { Sport("Sport"), News("News"), Movies("Movies"), Kids("Kids"), Music("Music"), Docs("Docs") }

/**
 * Works out a channel's kind from the words in its name and its group. The word lists are plain sets so
 * they are easy to read and change. A cue is a whole word or a whole run of words ("sky sports"), never
 * part of a longer word. The first kind with a cue wins, in the order of [order]; the name is read before the group.
 */
object Kinds {
    private val sport = setOf(
        "sport", "sports", "football", "soccer", "nfl", "nba", "nhl", "mlb", "ufc", "f1", "motorsport", "racing",
        "golf", "tennis", "cricket", "rugby", "espn", "bein", "dazn", "sky sports", "tnt sports", "ppv",
    )
    private val news = setOf(
        "news", "cnn", "bbc news", "fox news", "msnbc", "sky news", "al jazeera", "bloomberg", "cnbc", "weather",
    )
    private val movies = setOf(
        "movie", "movies", "cinema", "film", "films", "hbo", "starz", "cinemax", "showtime", "paramount+",
    )
    private val kids = setOf(
        "kids", "kid", "cartoon", "cartoons", "nick", "nickelodeon", "disney", "boomerang", "cbeebies", "toon", "junior", "baby",
    )
    private val music = setOf("music", "mtv", "vh1", "radio", "hits", "fm")
    private val docs = setOf(
        "documentary", "documentaries", "docu", "discovery", "nat geo", "national geographic", "history", "animal planet", "science",
    )

    /** Sport, then News, Kids, Movies, Docs, Music: so "Sky Sports News" is Sport and "Kids Movies" is Kids. */
    private val order: List<Pair<Kind, Set<String>>> = listOf(
        Kind.Sport to sport,
        Kind.News to news,
        Kind.Kids to kids,
        Kind.Movies to movies,
        Kind.Docs to docs,
        Kind.Music to music,
    )

    // Each cue padded with spaces once, so matching does not build strings for every channel.
    private val padded: List<Pair<Kind, List<String>>> = order.map { (kind, cues) -> kind to cues.map { " $it " } }

    private val notWord = Regex("""[^\p{L}\p{N}+]+""")
    // "ESPN+", "Sky Cinema+1": a trailing "+" or "+1" on a word does not change the word.
    private val plusTail = Regex("""\+\d*(?= |$)""")

    // The same channels come round on every database emission, so each is read once. Bounded; oldest drop out first.
    private val cache = object : LinkedHashMap<Pair<String, String>, Kind?>(1024, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, String>, Kind?>) = size > 100_000
    }

    fun of(name: String, group: String): Kind? {
        val key = name to group
        synchronized(cache) { if (cache.containsKey(key)) return cache[key] }
        val found = match(words(ChannelNames.display(name).title))
            // The cleaned title drops a leading "24/7", which is what marks a loop of films or series.
            ?: (if (name.contains("24") && twentyFourSevenFilm(words(name))) Kind.Movies else null)
            ?: match(words(ChannelNames.group(group).title))
        synchronized(cache) { cache[key] = found }
        return found
    }

    fun of(channel: Channel): Kind? = of(channel.name, channel.group)

    /** Every channel's kind, by channel id (channels with no kind are left out). Do this once per database emission, off the main thread. */
    fun index(channels: List<Channel>): Map<Long, Kind> {
        val found = HashMap<Long, Kind>()
        for (channel in channels) of(channel)?.let { found[channel.id] = it }
        return found
    }

    /** The same map as [index], with the work split across the CPU's cores. Used to read every kind once, ahead of the guide. */
    suspend fun indexInParallel(
        channels: List<Channel>,
        parts: Int = Runtime.getRuntime().availableProcessors(),
    ): Map<Long, Kind> {
        val count = minOf(parts, channels.size / 2_000)
        if (count < 2) return index(channels)
        val size = (channels.size + count - 1) / count
        return coroutineScope {
            channels.chunked(size).map { part -> async(Dispatchers.Default) { index(part) } }.awaitAll()
        }.fold(HashMap()) { all, part -> all.putAll(part); all }
    }

    /** [kind] null means all. [kindOf] defaults to reading the words; pass an [index] lookup to skip that. */
    fun filter(channels: List<Channel>, kind: Kind?, kindOf: (Channel) -> Kind? = ::of): List<Channel> =
        if (kind == null) channels else channels.filter { kindOf(it) == kind }

    /** The kinds that at least one of these channels has, in the order the pills show them. */
    fun present(channels: List<Channel>, kindOf: (Channel) -> Kind? = ::of): List<Kind> {
        val seen = channels.mapNotNullTo(HashSet(), kindOf)
        return Kind.entries.filter { it in seen }
    }

    // Padded with spaces so " sky sports " only matches whole words.
    private fun words(text: String): String = " " + notWord.replace(text.lowercase(), " ").trim() + " "

    private fun match(text: String): Kind? {
        // Read with and without a trailing "+", so "paramount+" and "espn+" both work.
        val plain = plusTail.replace(text, "")
        return padded.firstOrNull { (kind, cues) ->
            cues.any { text.contains(it) || plain.contains(it) } ||
                (kind == Kind.Movies && twentyFourSevenFilm(plain))
        }?.first
    }

    // "24/7 Series", "24/7 Shows": round-the-clock channels of films and series.
    private fun twentyFourSevenFilm(text: String): Boolean =
        text.contains(" 24 7 ") && (text.contains(" series ") || text.contains(" shows "))
}
