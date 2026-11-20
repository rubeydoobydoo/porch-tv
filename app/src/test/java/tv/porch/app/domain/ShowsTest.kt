package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.porch.app.parse.XtreamEpisode
import tv.porch.app.parse.XtreamSeason

class ShowsTest {
    private fun ep(id: Long, season: Int = 1, number: Int = id.toInt()) =
        XtreamEpisode(id, season, number, "E$id", null, null, null)

    @Test
    fun genresRankDramaThenComedyThenLifestyle() {
        assertEquals(0, Shows.genreRank("Crime Drama"))
        assertEquals(0, Shows.genreRank("POLICE"))
        assertEquals(1, Shows.genreRank("Sitcoms"))
        assertEquals(2, Shows.genreRank("Home and Garden"))
        assertEquals(3, Shows.genreRank("Kids"))
        assertEquals(3, Shows.genreRank("Dramatic")) // whole words only
        assertEquals(0, Shows.genreRank("Comedy Drama")) // first rank wins
    }

    @Test
    fun shelvesAreOrderedByGenreKeepingProviderOrderInside() {
        val cats = listOf("1" to "EN | KIDS", "2" to "EN | SITCOMS", "3" to "EN | LIFESTYLE", "4" to "EN | CRIME DRAMA", "5" to "EN | ANIME")
        assertEquals(
            listOf("CRIME DRAMA", "SITCOMS", "LIFESTYLE", "KIDS", "ANIME"),
            Shows.shelves(cats, "AU").map { it.title.uppercase() },
        )
    }

    // A provider that wraps the label in bars: "|FR| NETFLIX CRIME". The label still decides, and the bars go.
    @Test
    fun aLabelWrappedInBarsIsStillReadAndCleanedOff() {
        val cats = listOf("1" to "|EN| COMEDY SERIES", "2" to "|FR| NETFLIX CRIME | MYSTERE", "3" to "|DE| REALITY TV")
        val shelves = Shows.shelves(cats, "AU")
        assertEquals(listOf("Comedy Series"), shelves.filter { it.chosen }.map { it.title })
        assertEquals("Netflix Crime | Mystere", shelves.single { it.categoryIds == listOf("2") }.title)
    }

    @Test
    fun theSameNameInTwoLanguagesStaysApart() {
        val cats = listOf("1" to "|EN| NETFLIX", "2" to "|FR| NETFLIX", "3" to "|EN| ANIME", "4" to "|FR| ANIME")
        val chosen = Shows.shelves(cats, "AU").filter { it.chosen }
        assertEquals(listOf(listOf("1"), listOf("3")), chosen.map { it.categoryIds })
    }

    @Test
    fun kidsCartoonsAndAnimationShareOneShelf() {
        val cats = listOf(
            "1" to "|EN| DISNEY+ ANIMATION", "2" to "|EN| ENGLISH KIDS", "3" to "|EN| CLASSIC CARTOONS",
            "4" to "|EN| ANIME", "5" to "|FR| NETFLIX KIDS",
        )
        val chosen = Shows.shelves(cats, "AU").filter { it.chosen }
        assertEquals(listOf("Anime", "Kids & cartoons"), chosen.map { it.title }.sorted())
        assertEquals(listOf("1", "2", "3"), chosen.single { it.title == "Kids & cartoons" }.categoryIds)
    }

    @Test
    fun afterWatchKeepsNextClearsAndIgnores() {
        val eps = listOf(ep(1), ep(2), ep(3))
        val min = 60_000L
        assertEquals(ShowProgressChange.Keep(2, 10 * min, 40 * min), Shows.afterWatch(eps, 2, 10 * min, 40 * min))
        assertEquals(ShowProgressChange.Next(eps[2]), Shows.afterWatch(eps, 2, 39 * min, 40 * min))
        assertEquals(ShowProgressChange.Clear, Shows.afterWatch(eps, 3, 39 * min, 40 * min))
        assertEquals(ShowProgressChange.Clear, Shows.afterWatch(eps, 99, 39 * min, 40 * min))
        assertEquals(ShowProgressChange.Ignore, Shows.afterWatch(eps, 2, 90_000L, 40 * min))
        assertEquals(ShowProgressChange.Ignore, Shows.afterWatch(eps, 2, 120_000L, 40 * min))
    }

    @Test
    fun startSeasonFindsTheEpisodeOrFirstSeason() {
        val seasons = listOf(XtreamSeason(2, listOf(ep(21, 2))), XtreamSeason(3, listOf(ep(31, 3))))
        assertEquals(3, Shows.startSeason(seasons, 31))
        assertEquals(2, Shows.startSeason(seasons, 999))
        assertEquals(2, Shows.startSeason(seasons, null))
    }

    @Test
    fun theShowMetaLineLeavesOutWhatIsMissing() {
        assertEquals("Crime \u00b7 2019 \u00b7 3 seasons \u00b7 Rated 7.4", Shows.meta("Crime", 2019, 3, 7.4))
        assertEquals("1 season", Shows.meta(" ", null, 1, null))
        assertEquals("Comedy \u00b7 2 seasons", Shows.meta("Comedy", 0, 2, 0.0))
    }

    @Test
    fun theEpisodeNoteSaysHowFarInOrThatItIsNext() {
        assertEquals("12 min in", Shows.episodeNote(12 * 60_000L))
        assertEquals("Up next", Shows.episodeNote(0L))
    }

    @Test
    fun thePlayerTitleNamesShowSeasonAndEpisode() {
        assertEquals("Flatmates \u00b7 S1 E3 \u00b7 The Leak", Shows.playerTitle("Flatmates", 1, 3, "The Leak"))
    }
}
