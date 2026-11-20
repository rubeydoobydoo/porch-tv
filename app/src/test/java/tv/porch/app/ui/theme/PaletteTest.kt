package tv.porch.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteTest {
    private fun contrast(a: Color, b: Color): Float {
        val hi = maxOf(a.luminance(), b.luminance())
        val lo = minOf(a.luminance(), b.luminance())
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @After
    fun backToDark() {
        PorchColors.palette = DarkPalette
    }

    @Test
    fun darkIsStillTheTwoNovemberLook() {
        assertEquals(Color(0xFF08090C), DarkPalette.Bg)
        assertEquals(Color(0xFFF5F3EE), DarkPalette.Focus)
        assertEquals(Color(0xFFF5A524), DarkPalette.Accent)
        assertEquals(Color(0xFFE23B3B), DarkPalette.Live)
    }

    @Test
    fun everyLinenWordCanBeReadFromTheCouch() {
        val p = LinenPalette
        listOf(
            p.Text to p.Bg, p.TextSecondary to p.Bg, p.Muted to p.Bg, p.Muted to p.Surface, p.Faint to p.Bg, p.Accent to p.Bg,
            p.LiveOnDark to p.Bg, p.OnFocus to p.Focus, p.LiveOnLight to p.Focus, p.Text to p.GuideElapsed, PorchColors.OnPlate to p.LogoPlate,
        ).forEach { (ink, ground) -> assertTrue("$ink on $ground", contrast(ink, ground) >= 4.5f) }
    }

    @Test
    fun choosingLinenChangesWhatTheScreensDraw() {
        PorchColors.palette = LinenPalette
        assertEquals(LinenPalette.Bg, PorchColors.Bg)
        assertEquals(LinenPalette.Muted, PorchType.Meta.color)
        assertEquals(LinenPalette.Bg.copy(alpha = 0.60f), PorchColors.Dim)
    }
}
