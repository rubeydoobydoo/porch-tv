package tv.porch.app.domain

/** The words that say what is chosen, so a choice never rests on colour alone. */
object ChoiceLabels {
    fun chosen(label: String, selected: Boolean): String = if (selected) "✓ $label" else label

    fun group(hidden: Boolean): String = if (hidden) "Hidden" else "Showing"
}
