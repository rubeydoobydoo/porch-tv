package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class KindsTest {
    @Test
    fun namesLandInTheKindTheirWordsSay() {
        val table = listOf(
            Triple("Sky Sports Main Event", "UK| SPORTS", Kind.Sport),
            Triple("CNN International", "US| NEWS", Kind.News),
            Triple("Sky Sports News", "", Kind.Sport),
            Triple("Cartoon Network", "US| KIDS", Kind.Kids),
            Triple("Kids Movies", "", Kind.Kids),
            Triple("HBO 2", "US| MOVIES", Kind.Movies),
            Triple("Discovery Channel", "", Kind.Docs),
            Triple("MTV Hits", "", Kind.Music),
            Triple("Harbour News 24", "General", Kind.News),
            Triple("Channel 204", "General", null),
            Triple("24/7 Series", "", Kind.Movies),
            Triple("ESPN+ 2", "", Kind.Sport),
            Triple("Disney+ Kids", "", Kind.Kids),
            // Cues are whole words: "kid" is not in "Kidney", "nick" is not in "Nickel", "f1" is not in "F12".
            Triple("Whitsunday Kidney F12", "Nickel", null),
        )
        for ((name, group, kind) in table) {
            assertEquals("$name / $group", kind, Kinds.of(name, group))
        }
    }
}
