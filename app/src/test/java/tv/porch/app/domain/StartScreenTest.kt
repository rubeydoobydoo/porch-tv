package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.porch.app.domain.StartScreen.Start

class StartScreenTest {
    @Test
    fun simpleModeOpensOnTheLastChannelOrTheWelcomeWhileItIsPending() {
        assertEquals(Start.LastChannel, StartScreen.pick(hasSources = true, hasLastChannel = true, launcherCard = false, simple = true, welcomePending = false))
        assertEquals(Start.Welcome, StartScreen.pick(hasSources = true, hasLastChannel = true, launcherCard = false, simple = true, welcomePending = true))
        // Nothing played yet: the favourites list.
        assertEquals(Start.Home, StartScreen.pick(hasSources = true, hasLastChannel = false, launcherCard = false, simple = true, welcomePending = false))
        // Unchanged outside simple mode: a pending welcome means nothing there.
        assertEquals(Start.LastChannel, StartScreen.pick(hasSources = true, hasLastChannel = true, launcherCard = false, simple = false, welcomePending = true))
        assertEquals(Start.Home, StartScreen.pick(hasSources = true, hasLastChannel = true, launcherCard = true, simple = true, welcomePending = false))
        assertEquals(Start.Setup, StartScreen.pick(hasSources = false, hasLastChannel = false, launcherCard = false, simple = true, welcomePending = true))
    }
}
