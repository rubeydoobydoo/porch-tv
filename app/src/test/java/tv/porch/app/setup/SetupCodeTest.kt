package tv.porch.app.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class SetupCodeTest {
    @Test
    fun onlyTheAddressOnTheTvOpensThePage() {
        assertTrue(SetupCode.allows("/k7m2xq/", "k7m2xq"))
        assertTrue(SetupCode.allows("/k7m2xq", "k7m2xq"))
        listOf("/", "", "/guides", "/k7m2xp/", "/k7m2xq/extra", "/K7M2XQ/", "//k7m2xq/").forEach {
            assertFalse(it, SetupCode.allows(it, "k7m2xq"))
        }
    }

    @Test
    fun aNewCodeIsEasyToTypeAndChangesEachTime() {
        val random = Random(7)
        val first = SetupCode.generate(random)
        assertEquals(6, first.length)
        assertTrue(first, first.all { it in "abcdefghjkmnpqrstuvwxyz23456789" })
        assertFalse(first == SetupCode.generate(random))
    }
}
