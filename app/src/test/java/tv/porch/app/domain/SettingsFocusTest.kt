package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsFocusTest {
    @Test
    fun aFreshVisitStartsOnChannelsEvenIfARowWasSavedEarlier() {
        assertEquals("channels", SettingsFocus.startRow("source:7", comingBack = false))
        assertEquals("channels", SettingsFocus.startRow(null, comingBack = false))
    }

    @Test
    fun comingBackFromASubScreenLandsOnTheRowYouLeft() {
        assertEquals("source:7", SettingsFocus.startRow("source:7", comingBack = true))
    }

    @Test
    fun comingBackWithNothingSavedStartsOnChannels() {
        assertEquals("channels", SettingsFocus.startRow(null, comingBack = true))
    }
}
