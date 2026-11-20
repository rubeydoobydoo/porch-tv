package tv.porch.app.domain

import java.util.IdentityHashMap
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/** The picture quality a copy's raw name claims. In the order a copy is preferred: UHD ranks below HD because it is what stutters. */
enum class CopyQuality(val label: String?) { FHD("FHD"), HD("HD"), UHD("UHD"), NONE(null), SD("SD") }

data class FoldCounts(val total: Int, val shown: Int)

/**
 * Which channels are copies of one another. Only channels that have a copy are held, so a lookup misses for the rest.
 * Worked out once per channel list, off the main thread, and read by every screen.
 */
class ChannelFamilies internal constructor(
    private val byChannel: Map<Long, List<Channel>>,
    private val qualities: Map<String, CopyQuality> = emptyMap(),
) {
    /** The whole family, best copy first, the channel itself included. Empty for a channel with no copies. */
    fun copiesOf(id: Long): List<Channel> = byChannel[id].orEmpty()

    /** The family without this channel: what "Other copies" lists. */
    fun othersOf(id: Long): List<Channel> = copiesOf(id).filter { it.id != id }

    fun sameFamily(a: Long, b: Long): Boolean {
        val family = byChannel[a] ?: return false
        return family === byChannel[b]
    }

    internal val isEmpty: Boolean get() = byChannel.isEmpty()

    internal fun familyOf(id: Long): List<Channel>? = byChannel[id]

    internal fun qualityOf(raw: String): CopyQuality = qualities[raw] ?: ChannelFold.qualityOf(raw)

    companion object {
        /** Nothing folds: the Settings switch is off. */
        val None = ChannelFamilies(emptyMap())
    }
}

/**
 * Folds the copies a provider sells of one channel ("Example Sport", "Example Sport FHD", "Example Sport (2)") into one.
 * Pure. Channels are grouped by a key made from the country label and the name with only quality words taken out, so
 * anything else in a name (+1, East, a number, a city) keeps channels apart without having to be listed. No pairwise compare.
 */
object ChannelFold {
    /** Reconnects a copy gets once the player has moved on from the channel asked for. That one gets the full [RetryPolicy]. */
    const val COPY_ATTEMPTS: Int = 1

    /** Reconnects the channel asked for gets when another copy is waiting: about seven seconds, not a minute of black screen. */
    const val ASKED_COPY_ATTEMPTS: Int = 3

    private const val MIN_NAME = 3

    // Words that say how a stream is delivered, not what it is. "raw" isn't here: ChannelNames drops capital RAW, and a show can be called Raw.
    private val qualityWords = setOf(
        "sd", "hd", "fhd", "uhd", "4k", "8k", "hevc", "h265", "h264", "hdr", "vip",
        "25fps", "30fps", "50fps", "60fps", "720p", "1080p", "1080i", "2160p",
    ).groupBy { it.length }.let { byLength -> Array(6) { byLength[it].orEmpty().toTypedArray() } }

    // Dropped only at the end of a name: "Alt Example" is a channel. "Backup 2" is a spare too, but on real lists "Alt 1" and "Alt 2" are different feeds.
    private val spareWords = arrayOf("backup", "bk", "alt")
    private val numberedSpareWords = arrayOf("backup", "bk")

    // A last word that is a number in writing, so "Example Two" and "Example 2" are one channel. "Example One North" stays as it is.
    private val numberWords = arrayOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine")

    // With a number these are one-off events, however alike their names look.
    private val eventWords = arrayOf("ppv", "event", "events", "match", "matchday", "fight", "fixture")

    private class GroupFacts(val label: String?, val loop: Boolean, val event: Boolean, val name: String)

    /** Every family in [channels]. With [on] false, none: every list comes back as it went in. */
    fun families(channels: List<Channel>, on: Boolean = true): ChannelFamilies {
        if (!on) return ChannelFamilies.None
        val keys = arrayOfNulls<String>(channels.size)
        writeKeys(channels, keys, 0, channels.size)
        return merged(listOf(shard(channels, keys, 0, 1)))
    }

