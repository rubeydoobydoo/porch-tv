package tv.porch.app.parse

import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * XMLTV timestamps look like `20261025180000 +0000`.
 * A value with no zone is treated as UTC. That assumption is documented in the README.
 */
fun parseXmltvTime(raw: String): Long? {
    val text = raw.trim()
    if (text.length < 14) return null
    val stamp = text.substring(0, 14)
    if (stamp.any { !it.isDigit() }) return null
    return try {
        val local = LocalDateTime.parse(stamp, STAMP)
        val offset = if (text.length > 14) parseZone(text.substring(14).trim()) else ZoneOffset.UTC
        local.toInstant(offset).toEpochMilli()
    } catch (_: DateTimeException) {
        null
    }
}

private fun parseZone(raw: String): ZoneOffset {
    val text = raw.replace(":", "")
    if (text.length < 5 || (text[0] != '+' && text[0] != '-')) return ZoneOffset.UTC
    val sign = if (text[0] == '-') -1 else 1
    val hours = text.substring(1, 3).toIntOrNull() ?: return ZoneOffset.UTC
    val minutes = text.substring(3, 5).toIntOrNull() ?: return ZoneOffset.UTC
    if (hours > 18 || minutes > 59) return ZoneOffset.UTC
    return ZoneOffset.ofHoursMinutes(sign * hours, sign * minutes)
}

private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
