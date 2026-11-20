package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuideMatcherTest {
    private val guide = listOf(
        MatchableGuide("redfield.sport", listOf("Redfield Sport")),
        MatchableGuide("harbor.news", listOf("Harbor News HD")),
        MatchableGuide("north.a", listOf("North Harbor Live")),
        MatchableGuide("north.b", listOf("North Harbor Livz")),
        MatchableGuide("west.pier", listOf("West Pier Cinema")),
    )

    @Test
    fun idWinsEvenWhenTheNameDiffers() {
        val matched = GuideMatcher.match(
            listOf(MatchableChannel("1", "Redfield.Sport", "Something Else", "Something Else")),
            guide,
        )
        assertEquals("redfield.sport", matched["1"])
    }

    @Test
    fun missingIdFallsThroughToTheCleanedName() {
        val matched = GuideMatcher.match(
            listOf(MatchableChannel("1", "not-in-the-guide", null, "Harbor News FHD")),
            guide,
        )
        assertEquals("harbor.news", matched["1"])
    }

    @Test
    fun fuzzyNameMatchesAndAmbiguousNamesDoNot() {
        val matched = GuideMatcher.match(
            listOf(
                MatchableChannel("close", null, null, "Redfield Sports"),
                MatchableChannel("short", null, null, "News"),
                MatchableChannel("tie", null, null, "North Harbor Liv"),
            ),
            guide,
        )
        assertEquals("redfield.sport", matched["close"])
        assertNull(matched["short"])
        assertNull(matched["tie"])
    }

    @Test
    fun exactNameBeatsAFuzzyNeighbour() {
        val matched = GuideMatcher.match(
            listOf(MatchableChannel("1", null, "West Pier Cinema", "West Pier Cinemas")),
            guide,
        )
        assertEquals("west.pier", matched["1"])
    }
}