    /**
     * The same answer as [families], split across the CPU's cores: a Chromecast takes half a second over 49,000 names on one.
     * The names are read in parts of the list, then each worker takes its own share of the keys, so none of them share a map.
     */
    suspend fun familiesInParallel(channels: List<Channel>, on: Boolean = true, parts: Int = Runtime.getRuntime().availableProcessors()): ChannelFamilies {
        if (!on) return ChannelFamilies.None
        val count = minOf(parts, channels.size / MIN_PART)
        if (count < 2) return families(channels)
        return coroutineScope {
            val keys = arrayOfNulls<String>(channels.size)
            val size = (channels.size + count - 1) / count
            (0 until count).map { part ->
                launch(Dispatchers.Default) { writeKeys(channels, keys, part * size, minOf(channels.size, (part + 1) * size)) }
            }.joinAll()
            merged((0 until count).map { share -> async(Dispatchers.Default) { shard(channels, keys, share, count) } }.awaitAll())
        }
    }

    private const val MIN_PART = 2_000

    private class Shard(val byChannel: Map<Long, List<Channel>>, val qualities: Map<String, CopyQuality>)

    private fun writeKeys(channels: List<Channel>, keys: Array<String?>, from: Int, until: Int) {
        val groups = HashMap<String, GroupFacts>()
        val scan = NameScan()
        val key = StringBuilder(64)
        for (i in from until until) {
            if (keyOf(channels[i], groups, scan, key)) keys[i] = key.toString()
        }
    }

    private fun shard(channels: List<Channel>, keys: Array<String?>, share: Int, shares: Int): Shard {
        // A channel on its own, or a list once a second turns up: most keys never need the list.
        val buckets = HashMap<String, Any>(channels.size * 2 / shares)
        for (i in keys.indices) {
            val key = keys[i] ?: continue
            if (shares > 1 && (key.hashCode() and Int.MAX_VALUE) % shares != share) continue
            val channel = channels[i]
            when (val held = buckets[key]) {
                null -> buckets[key] = channel
                is Channel -> buckets[key] = arrayListOf(held, channel)
                else -> @Suppress("UNCHECKED_CAST") (held as MutableList<Channel>).add(channel)
            }
        }
        val byChannel = HashMap<Long, List<Channel>>()
        val qualities = HashMap<String, CopyQuality>()
        val order = CopyOrder { name -> qualities.getOrPut(name) { qualityOf(name) } }
        for (held in buckets.values) {
            if (held is Channel) continue
            @Suppress("UNCHECKED_CAST")
            for (family in splitByGuide(held as MutableList<Channel>)) {
                if (family.size < 2) continue
                family.sortWith(order)
                for (member in family) byChannel[member.id] = family
            }
        }
        return Shard(byChannel, qualities)
    }

    private fun merged(shards: List<Shard>): ChannelFamilies {
        val single = shards.singleOrNull()
        if (single != null) return ChannelFamilies(single.byChannel, single.qualities)
        val byChannel = HashMap<Long, List<Channel>>(shards.sumOf { it.byChannel.size } * 2)
        val qualities = HashMap<String, CopyQuality>(shards.sumOf { it.qualities.size } * 2)
        for (part in shards) {
            byChannel.putAll(part.byChannel)
            qualities.putAll(part.qualities)
        }
        return ChannelFamilies(byChannel, qualities)
    }

    /**
     * [channels] with each family down to its best copy among those in the list; everything keeps its place.
     * [keep] is a channel the viewer chose: it stands for its family whatever its rank.
     */
    fun fold(channels: List<Channel>, families: ChannelFamilies, keep: Long? = null): List<Channel> {
        if (families.isEmpty) return channels
        val best = IdentityHashMap<List<Channel>, Channel>()
        val order = CopyOrder(families::qualityOf)
        for (channel in channels) {
            val family = families.familyOf(channel.id) ?: continue
            val current = best[family]
            if (current == null || order.compare(channel, current) < 0) best[family] = channel
        }
        if (keep != null) {
            val family = families.familyOf(keep)
            val kept = channels.firstOrNull { it.id == keep }
            if (family != null && kept != null) best[family] = kept
        }
        return channels.filter { channel ->
            val family = families.familyOf(channel.id)
            family == null || best[family]?.id == channel.id
        }
    }

    /**
     * [fold] for a list that runs on the guide (programmes, what's on now): only copies with guide data take part. The best copy
     * overall may have none, because last played and favourite rank above it, and it would take its family's matched copy with it.
     */
    fun foldGuided(channels: List<Channel>, families: ChannelFamilies, keep: Long? = null): List<Channel> =
        fold(channels.filter { it.matchedXmltvId != null }, families, keep)

    /** The copy to try after the [tried] ones have failed, best first. Null when none is left, and always on catch-up. */
    fun nextCopy(copies: List<Channel>, tried: Set<Long>, catchup: Boolean): Channel? =
        if (catchup) null else copies.firstOrNull { it.id !in tried }

