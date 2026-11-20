package tv.porch.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyTextTest {
    @Test
    fun aMistypedWordStillFindsTheShow() {
        assertTrue(FuzzyText.matches("matlidas", "Matildas v England"))
        assertTrue(FuzzyText.matches("footbal", "College Football"))
        assertTrue(FuzzyText.matches("colege footbal", "College Football: Ole Miss"))
    }

    @Test
    fun aDifferentWordDoesNotMatch() {
        assertFalse(FuzzyText.matches("matlidas", "Maltese Falcon"))
        assertFalse(FuzzyText.matches("news", "Nine Gem"))
        assertFalse(FuzzyText.worthTrying("nba"))
    }
}
