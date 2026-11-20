package tv.porch.app.domain

/** Whether a logo would vanish on the light plate: nearly all of what it draws is near white. */
object LogoTone {
    private const val SOLID = 128
    private const val LIGHT = 200
    private const val SHARE = 0.85

    fun needsDarkPlate(pixels: IntArray): Boolean {
        var drawn = 0
        var light = 0
        for (pixel in pixels) {
            if (pixel ushr 24 < SOLID) continue
            drawn++
            val r = pixel shr 16 and 0xFF
            val g = pixel shr 8 and 0xFF
            val b = pixel and 0xFF
            if ((r * 3 + g * 6 + b) / 10 >= LIGHT) light++
        }
        return drawn > 0 && light >= drawn * SHARE
    }
}
