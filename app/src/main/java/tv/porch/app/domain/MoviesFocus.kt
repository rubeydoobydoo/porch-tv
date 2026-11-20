package tv.porch.app.domain

/** Where focus first lands on the Movies tab. */
object MoviesFocus {
    enum class Start { Continue, Grid, Chips }

    /** Continue watching first so the shelf stays in view; else the first poster; else the chips. */
    fun start(continueCount: Int, filmCount: Int): Start = when {
        continueCount > 0 -> Start.Continue
        filmCount > 0 -> Start.Grid
        else -> Start.Chips
    }
}
