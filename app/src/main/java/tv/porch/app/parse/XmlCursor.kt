package tv.porch.app.parse

/**
 * A small streaming reader for the slice of XML that XMLTV actually uses.
 * It never builds a document tree. It does not fetch external DTDs.
 */
internal class XmlCursor(private val reader: java.io.Reader) {
    // Pushed-back chars as a plain int stack: no boxing. peekN pushes back at most a few.
    private val unread = IntArray(32)
    private var unreadSize = 0

    // One builder reused for text, names and attribute values; always copied out with toString().
    private val sb = StringBuilder()

    // Chars come out of our own block, because a call into the reader for each char is slow on a TV box.
    private val block = CharArray(64 * 1024)
    private var blockPos = 0
    private var blockLen = 0

    init {
        val first = nextChar()
        if (first >= 0 && first != 0xFEFF) unread(first)
    }

    fun next(): XmlEvent? {
        while (true) {
            val c = read()
            if (c < 0) return null
            if (c == '<'.code) {
                val event = readTag()
                if (event != null) return event
                continue
            }
            unread(c)
            val text = readText()
            if (text.isNotBlank()) return XmlEvent.Text(decodeXml(text))
        }
    }

    private fun readTag(): XmlEvent? {
        val c = peek()
        if (c < 0) return null
        if (c == '!'.code) {
            read()
            return readSpecial()
        }
        if (c == '?'.code) {
            read()
            skipUntil("?>")
            return null
        }
        if (c == '/'.code) {
            read()
            skipSpace()
            val name = readName()
            skipSpace()
            if (peek() == '>'.code) read()
            return XmlEvent.End(name)
        }
        return readStart()
    }

    private fun readSpecial(): XmlEvent? {
        if (peek() == '-'.code) {
            skipUntil("-->")
            return null
        }
        if (peekN(7) == "[CDATA[") {
            repeat(7) { read() }
            return XmlEvent.Text(readUntil("]]>"))
        }
        skipDeclaration()
        return null
    }

    private fun readStart(): XmlEvent {
        val name = readName()
        val attrs = LinkedHashMap<String, String>()
        while (true) {
            skipSpace()
            val c = peek()
            if (c < 0) return XmlEvent.Start(name, attrs, empty = false)
            if (c == '>'.code) {
                read()
                return XmlEvent.Start(name, attrs, empty = false)
            }
            if (c == '/'.code) {
                read()
                if (peek() == '>'.code) read()
                return XmlEvent.Start(name, attrs, empty = true)
            }
            val key = readName()
            if (key.isEmpty()) {
                read()
                continue
            }
            skipSpace()
            if (peek() == '='.code) {
                read()
                attrs[key] = decodeXml(readQuoted())
            }
        }
    }

    private fun readText(): String {
        sb.setLength(0)
        while (true) {
            val c = peek()
            if (c < 0 || c == '<'.code) break
            sb.append(read().toChar())
        }
        return sb.toString()
    }

    private fun readQuoted(): String {
        skipSpace()
        val q = peek()
        sb.setLength(0)
        if (q != '"'.code && q != '\''.code) {
            while (true) {
                val c = peek()
                if (c < 0 || c <= ' '.code || c == '>'.code || c == '/'.code) break
                sb.append(read().toChar())
            }
            return sb.toString()
        }
        read()
        while (true) {
            val c = read()
            if (c < 0 || c == q) break
            sb.append(c.toChar())
        }
        return sb.toString()
    }

    private fun readName(): String {
        sb.setLength(0)
        while (true) {
            val c = peek()
            if (c < 0) break
            val ch = c.toChar()
            if (ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == ':' || ch == '.') {
                sb.append(ch)
                read()
            } else {
                break
            }
        }
        return sb.toString()
    }

    private fun skipSpace() {
        while (true) {
            val c = peek()
            if (c == ' '.code || c == '\n'.code || c == '\r'.code || c == '\t'.code) read() else break
        }
    }

    private fun skipDeclaration() {
        var quote = 0
        var bracket = 0
        while (true) {
            val c = read()
            if (c < 0) return
            if (quote != 0) {
                if (c == quote) quote = 0
                continue
            }
            when (c) {
                '"'.code, '\''.code -> quote = c
                '['.code -> bracket++
                ']'.code -> if (bracket > 0) bracket--
                '>'.code -> if (bracket == 0) return
            }
        }
    }

    private fun skipUntil(marker: String) {
        readUntil(marker)
    }

    private fun readUntil(marker: String): String {
        sb.setLength(0)
        var matched = 0
        while (true) {
            val c = read()
            if (c < 0) break
            if (c == marker[matched].code) {
                matched++
                if (matched == marker.length) break
            } else {
                if (matched > 0) {
                    sb.append(marker, 0, matched)
                    matched = 0
                    if (c == marker[0].code) {
                        matched = 1
                        continue
                    }
                }
                sb.append(c.toChar())
            }
        }
        return sb.toString()
    }

    /** Moves past [marker] without keeping anything. [marker] starts with a char it doesn't repeat. */
    fun skipPast(marker: String) {
        var matched = 0
        while (true) {
            val c = read()
            if (c < 0) return
            if (c == marker[matched].code) {
                matched++
                if (matched == marker.length) return
            } else {
                matched = if (c == marker[0].code) 1 else 0
            }
        }
    }

    private fun peekN(count: Int): String {
        val chars = IntArray(count)
        var n = 0
        while (n < count) {
            val c = read()
            if (c < 0) break
            chars[n++] = c
        }
        for (i in n - 1 downTo 0) unread(chars[i])
        return buildString(n) { for (i in 0 until n) append(chars[i].toChar()) }
    }

    private fun peek(): Int {
        val c = read()
        if (c >= 0) unread(c)
        return c
    }

    private fun read(): Int = if (unreadSize == 0) nextChar() else unread[--unreadSize]

    private fun nextChar(): Int {
        if (blockPos >= blockLen) {
            blockLen = reader.read(block)
            blockPos = 0
            if (blockLen <= 0) {
                blockLen = 0
                return -1
            }
        }
        return block[blockPos++].code
    }

    private fun unread(c: Int) {
        check(unreadSize < unread.size) { "Pushback is full (${unread.size} chars)" }
        unread[unreadSize++] = c
    }
}

internal sealed interface XmlEvent {
    data class Start(val name: String, val attrs: Map<String, String>, val empty: Boolean) : XmlEvent
    data class End(val name: String) : XmlEvent
    data class Text(val text: String) : XmlEvent
}

internal fun decodeXml(raw: String): String {
    if ('&' !in raw) return raw
    val sb = StringBuilder(raw.length)
    var i = 0
    while (i < raw.length) {
        if (raw[i] != '&') {
            sb.append(raw[i])
            i++
            continue
        }
        val semi = raw.indexOf(';', i + 1)
        if (semi < 0 || semi - i > 12) {
            sb.append('&')
            i++
            continue
        }
        val token = raw.substring(i + 1, semi)
        val ch = when (token) {
            "amp" -> '&'
            "lt" -> '<'
            "gt" -> '>'
            "quot" -> '"'
            "apos" -> '\''
            else -> when {
                token.startsWith("#x") || token.startsWith("#X") -> token.drop(2).toIntOrNull(16)?.toChar()
                token.startsWith("#") -> token.drop(1).toIntOrNull()?.toChar()
                else -> null
            }
        }
        if (ch == null) {
            sb.append('&')
            i++
        } else {
            sb.append(ch)
            i = semi + 1
        }
    }
    return sb.toString()
}
