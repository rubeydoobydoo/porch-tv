package tv.porch.app.ui.theme

import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class PorchTypeTest {
    @Test
    fun headingsUseTheDisplayFaceAndEverythingElseUsesInter() {
        assertNotSame(Inter, Display)
        val headings = mapOf(
            "ScreenTitle" to PorchType.ScreenTitle,
            "PlayerTitle" to PorchType.PlayerTitle,
            "Section" to PorchType.Section,
        )
        for ((name, style) in headings) assertSame("$name should use the display face", Display, style.fontFamily)
        val text = mapOf(
            "RowTitle" to PorchType.RowTitle,
            "TileTitle" to PorchType.TileTitle,
            "Meta" to PorchType.Meta,
            "Body" to PorchType.Body,
            "Hint" to PorchType.Hint,
            "LiveLabel" to PorchType.LiveLabel,
            "Tab" to PorchType.Tab,
        )
        for ((name, style) in text) assertSame("$name should use Inter", Inter, style.fontFamily)
    }
}
