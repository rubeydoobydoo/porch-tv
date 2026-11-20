package tv.porch.app.domain

/** What Watch on a reminder card does. A player already on screen changes channel in place, which is much quicker than opening a new one. */
object ReminderWatch {
    enum class Action { Tune, Open }

    fun action(playerOnTop: Boolean): Action = if (playerOnTop) Action.Tune else Action.Open
}
