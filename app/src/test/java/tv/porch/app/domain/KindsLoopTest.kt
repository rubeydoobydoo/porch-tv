package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class KindsLoopTest {
    @Test
    fun aLoopOfSeriesIsStillMoviesAfterItsTitleLosesThe247() {
        assertEquals(Kind.Movies, Kinds.of("24/7 Example Shows", "Example Group"))
    }

    @Test
    fun aRawNameWith24IsNotMadeMoviesUnlessItIsALoopOfSeries() {
        assertEquals(Kinds.of("Example", "Example Group"), Kinds.of("Example 24 Hours", "Example Group"))
    }

    @Test
    fun aGroupNamed247SeriesStillMakesItsChannelsMovies() {
        assertEquals(Kind.Movies, Kinds.of("Example", "24/7 Series"))
    }
}
