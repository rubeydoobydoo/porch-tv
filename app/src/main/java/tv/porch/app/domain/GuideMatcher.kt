package tv.porch.app.domain

data class MatchableChannel(
    val key: String,
    val tvgId: String?,
    val tvgName: String?,
    val name: String,
)

data class MatchableGuide(
    val xmltvId: String,
    val names: List<String>,
)

/** A channel with its names cleaned at most once, however many guide files it's matched against. */
class PreparedChannel internal constructor(internal val channel: MatchableChannel, private val label: Regex) {
    val key: String get() = channel.key

    internal val names: List<String> by lazy(LazyThreadSafetyMode.NONE) {
        listOfNotNull(channel.tvgName, channel.name)
            .map { ChannelNames.normalize(it) }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    internal val unlabelled: List<String> by lazy(LazyThreadSafetyMode.NONE) {
        listOfNotNull(channel.tvgName, channel.name).map { ChannelNames.normalize(label.replace(it, "")) }
    }
}

/**
 * Match playlist channels to XMLTV channels.
 * Order: tvg-id (case-insensitive), then the same name after cleanup, then the same name without
 * a provider label, then a close name that shares the first word. A close call between two different guide channels is left unmatched.
 * A tvg-id that isn't in the guide falls through to the name, because bad ids are common.
 */
object GuideMatcher {
    private val label = Regex("""^\s*[A-Za-z0-9/+]{2,8}\s*[:|]+\s*""")

    fun prepare(channels: List<MatchableChannel>): List<PreparedChannel> = channels.map { PreparedChannel(it, label) }

    /** [fuzzy] false stops after the exact steps, with no close-name guess. */
    fun match(channels: List<MatchableChannel>, guide: List<MatchableGuide>, fuzzy: Boolean = true): Map<String, String> =
        matchPrepared(prepare(channels), guide, fuzzy)

    fun matchPrepared(channels: List<PreparedChannel>, guide: List<MatchableGuide>, fuzzy: Boolean = true): Map<String, String> {
        val byId = HashMap<String, String>(guide.size * 2)
        val byName = HashMap<String, String>(guide.size * 2)
        val buckets = HashMap<String, MutableList<Pair<String, String>>>()
        for (entry in guide) {
            val id = entry.xmltvId.trim()
            if (id.isEmpty()) continue
            byId.putIfAbsent(id.lowercase(), id)
            val names = entry.names.ifEmpty { listOf(id) }
            for (raw in names) {
                val norm = ChannelNames.normalize(raw)
                if (norm.isEmpty()) continue
                byName.putIfAbsent(norm, id)
                val token = norm.substringBefore(' ')
                if (fuzzy && token.length >= 3) {
                    buckets.getOrPut(token) { mutableListOf() }.add(norm to id)
                }
            }
        }

        val matched = HashMap<String, String>(channels.size)
        for (channel in channels) {
            val id = channel.channel.tvgId?.trim()?.lowercase().orEmpty()
            if (id.isNotEmpty()) {
                val hit = byId[id]
                if (hit != null) {
                    matched[channel.key] = hit
                    continue
                }
            }
            val names = channel.names
            val exact = names.firstNotNullOfOrNull { byName[it] }
            if (exact != null) {
                matched[channel.key] = exact
                continue
            }
            // Providers put a label before the name, as in "US| Name". Exact only: without the label, close names mislead.
            val unlabelled = channel.unlabelled.firstNotNullOfOrNull { byName[it] }
            if (unlabelled != null) {
                matched[channel.key] = unlabelled
                continue
            }
            if (!fuzzy) continue
            val close = fuzzy(names, buckets)
            if (close != null) matched[channel.key] = close
        }
        return matched
    }

    private fun fuzzy(names: List<String>, buckets: Map<String, List<Pair<String, String>>>): String? {
        val scores = HashMap<String, Double>()
        for (name in names) {
            if (name.length < 5) continue
            val pool = buckets[name.substringBefore(' ')].orEmpty()
            for ((other, id) in pool) {
                if (kotlin.math.abs(other.length - name.length) > 8) continue
                val score = similarity(name, other)
                val previous = scores[id] ?: 0.0
                if (score > previous) scores[id] = score
            }
        }
        val ranked = scores.entries.sortedByDescending { it.value }
        val top = ranked.firstOrNull() ?: return null
        if (top.value < 0.86) return null
        val next = ranked.getOrNull(1)
        if (next != null && top.value - next.value < 0.04) return null
        return top.key
    }
}

internal fun similarity(a: String, b: String): Double {
    if (a == b) return 1.0
    val maxLen = maxOf(a.length, b.length)
    if (maxLen == 0) return 1.0
    return 1.0 - levenshtein(a, b).toDouble() / maxLen
}

private fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var previous = IntArray(b.length + 1) { it }
    var current = IntArray(b.length + 1)
    for (i in 1..a.length) {
        current[0] = i
        val ca = a[i - 1]
        for (j in 1..b.length) {
            val cost = if (ca == b[j - 1]) 0 else 1
            current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
        }
        val swap = previous
        previous = current
        current = swap
    }
    return previous[b.length]
}
