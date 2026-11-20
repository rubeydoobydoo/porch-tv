package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackNamesTest {
    @Test
    fun codesBecomeEnglishNamesAndFlagsAppend() {
        assertEquals("English", TrackNames.audio("en", 1))
        assertEquals("Spanish", TrackNames.audio("es", 2))
        assertEquals("Track 3", TrackNames.audio(null, 3))
        assertEquals("Track 4", TrackNames.audio("xx", 4))
        assertEquals("English, audio description", TrackNames.audio("en", 1, description = true))
        assertEquals("English, hard of hearing", TrackNames.subtitle("en", 1, hardOfHearing = true))
    }

    @Test
    fun repeatedNamesGetTheLabelOrANumber() {
        assertEquals(listOf("English", "English 2", "Spanish"), TrackNames.dedupe(listOf("English", "English", "Spanish"), listOf(null, null, null)))
        assertEquals(listOf("English · Stereo", "English · 5.1"), TrackNames.dedupe(listOf("English", "English"), listOf("Stereo", "5.1")))
        assertEquals(listOf("English", "Spanish"), TrackNames.dedupe(listOf("English", "Spanish"), listOf("Main", "Alt")))
    }
}