    /** The same channels counted before and after folding, whether or not the switch is on. */
    fun counts(channels: List<Channel>, families: ChannelFamilies = families(channels)): FoldCounts =
        FoldCounts(channels.size, fold(channels, families).size)

    /** A row in the Other copies panel: the channel's name is the panel's title, so a row says how this copy differs. */
    fun copyLabel(channel: Channel): String =
        listOfNotNull(qualityOf(channel.name).label, channel.number.takeIf { it > 0 }?.let { "Ch $it" })
            .joinToString("   ")
            .ifEmpty { channel.shownTitle }

    /** Reads the raw name, since the shown name has had these words taken out. The best word wins if there are several. */
    fun qualityOf(raw: String): CopyQuality {
        var found = CopyQuality.NONE
        val word = CharArray(QUALITY_WORD_MAX)
        var length = 0
        var inRaised = false
        fun endWord() {
            if (length in 1..QUALITY_WORD_MAX) {
                val quality = qualityWord(word, length)
                if (quality != null && (found == CopyQuality.NONE || quality.ordinal < found.ordinal)) found = quality
            }
            length = 0
        }
        for (char in raw) {
            val raised = raisedLetter(char)
            val plain = if (raised != NOT_A_LETTER) raised else plainChar(char)
            // A word and its raised letters are two words: "Example\u1D34\u1D30" is Example, then HD.
            if (plain == NOT_A_LETTER || (raised != NOT_A_LETTER) != inRaised) endWord()
            if (plain != NOT_A_LETTER) {
                if (length < QUALITY_WORD_MAX) word[length] = plain
                length++
            }
            inRaised = raised != NOT_A_LETTER
        }
        endWord()
        return found
    }

    private const val QUALITY_WORD_MAX = 5

    private fun qualityWord(word: CharArray, length: Int): CopyQuality? = when {
        word.spells(length, "fhd") || word.spells(length, "1080p") || word.spells(length, "1080i") -> CopyQuality.FHD
        word.spells(length, "hd") || word.spells(length, "720p") -> CopyQuality.HD
        word.spells(length, "uhd") || word.spells(length, "4k") || word.spells(length, "8k") || word.spells(length, "2160p") -> CopyQuality.UHD
        word.spells(length, "sd") -> CopyQuality.SD
        else -> null
    }

    private fun CharArray.spells(length: Int, text: String): Boolean {
        if (length != text.length) return false
        for (i in 0 until length) if (this[i] != text[i]) return false
        return true
    }

    // The raised letters providers write quality in. Everything else in that Unicode range says nothing we need.
    private fun raisedLetter(char: Char): Char = when (char) {
        '\u1D34' -> 'h'
        '\u1D30' -> 'd'
        '\u1DA0' -> 'f'
        '\u1D41' -> 'u'
        '\u02E2' -> 's'
        '\u2074' -> '4'
        '\u1D4F' -> 'k'
        else -> NOT_A_LETTER
    }

    private const val NOT_A_LETTER = '\u0000'

    /** Lower case for a letter or digit, else [NOT_A_LETTER]. English letters skip the Unicode tables, which cost a call into native code on Android. */
    private fun plainChar(char: Char): Char = when {
        char in 'a'..'z' || char in '0'..'9' -> char
        char in 'A'..'Z' -> char + 32
        char.code < 128 -> NOT_A_LETTER
        char.isLetterOrDigit() -> char.lowercaseChar()
        else -> NOT_A_LETTER
    }

    /** Best first: favourite, last played, has guide data, quality, lowest number. The id settles a tie, so the order never wobbles. */
    private class CopyOrder(private val quality: (String) -> CopyQuality) : Comparator<Channel> {
        override fun compare(a: Channel, b: Channel): Int {
            if (a.favourite != b.favourite) return if (a.favourite) -1 else 1
            val playedA = a.lastPlayedAt ?: Long.MIN_VALUE
            val playedB = b.lastPlayedAt ?: Long.MIN_VALUE
            if (playedA != playedB) return if (playedA > playedB) -1 else 1
            val guideA = a.matchedXmltvId != null
            if (guideA != (b.matchedXmltvId != null)) return if (guideA) -1 else 1
            val qualityA = quality(a.name).ordinal
            val qualityB = quality(b.name).ordinal
            if (qualityA != qualityB) return qualityA - qualityB
            if (a.number != b.number) return a.number.compareTo(b.number)
            return a.id.compareTo(b.id)
        }
    }

