package tv.porch.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import tv.porch.app.domain.Channel
import tv.porch.app.domain.ChannelFamilies
import tv.porch.app.domain.ChannelFold

/** The shared channel list with its families worked out. They arrive together, so a screen never folds a list with an older list's families. */
class FoldedList(val channels: List<Channel>, val families: ChannelFamilies)

/** One copy of the families for every screen, worked out off the main thread once per channel list or switch change. */
class ChannelFolds(repository: LibraryRepository, flags: GuideFlags) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Kept through a short gap between screens, then dropped: a saved answer could be for the old list or switch.
    val listing: SharedFlow<FoldedList> by lazy {
        combine(repository.observeChannels(), flags.combineCopies) { channels, on ->
            FoldedList(channels, ChannelFold.familiesInParallel(channels, on))
        }.flowOn(Dispatchers.Default)
            .shareIn(scope, SharingStarted.WhileSubscribed(30_000, 0), replay = 1)
    }

    /** The list on hand right now, or null when none has been worked out yet (or it was dropped after the gap). Never waits. */
    fun now(): FoldedList? = listing.replayCache.lastOrNull()
}
