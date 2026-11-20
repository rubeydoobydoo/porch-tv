package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupGuideDefaultsTest {
    @Test
    fun aTvThatNeverChangedItsBackupsGetsTheFreeToAirGuide() {
        assertEquals(listOf(BackupGuideDefaults.AU_FREE_TO_AIR), BackupGuideDefaults.addresses(null))
        // Someone who removed every backup, or added their own, keeps that choice.
        assertEquals(emptyList<String>(), BackupGuideDefaults.addresses(""))
        assertEquals(listOf("http://example.com/a.xml"), BackupGuideDefaults.addresses("http://example.com/a.xml"))
    }
}
