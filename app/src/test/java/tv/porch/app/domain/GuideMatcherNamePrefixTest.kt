package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuideMatcherNamePrefixTest {
    private val guide = listOf(MatchableGuide("redfield.sport", listOf("Redfield Sport HD")))

    private fun match(name: String) =
        GuideMatcher.match(listOf(MatchableChannel("1", null, null, name)), guide)["1"]

    @Test
    fun aProviderLabelBeforeTheNameDoesNotStopTheMatch() {
        assertEquals("redfield.sport", match("US| REDFIELD SPORT"))
        assertEquals("redfield.sport", match("TV: Redfield Sport"))
    }

    @Test
    fun aDifferentChannelAfterTheLabelStillDoesNotMatch() {
        assertNull(match("US| Redfield Sporting Replay Two"))
    }
}