    /** One family, unless two different guide ids are in the bucket: then one per id, and the unmatched can't say which they belong to. */
    private fun splitByGuide(bucket: MutableList<Channel>): Collection<MutableList<Channel>> {
        val first = bucket.firstNotNullOfOrNull { it.matchedXmltvId } ?: return listOf(bucket)
        if (bucket.all { it.matchedXmltvId == null || it.matchedXmltvId == first }) return listOf(bucket)
        return bucket.groupByTo(HashMap()) { it.matchedXmltvId }.values
    }

    /** Writes source, country label and cleaned name into [key]. False for a channel that always stands alone. */
    private fun keyOf(channel: Channel, groups: HashMap<String, GroupFacts>, scan: NameScan, key: StringBuilder): Boolean {
        val facts = groups.getOrPut(channel.group) { groupFacts(channel.group, scan) }
        if (facts.loop || isLoopName(channel.name)) return false
        // Stored at import, so a list of 49,000 needs no regex here. A blank title is a row that hasn't been cleaned.
        val stored = channel.title.isNotBlank()
        val shown = if (stored) null else ChannelNames.display(channel.name)
        scan.read(shown?.title ?: channel.title, channel.name)
        if (scan.nameLength < MIN_NAME) return false
        // A "#1" or "(2)" is gone from the text by now, so it counts as a number here: "PPV #1" and "PPV #2" are two events.
        if ((scan.hasDigit || scan.droppedNumber) && (facts.event || scan.hasEventWord())) return false
        val tag = if (stored) channel.tag else shown?.tag
        val code = if (stored) channel.code else shown?.code
        val label = sameLabel(tag) ?: sameLabel(code?.takeIf(::looksLikeLabel)) ?: facts.label
        key.setLength(0)
        key.append(channel.sourceId).append('|').append(label.orEmpty())
        // What sets two channels apart when the label doesn't: a leading bracket ("(+1)", "(East)", "[DE]") the label didn't use, or, with no label, the group.
        key.append('|')
        if (code != null && sameLabel(code) != label) key.append(code.lowercase(Locale.ROOT))
        key.append('|')
        if (label == null) key.append(facts.name)
        key.append('|').append(scan.text)
        return true
    }

    private fun groupFacts(group: String, scan: NameScan): GroupFacts {
        scan.read(group)
        return GroupFacts(
            label = sameLabel(ChannelNames.group(group).tag),
            loop = isLoopName(group),
            event = scan.hasEventWord(),
            name = scan.text.toString(),
        )
    }

    private fun isLoopName(text: String): Boolean {
        if ('2' !in text) return false
        return text.contains("24/7") || text.contains("24 / 7") || text.contains("24-7") || text.contains("24x7", ignoreCase = true)
    }

    private fun looksLikeLabel(code: String): Boolean = code.length in 2..3 && code.all { it.isLetter() } && !code.equals("VIP", ignoreCase = true)

    // One country goes by more than one label.
    private fun sameLabel(label: String?): String? = when (label) {
        null -> null
        "UK", "US", "AU", "NZ", "CA", "IE" -> label
        else -> when (val upper = label.uppercase(Locale.ROOT)) {
            "GB" -> "UK"
            "USA" -> "US"
            else -> upper
        }
    }

    /**
     * Reads a name into its words, lower case, with quality words, copy markers and spare labels gone. "+" stays a word of its
     * own, so "+1" and "Example+" stay apart. One is reused for a whole list: this runs 49,000 times, so it makes no strings.
     */
    private class NameScan {
        /** The words kept, each followed by a space. */
        val text = StringBuilder(64)
        private var starts = IntArray(16)
        private var words = 0

        /** Characters in the words, without the spaces. */
        val nameLength: Int get() = text.length - words

        /** A "#2" or "(2)" copy marker was left out of [text]. */
        var droppedNumber = false
            private set

        val hasDigit: Boolean
            get() {
                for (i in 0 until text.length) if (text[i] in '0'..'9') return true
                return false
            }

