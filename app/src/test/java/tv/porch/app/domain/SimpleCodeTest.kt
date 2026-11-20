package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.porch.app.domain.SimpleCode.Outcome
import tv.porch.app.domain.SimpleCode.Step

class SimpleCodeTest {
    private fun typed(vararg keys: String) = keys.fold("") { code, key -> SimpleCode.type(code, key) }

    @Test
    fun turningOnNeedsTheSameFourDigitsTwice() {
        // Five presses keep four digits; Delete takes one off.
        assertEquals("1234", typed("1", "2", "3", "4", "5"))
        assertEquals("12", typed("1", "2", "3", SimpleCode.DELETE))
        assertEquals("", typed(SimpleCode.DELETE))

        assertEquals(Outcome.Next(Step.Confirm("2580")), SimpleCode.submit(Step.Choose, "2580", saved = null))
        assertEquals(Outcome.Next(Step.Choose, mismatch = true), SimpleCode.submit(Step.Confirm("2580"), "2581", saved = null))
        assertEquals(Outcome.TurnOn("2580"), SimpleCode.submit(Step.Confirm("2580"), "2580", saved = null))
    }

    @Test
    fun aWrongCodeKeepsSimpleModeOnAndTheRightOneTurnsItOff() {
        assertEquals(Outcome.Wrong, SimpleCode.submit(Step.Unlock, "1111", saved = "2580"))
        assertEquals(Outcome.TurnOff, SimpleCode.submit(Step.Unlock, "2580", saved = "2580"))
        // No saved code (prefs lost) never unlocks by accident with an empty entry.
        assertEquals(Outcome.Wrong, SimpleCode.submit(Step.Unlock, "", saved = null))
    }
}
