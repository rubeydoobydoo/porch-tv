package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchKeysTest {
    @Test
    fun gridTypesAndEditsTheQuery() {
        // Letters come out lower case, as the field shows them; digits are kept.
        assertEquals("h", SearchKeys.apply("", "H"))
        assertEquals("harb", SearchKeys.apply("har", "B"))
        assertEquals("ch 4", SearchKeys.apply("ch ", "4"))
        // Space: never first, never doubled.
        assertEquals("", SearchKeys.apply("", SearchKeys.SPACE))
        assertEquals("harb ", SearchKeys.apply("harb ", SearchKeys.SPACE))
        assertEquals("harb ", SearchKeys.apply("harb", SearchKeys.SPACE))
        // Delete removes one character; nothing to remove is fine.
        assertEquals("har", SearchKeys.apply("harb", SearchKeys.DELETE))
        assertEquals("", SearchKeys.apply("", SearchKeys.DELETE))
        // Clear empties the field.
        assertEquals("", SearchKeys.apply("harbour news", SearchKeys.CLEAR))
        // The field holds 40 characters at most.
        val full = "a".repeat(40)
        assertEquals(full, SearchKeys.apply(full, "B"))
    }

    @Test
    fun laterResultsSayTodayTonightTomorrowOrTheDay() {
        val zone = java.time.ZoneId.of("UTC")
        fun at(day: Int, hour: Int, minute: Int) =
            java.time.ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()
        val morning = at(29, 11, 45)
        // Today before 6 pm is "today"; from 6 pm it is "tonight".
        assertEquals("12:34 pm today", formatLaterStart(at(29, 12, 34), morning, zone))
        assertEquals("5:59 pm today", formatLaterStart(at(29, 17, 59), morning, zone))
        assertEquals("6:00 pm tonight", formatLaterStart(at(29, 18, 0), morning, zone))
        assertEquals("11:00 pm tonight", formatLaterStart(at(29, 23, 0), at(29, 20, 42), zone))
        // Tomorrow, then the weekday.
        assertEquals("tomorrow 9:00 am", formatLaterStart(at(30, 9, 0), morning, zone))
        assertEquals("Sat 7:30 pm", formatLaterStart(at(31, 19, 30), morning, zone))
    }

    @Test
    fun searchingUntilTheAnswerIsForThisQuery() {
        assertEquals(true, SearchKeys.isSearching("harb", "har"))
        assertEquals(false, SearchKeys.isSearching("harb", "harb"))
        assertEquals(false, SearchKeys.isSearching("", "x"))
        assertEquals(true, SearchKeys.isSearching("harb", null))
    }
}