        /** [raw] is the name as the provider wrote it, needed only to tell a raised 2 from the start of a raised 25fps. */
        fun read(title: String, raw: String? = null) {
            text.setLength(0)
            words = 0
            droppedNumber = false
            var start = 0
            var rawAt = 0
            var i = 0
            val end = title.length
            while (i < end) {
                val char = title[i]
                when {
                    char == '(' || char == '[' -> {
                        start = endWord(start)
                        val close = copyMarkerEnd(title, i)
                        if (close > 0) {
                            i = close
                            droppedNumber = true
                        }
                    }
                    char == '#' -> {
                        start = endWord(start)
                        if (onlyDigitsAfter(title, i)) {
                            i = end
                            droppedNumber = true
                        }
                    }
                    char == '+' -> {
                        start = endWord(start)
                        text.append('+')
                        start = endWord(start)
                    }
                    // "H.265" is one word.
                    char == '.' && text.length == start + 1 && text[start] == 'h' && i + 1 < end && title[i + 1] in '0'..'9' -> Unit
                    char == '\u00B9' || char == '\u00B2' || char == '\u00B3' -> {
                        val at = raw?.indexOf(char, rawAt) ?: -1
                        if (at >= 0) rawAt = at + 1
                        if (raw != null && at >= 0 && inRaisedRun(raw, at)) {
                            start = endWord(start)
                        } else {
                            text.append(if (char == '\u00B9') '1' else if (char == '\u00B2') '2' else '3')
                        }
                    }
                    else -> {
                        val plain = plainChar(char)
                        if (plain != NOT_A_LETTER) text.append(plain) else start = endWord(start)
                    }
                }
                i++
            }
            endWord(start)
            dropSpareWords()
            writeLastNumber()
        }

        private fun inRaisedRun(raw: String, at: Int): Boolean =
            (at + 1 < raw.length && isRaised(raw[at + 1])) || (at > 0 && isRaised(raw[at - 1]))

        private fun isRaised(char: Char): Boolean =
            char in '\u02B0'..'\u02FF' || char in '\u1D2C'..'\u1DBF' || char in '\u2070'..'\u209F' || char == '\u00B9' || char == '\u00B2' || char == '\u00B3'

        private fun writeLastNumber() {
            if (words < 2) return
            val last = words - 1
            val number = numberWords.indexOfFirst { wordIs(last, arrayOf(it)) }
            if (number < 0) return
            text.setLength(starts[last])
            text.append(number + 1).append(' ')
        }

        /** Keeps the word running from [start] unless it's a quality word. Returns where the next one starts. */
        private fun endWord(start: Int): Int {
            val length = text.length - start
            if (length == 0) return start
            if (length < qualityWords.size && qualityWords[length].any { spells(start, length, it) }) {
                text.setLength(start)
                return start
            }
            if (words == starts.size) starts = starts.copyOf(words * 2)
            starts[words++] = start
            text.append(' ')
            return text.length
        }

        private fun spells(start: Int, length: Int, word: String): Boolean {
            if (length != word.length) return false
            for (i in 0 until length) if (text[start + i] != word[i]) return false
            return true
        }

        private fun wordIs(index: Int, candidates: Array<String>): Boolean {
            val start = starts[index]
            val length = (if (index + 1 < words) starts[index + 1] else text.length) - start - 1
            return candidates.any { spells(start, length, it) }
        }

        private fun wordIsNumber(index: Int): Boolean {
            val start = starts[index]
            val stop = (if (index + 1 < words) starts[index + 1] else text.length) - 1
            for (i in start until stop) if (text[i] !in '0'..'9') return false
            return true
        }

        private fun dropSpareWords() {
            while (words > 0) {
                val last = words - 1
                val cut = when {
                    wordIs(last, spareWords) -> last
                    last > 0 && wordIs(last - 1, numberedSpareWords) && wordIsNumber(last) -> last - 1
                    else -> return
                }
                text.setLength(starts[cut])
                words = cut
            }
        }

        fun hasEventWord(): Boolean {
            for (word in 0 until words) {
                if (wordIs(word, eventWords)) return true
                val start = starts[word]
                if (text.length - start > 3 && text[start] == 'p' && text[start + 1] == 'p' && text[start + 2] == 'v') return true
            }
            return false
        }

        // "(2)" or "[2]" is a copy marker; a bracket holding anything else is read as words. Returns the closing bracket's place, or -1.
        private fun copyMarkerEnd(title: String, open: Int): Int {
            val close = if (title[open] == '(') ')' else ']'
            val end = open + 2
            val stop = if (end < title.length && title[end] == close) end else if (end + 1 < title.length && title[end + 1] == close) end + 1 else return -1
            for (i in open + 1 until stop) if (title[i] !in '0'..'9') return -1
            return stop
        }

        private fun onlyDigitsAfter(title: String, at: Int): Boolean {
            if (at + 1 >= title.length) return false
            for (i in at + 1 until title.length) if (title[i] !in '0'..'9') return false
            return true
        }
    }
}
