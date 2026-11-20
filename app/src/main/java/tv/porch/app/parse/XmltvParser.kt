package tv.porch.app.parse

import java.io.BufferedInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.zip.GZIPInputStream
import tv.porch.app.domain.LogoAddress

interface XmltvSink {
    fun onChannels(channels: List<ParsedGuideChannel>)
    fun onProgrammes(batch: List<ParsedProgramme>)
}

/**
 * Streaming XMLTV parser. Programme batches are handed to [sink] and not retained,
 * so a guide with hundreds of thousands of programmes does not sit in memory.
 * [wanted], when given, is asked about each programme's channel. A programme it turns down is skipped
 * without being read, which is most of the work for a big file where few channels matter.
 */
class XmltvParser {
    fun parse(input: InputStream, batchSize: Int = 400, sink: XmltvSink, wanted: ((String) -> Boolean)? = null) {
        val cursor = XmlCursor(xmlReader(input))
        val channels = mutableListOf<ParsedGuideChannel>()
        val batch = ArrayList<ParsedProgramme>(batchSize)
        var inChannel = false
        var channelId: String? = null
        val names = mutableListOf<String>()
        var programme: ProgrammeBuild? = null
        var skipProgramme = false
        var textTarget: String? = null
        val text = StringBuilder()

        fun flushText() {
            val target = textTarget ?: return
            val value = text.toString().trim()
            text.setLength(0)
            textTarget = null
            when (target) {
                "name" -> if (value.isNotEmpty() && names.size < 8) names.add(value)
                "title" -> if (programme?.title == null && value.isNotEmpty()) {
                    programme?.title = value.take(300)
                }
                "desc" -> if (programme?.description == null && value.isNotEmpty()) {
                    programme?.description = value.take(2_000)
                }
                "category" -> if (programme?.category == null && value.isNotEmpty()) {
                    programme?.category = value.take(80)
                }
            }
        }

        fun handleStart(event: XmlEvent.Start) {
            when (event.name) {
                "channel" -> {
                    flushText()
                    inChannel = true
                    channelId = event.attrs["id"]?.trim()
                    names.clear()
                }
                "display-name" -> if (inChannel) {
                    flushText()
                    textTarget = "name"
                }
                "programme" -> {
                    flushText()
                    // The channel list comes first in XMLTV. Hand it over before any programme so
                    // the caller can save channels while the programmes are still streaming in.
                    if (channels.isNotEmpty()) {
                        sink.onChannels(channels.toList())
                        channels.clear()
                    }
                    val channel = event.attrs["channel"]?.trim().orEmpty()
                    if (wanted != null && !wanted(channel)) {
                        programme = null
                        skipProgramme = !event.empty
                        return
                    }
                    programme = ProgrammeBuild(
                        channelKey = channel,
                        startUtc = event.attrs["start"]?.let(::parseXmltvTime),
                        stopUtc = event.attrs["stop"]?.let(::parseXmltvTime),
                    )
                }
                "title" -> if (programme != null && programme?.title == null) {
                    flushText()
                    textTarget = "title"
                }
                "desc", "description" -> if (programme != null && programme?.description == null) {
                    flushText()
                    textTarget = "desc"
                }
                "category" -> if (programme != null && programme?.category == null) {
                    flushText()
                    textTarget = "category"
                }
                // Only inside a programme: a channel's own icon comes while no programme is open.
                "icon" -> if (!inChannel && programme != null && programme?.iconUrl == null) {
                    programme?.iconUrl = LogoAddress.clean(event.attrs["src"])?.takeIf { it.length <= MAX_ICON_ADDRESS }
                }
            }
        }

        fun handleEnd(name: String) {
            val closes = when (textTarget) {
                "name" -> name == "display-name"
                "title" -> name == "title"
                "desc" -> name == "desc" || name == "description"
                "category" -> name == "category"
                else -> false
            }
            if (closes) flushText()
            when (name) {
                "channel" -> {
                    val id = channelId
                    if (!id.isNullOrEmpty() && channels.size < 100_000) {
                        channels.add(ParsedGuideChannel(id, names.toList()))
                    }
                    inChannel = false
                    channelId = null
                    names.clear()
                }
                "programme" -> {
                    val built = programme
                    programme = null
                    if (built != null &&
                        built.channelKey.isNotEmpty() &&
                        !built.title.isNullOrBlank() &&
                        built.startUtc != null &&
                        built.stopUtc != null &&
                        built.stopUtc > built.startUtc
                    ) {
                        batch.add(
                            ParsedProgramme(
                                channelKey = built.channelKey,
                                title = built.title!!.trim(),
                                description = built.description?.trim()?.ifBlank { null },
                                startUtc = built.startUtc,
                                stopUtc = built.stopUtc,
                                category = built.category?.trim()?.ifBlank { null },
                                iconUrl = built.iconUrl,
                            ),
                        )
                        if (batch.size >= batchSize) {
                            sink.onProgrammes(batch.toList())
                            batch.clear()
                        }
                    }
                }
            }
        }

        while (true) {
            when (val event = cursor.next()) {
                null -> break
                is XmlEvent.Start -> {
                    handleStart(event)
                    if (skipProgramme) {
                        skipProgramme = false
                        cursor.skipPast("</programme") // XML allows a space before the bracket.
                        cursor.skipPast(">")
                    } else if (event.empty) {
                        handleEnd(event.name)
                    }
                }
                is XmlEvent.End -> handleEnd(event.name)
                is XmlEvent.Text -> if (textTarget != null) text.append(event.text)
            }
        }
        if (channels.isNotEmpty()) sink.onChannels(channels)
        if (batch.isNotEmpty()) sink.onProgrammes(batch.toList())
    }
}

private class ProgrammeBuild(
    val channelKey: String,
    val startUtc: Long?,
    val stopUtc: Long?,
    var title: String? = null,
    var description: String? = null,
    var category: String? = null,
    var iconUrl: String? = null,
)

/** Longer than this is not a real picture address. It is dropped, not cut, because a cut address leads nowhere. */
private const val MAX_ICON_ADDRESS = 500

internal fun xmlReader(input: InputStream): InputStreamReader {
    val buffered = unzipIfGzip(input)
    if (!buffered.markSupported()) return InputStreamReader(buffered, Charsets.UTF_8)
    buffered.mark(900)
    val head = ByteArray(900)
    val count = buffered.read(head)
    buffered.reset()
    val charset = charsetFromXmlDeclaration(if (count > 0) head.copyOf(count) else ByteArray(0))
    return InputStreamReader(buffered, charset)
}

/** Guides are often gzipped whatever the file is called, so look at the first two bytes, not the name. */
internal fun unzipIfGzip(input: InputStream): InputStream {
    val buffered = if (input.markSupported()) input else BufferedInputStream(input)
    buffered.mark(2)
    val first = buffered.read()
    val second = buffered.read()
    buffered.reset()
    return if (first == 0x1f && second == 0x8b) BufferedInputStream(GZIPInputStream(buffered, 64 * 1024)) else buffered
}

private fun charsetFromXmlDeclaration(head: ByteArray): Charset {
    val latin = head.toString(Charsets.ISO_8859_1)
    val match = ENCODING.find(latin)?.groupValues?.get(1)?.lowercase() ?: return Charsets.UTF_8
    return when (match) {
        "utf-8", "utf8" -> Charsets.UTF_8
        "iso-8859-1", "latin1", "iso-8859-15", "windows-1252" -> Charsets.ISO_8859_1
        else -> Charsets.UTF_8
    }
}

private val ENCODING = Regex("""encoding\s*=\s*["']([A-Za-z0-9_\-]+)["']""")
