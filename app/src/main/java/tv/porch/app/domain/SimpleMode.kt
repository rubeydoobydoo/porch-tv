package tv.porch.app.domain

/**
 * Simple mode, for a viewer who only wants their channels: Home is the favourites list, the only other tab is the guide,
 * and leaving the app asks first. The screens only draw what these rules say.
 */
object SimpleMode {
    /** The only tabs simple mode shows, and the only ones it lets open. */
    val TABS = listOf("Home", "Full guide")

    /** The top-bar tabs to show. */
    fun tabs(all: List<String>, simple: Boolean): List<String> = if (simple) all.filter { it in TABS } else all

    /** Whether a tab may open. A screen that forgot to hide a tab still can't leave simple mode through it. */
    fun allows(tab: String, simple: Boolean): Boolean = !simple || tab in TABS

    /** The hold-OK menu. Simple mode can't hide a group (Settings, where it comes back, is out of reach) or drop a channel from the list. */
    fun menuRows(rows: List<QuickAction>, simple: Boolean): List<QuickAction> =
        if (simple) rows - QuickAction.HideGroup - QuickAction.RemoveFavourite else rows

    /** What "What else is on" may offer: in simple mode only the favourites. */
    fun offered(channels: List<Channel>, simple: Boolean): List<Channel> = if (simple) channels.filter { it.favourite } else channels

    /**
     * The zap list for a cold start in simple mode: [lastId] first, then the favourites list in the order the simple Home shows it
     * (one copy of each channel, by number). The channel played last leads even when it is not a favourite, so the start stays put.
     * Returns the ids and the start index (always 0 here, and the last channel is not repeated).
     */
    fun coldStart(channels: List<Channel>, families: ChannelFamilies, lastId: Long): Pair<List<Long>, Int> {
        val favourites = ChannelFold.fold(channels.filter { it.favourite }, families).sortedBy { it.number }.map { it.id }
        val at = favourites.indexOf(lastId)
        return if (at >= 0) favourites to at else (listOf(lastId) + favourites) to 0
    }

    /** What Back on Home does: ask "Leave Porch?", or the usual press-twice warning. */
    enum class HomeBack { AskLeave, BackTwice }

    fun homeBack(simple: Boolean): HomeBack = if (simple) HomeBack.AskLeave else HomeBack.BackTwice

    /** Where Back from the player goes: one step back, but in simple mode always the favourites list, whatever opened it. */
    fun <T> afterPlayer(stack: List<T>, home: T, simple: Boolean): List<T> = if (simple) listOf(home) else Tabs.back(stack, home)

    /** The app's one Back step: [afterPlayer] when the top of [stack] is a player, otherwise the usual step. */
    fun <T> back(stack: List<T>, home: T, simple: Boolean, isPlayer: (T) -> Boolean): List<T> =
        if (stack.lastOrNull()?.let(isPlayer) == true) afterPlayer(stack, home, simple) else Tabs.back(stack, home)
}

/** The 4-digit code that turning simple mode on sets, and leaving it asks for. Digits only, so a D-pad keypad can type it. */
object SimpleCode {
    const val LENGTH = 4
    const val DELETE = "Delete"

    /** The keypad's keys, in reading order: 1 to 9, then 0 and Delete. */
    val KEYS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0", DELETE)

    /** [entered] after pressing [key]: a digit adds one (up to [LENGTH]), Delete takes the last one off. */
    fun type(entered: String, key: String): String = when {
        key == DELETE -> entered.dropLast(1)
        key.length == 1 && key[0].isDigit() && entered.length < LENGTH -> entered + key
        else -> entered
    }

    /** True once [entered] has all its digits. */
    fun complete(entered: String): Boolean = entered.length == LENGTH && entered.all { it.isDigit() }

    /** Which step the code pad is on. */
    sealed interface Step {
        /** Turning simple mode on: choose the code. */
        data object Choose : Step

        /** Turning it on: the same code again, so a slip of the thumb can't lock the TV. */
        data class Confirm(val first: String) : Step

        /** Leaving simple mode: the saved code. */
        data object Unlock : Step
    }

    sealed interface Outcome {
        /** Go on to [step] with an empty entry. [mismatch] says the two codes didn't agree. */
        data class Next(val step: Step, val mismatch: Boolean = false) : Outcome

        /** Both codes agreed: turn simple mode on with [code]. */
        data class TurnOn(val code: String) : Outcome

        /** Not the saved code: clear the entry and say so. Simple mode stays on. */
        data object Wrong : Outcome

        /** The saved code: turn simple mode off. */
        data object TurnOff : Outcome
    }

    /** What a complete [entered] code does on [step]. [saved] is the code simple mode was turned on with. */
    fun submit(step: Step, entered: String, saved: String?): Outcome = when (step) {
        Step.Choose -> if (complete(entered)) Outcome.Next(Step.Confirm(entered)) else Outcome.Next(Step.Choose)
        is Step.Confirm -> if (entered == step.first) Outcome.TurnOn(entered) else Outcome.Next(Step.Choose, mismatch = true)
        Step.Unlock -> if (saved != null && complete(saved) && entered == saved) Outcome.TurnOff else Outcome.Wrong
    }
}

/** Where a cold start opens. */
object StartScreen {
    enum class Start { Setup, Home, LastChannel, Welcome }

    /**
     * No source yet opens Setup. A launcher card opens Home under the card's channel. Otherwise the last channel played
     * opens straight away, but in simple mode a welcome card not yet dismissed comes first.
     */
    fun pick(hasSources: Boolean, hasLastChannel: Boolean, launcherCard: Boolean, simple: Boolean, welcomePending: Boolean): Start = when {
        !hasSources -> Start.Setup
        launcherCard -> Start.Home
        simple && welcomePending -> Start.Welcome
        hasLastChannel -> Start.LastChannel
        else -> Start.Home
    }
}
