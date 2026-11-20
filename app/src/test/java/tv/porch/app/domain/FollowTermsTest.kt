package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowTermsTest {
    @Test
    fun choicesAreTheNamesInTheTitleThenTheWholeTitle() {
        assertEquals(listOf("Matildas", "England", "Matildas v England"), FollowTerms.choices("Matildas v England"))
        assertEquals(listOf("Bluey"), FollowTerms.choices("Bluey"))
    }

    @Test
    fun aCloseTitleStillMatchesTheFollow() {
        assertTrue(FollowTerms.matches("Matildas", "Matilda's Big Match"))
        assertTrue(FollowTerms.matches("matildas", "Live: Matildas v England"))
        assertFalse(FollowTerms.matches("Matildas", "Harbour Cooking"))
        // Whole words only, and a close spelling only for long words.
        assertFalse(FollowTerms.matches("USA", "Jerusalem"))
        assertFalse(FollowTerms.matches("Live", "Delivery"))
        assertFalse(FollowTerms.matches("Fox", "Box"))
        assertFalse(FollowTerms.matches("Live", "Love"))
        assertTrue(FollowTerms.matches("Live", "Live: Harbour Open"))
    }
}
