package tv.porch.app.parse

import java.io.InputStream

/**
 * Extended M3U parser. Reads line by line, so a large playlist is not held as one string.
 * The title is everything after the first comma that is not inside quotes, so titles may contain commas.
 */
class M3uParser {
    fun parse(input: InputStream): List<ParsedChannel> {
        val out = ArrayList<ParsedChannel>()
        xmlReader(input).buffered().useLines { lines ->
            var pending: PendingExtinf? = null
            for (raw in lines) {
                val line = raw.trim().removePrefix("\uFEFF")
                if (line.isEmpty()) continue
                when {
                    line.startsWith("#EXTINF", ignoreCase = true) -> pending = parseExtinf(line)
                    line.startsWith("#EXTGRP", ignoreCase = true) -> {
                        val group = line.substringAfter(':').trim()
                        if (pending != null && pending.group.isNullOrBlank() && group.isNotEmpty()) {
                            pending = pending.copy(group = group)
                        }
                    }
                    line.startsWith("#") -> Unit
                    pending != null -> {
                        out.add(pending.toChannel(line, out.size + 1))
                        pending = null
                    }
                }
            }
        }
        return out
    }
}

private data class PendingExtinf(
    val title: String,
    val tvgId: String?,
    val tvgName: String?,
    val logo: String?,
    val group: String?,
    val number: Int?,
    val archiveDays: Int,
)

private fun PendingExtinf.toChannel(url: String, position: Int): ParsedChannel {
    val name = title.ifBlank { tvgName?.trim().orEmpty() }.ifBlank { "Channel $position" }
    val groupName = group?.trim()?.ifBlank { null } ?: "Ungrouped"
    return ParsedChannel(
        identity = "m3u:$url",
        name = name,
        number = number ?: position,
        tvgId = tvgId?.trim()?.ifBlank { null },
        tvgName = tvgName?.trim()?.ifBlank { null },
        group = groupName,
        logo = logo?.trim()?.ifBlank { null },
        streamUrl = url,
        xtreamStreamId = null,
        archiveDays = archiveDays.coerceAtLeast(0),
    )
}

private fun parseExtinf(line: String): PendingExtinf {
    val body = line.substringAfter(':')
    val (head, title) = splitExtinf(body)
    val splitAt = head.indexOfFirst { it.isWhitespace() }
    val attrs = if (splitAt < 0) emptyMap() else parseAttributes(head.substring(splitAt + 1))
    val archive = attrs["catchup-days"]?.toIntOrNull()
        ?: attrs["tvg-rec"]?.toDoubleOrNull()?.toInt()
        ?: 0
    return PendingExtinf(
        title = title.trim(),
        tvgId = attrs["tvg-id"],
        tvgName = attrs["tvg-name"],
        logo = attrs["tvg-logo"],
        group = attrs["group-title"],
        number = attrs["tvg-chno"]?.toIntOrNull(),
        archiveDays = archive,
    )
}

/** Split on the first comma that is not inside single or double quotes. */
internal fun splitExtinf(body: String): Pair<String, String> {
    var quote: Char? = null
    for (i in body.indices) {
        val c = body[i]
        if (quote != null) {
            if (c == quote) quote = null
        } else when (c) {
            '"', '\'' -> quote = c
            ',' -> return body.substring(0, i) to body.substring(i + 1)
        }
    }
    return body to ""
}

private val ATTRIBUTE = Regex("""([\w-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""")

internal fun parseAttributes(text: String): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    for (match in ATTRIBUTE.findAll(text)) {
        val key = match.groupValues[1].lowercase()
        val doubleQuoted = match.groups[2]
        val singleQuoted = match.groups[3]
        val value = when {
            doubleQuoted != null -> doubleQuoted.value
            singleQuoted != null -> singleQuoted.value
            else -> ""
        }
        out[key] = value
    }
    return out
}
