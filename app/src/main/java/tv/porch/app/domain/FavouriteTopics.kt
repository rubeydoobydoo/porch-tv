package tv.porch.app.domain

/**
 * The picker's short list, by the sports, leagues and kinds of channel the viewer asked for (not broad kinds, so ten
 * golf channels can't crowd out the rest). A few channels per topic, Australian copies first. The cue lists are plain
 * sets, read as whole words of the channel's readable name, like [Kinds].
 */
object FavouriteTopics {
    const val PER_TOPIC = 5

    /** [australianOnly] keeps "Nine" or "Seven" from matching some other country's channel. [kinds] adds every channel of those kinds. */
    class Topic(val label: String, cues: Set<String>, val australianOnly: Boolean = false, val kinds: Set<Kind> = emptySet()) {
        internal val padded = cues.map { " $it " }
    }

    /** In the order the picker shows them. A channel goes to the first topic that wants it. */
    val topics = listOf(
        // This provider's Australian Fox channels: 504 is AFL ("Fotty"), 502 is NRL ("Leauge"), spelt as the provider spells them.
        Topic("AFL", setOf("afl", "fox footy", "footy", "fotty", "fox sports 504")),
        Topic("NRL", setOf("nrl", "fox league", "leauge", "fox sports 502")),
        Topic("Premier League", setOf("premier league", "epl")),
        Topic("Champions League", setOf("champions league", "ucl")),
        Topic("A-League", setOf("a league", "a leagues", "aleague", "aleagues")),
        Topic("F1", setOf("f1", "formula 1", "formula one")),
        Topic("NFL", setOf("nfl", "redzone")),
        Topic("UFC", setOf("ufc")),
        Topic("NBA", setOf("nba")),
        Topic("Golf", setOf("golf")),
        Topic("Tennis", setOf("tennis")),
        Topic("Wrestling", setOf("wwe", "aew", "wrestling")),
        Topic(
            "Free-to-air",
            setOf("abc", "sbs", "nitv", "seven", "7mate", "7two", "7flix", "nine", "9gem", "9go", "9life", "10", "channel 10", "10 bold", "10 peach"),
            australianOnly = true,
        ),
        Topic("World news", setOf("bbc news", "bbc world news", "cnn", "al jazeera", "sky news", "france 24", "dw", "euronews")),
        Topic("Kids", emptySet(), kinds = setOf(Kind.Kids)),
        Topic("Movies", emptySet(), kinds = setOf(Kind.Movies)),
        // TV shows is split by what people watch (Ruben, 10 Nov). Drama and crime goes first, so "Crime Comedy" is drama.
        Topic(
            "Drama and crime",
            setOf("drama", "crime", "crime investigation", "investigation discovery", "showcase", "universal", "13th street", "alibi", "fx", "amc"),
        ),
        Topic("Comedy", setOf("comedy", "comedy central", "dave")),
        Topic(
            "Lifestyle",
            setOf("lifestyle", "food", "food network", "cooking", "hgtv", "home", "renovation", "travel", "travel channel", "tlc"),
        ),
        // What is left: E! and general entertainment. "E!" reads as the word "e".
        Topic("TV shows", setOf("e", "e entertainment", "entertainment", "reality", "bravo", "uktv", "fox8", "fox 8", "fox one", "sky one", "sky max")),
        Topic("Docs", emptySet(), kinds = setOf(Kind.Docs)),
        Topic("Music", emptySet(), kinds = setOf(Kind.Music)),
    )

    data class Section(val label: String, val channels: List<Channel>)

    private val loop = Regex("""24\s*/?\s*7""")
    private val notWord = Regex("""[^\p{L}\p{N}]+""")
    private val australia = listOf(" au ", " aus ", " australia ", " australian ")

    /**
     * Only channels with a logo and no 24/7 loops. A channel also needs a guide unless it is Australian (this
     * provider's Australian sport has no guide match); a topic with nothing that passes offers the rest instead.
     */
    fun sections(channels: List<Channel>, kindOf: (Channel) -> Kind? = Kinds::of): List<Section> {
        val usable = channels.filter { it.logoUrl != null && !loop.containsMatchIn(it.shownTitle) }
        val names = usable.groupBy { it.shownTitle.lowercase() }.values.map { copies ->
            // The copy offered: Australian first, then one with a guide.
            val best = copies.sortedWith(compareBy({ !australian(it) }, { it.matchedXmltvId == null })).first()
            Name(best, copies.size, australian(best), best.matchedXmltvId != null, eventSlot(best))
        }
        // Real channels before pay-per-view event slots; then Australian, then with a guide, then the most-carried, then by
        // name: a name carried many times over is a channel people want.
        val ranked = names.sortedWith(compareBy({ it.event }, { !it.australian }, { !it.guided }, { -it.copies }, { it.channel.shownTitle.lowercase() }))
        val byTopic = LinkedHashMap<Topic, MutableList<Name>>()
        for (name in ranked) topicOf(name, kindOf)?.let { byTopic.getOrPut(it) { mutableListOf() } += name }
        return topics.mapNotNull { topic ->
            val all = byTopic[topic] ?: return@mapNotNull null
            val good = all.filter { it.guided || it.australian }.ifEmpty { all }
            Section(topic.label, good.take(PER_TOPIC).map { it.channel })
        }
    }

    private class Name(val channel: Channel, val copies: Int, val australian: Boolean, val guided: Boolean, val event: Boolean)

    private fun topicOf(name: Name, kindOf: (Channel) -> Kind?): Topic? {
        val text = words(name.channel.shownTitle)
        val kind by lazy { kindOf(name.channel) }
        return topics.firstOrNull { topic ->
            (!topic.australianOnly || name.australian) && (topic.padded.any { text.contains(it) } || kind in topic.kinds)
        }
    }

    // "AU| AFL PPV", "US| PARAMOUNT+ PPV": numbered slots for one-off events, mostly empty between them.
    private val events = listOf(" ppv ", " event ", " events ")

    private fun eventSlot(channel: Channel): Boolean = words(channel.group).let { group -> events.any { group.contains(it) } }

    private fun australian(channel: Channel): Boolean {
        if (channel.tag.equals("AU", ignoreCase = true) || channel.tag.equals("AUS", ignoreCase = true)) return true
        val group = words(channel.group)
        return australia.any { group.contains(it) }
    }

    private fun words(text: String): String = " " + notWord.replace(text.lowercase(), " ").trim() + " "
}
