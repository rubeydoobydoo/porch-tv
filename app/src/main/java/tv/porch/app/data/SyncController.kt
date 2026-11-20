package tv.porch.app.data

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.porch.app.domain.GuideRefresh
import tv.porch.app.domain.SourceProblem
import tv.porch.app.domain.SyncProgress

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data class Running(val progress: SyncProgress) : SyncStatus {
        val message: String get() = progress.line()
    }
    data class Failed(val problem: SourceProblem) : SyncStatus {
        val message: String get() = problem.line
    }
}

class SyncController(
    private val repo: LibraryRepository,
    private val scope: CoroutineScope,
    private val watching: () -> Boolean = { false },
    private val problems: ProblemStore? = null,
) {
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val again = ConcurrentHashMap.newKeySet<Long>()
    private val _status = MutableStateFlow<Map<Long, SyncStatus>>(emptyMap())
    val status: StateFlow<Map<Long, SyncStatus>> = _status

    init {
        // The last failure of each source, so Home still says why there is no guide after a restart.
        val saved = problems?.all().orEmpty()
        if (saved.isNotEmpty()) {
            scope.launch {
                val ids = repo.sources().map { it.id }.toSet()
                saved.forEach { (id, problem) ->
                    if (id !in ids) problems?.clear(id)
                    else _status.update { if (it.containsKey(id)) it else it + (id to SyncStatus.Failed(problem)) }
                }
            }
        }
    }

    /** A removed source says nothing more. A failure left behind would also stop Home saying an import is running. */
    fun forget(sourceId: Long) {
        _status.update { it - sourceId }
        problems?.clear(sourceId)
    }

    /** The typed progress of every running refresh, for screens that want more than [line]. */
    fun progress(status: Map<Long, SyncStatus> = _status.value): Map<Long, SyncProgress> =
        status.mapNotNull { (id, state) -> (state as? SyncStatus.Running)?.let { id to it.progress } }.toMap()

    /** True while a refresh runs and nothing has failed: screens slow their re-draws then. */
    fun importing(): Boolean {
        val all = _status.value.values
        return all.any { it is SyncStatus.Running } && all.none { it is SyncStatus.Failed }
    }

    /**
     * Starts a refresh of one source. While one is already running it does nothing, unless [rerun] is set (an edit was just
     * saved): then one more refresh runs when the current one ends, so the new address is used. The import can't be cut short.
     */
    fun refresh(sourceId: Long, rerun: Boolean = false) {
        val running = jobs[sourceId]
        if (running?.isActive == true) {
            if (rerun && again.add(sourceId)) {
                scope.launch {
                    running.join()
                    again.remove(sourceId)
                    refresh(sourceId)
                }
            }
            return
        }
        jobs[sourceId] = scope.launch {
            // One import runs at a time, so a second source says it is waiting until its turn.
            val waiting = jobs.any { (id, job) -> id != sourceId && job.isActive }
            val first = if (waiting) SyncProgress.Stage.Waiting else SyncProgress.Stage.Channels
            _status.update { it + (sourceId to SyncStatus.Running(SyncProgress(first))) }
            try {
                repo.refresh(sourceId) { progress ->
                    _status.update { it + (sourceId to SyncStatus.Running(progress)) }
                }
                _status.update { it + (sourceId to SyncStatus.Idle) }
                problems?.clear(sourceId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val xtream = runCatching { repo.source(sourceId)?.kind == "xtream" }.getOrDefault(false)
                val problem: SourceProblem = SourceProblems.of(error, xtream)
                problems?.save(sourceId, problem)
                _status.update { it + (sourceId to SyncStatus.Failed(problem)) }
            }
        }
    }

    fun refreshStale() {
        scope.launch { refreshDue() }
    }

    /** Starts every source whose guide is due and returns when they have finished. Returns how many it started. */
    suspend fun refreshDue(): Int {
        var started = 0
        for (source in repo.sources()) {
            // Asked before each source, so pressing play stops the rest of the queue.
            if (!GuideRefresh.due(source.lastSyncAt, System.currentTimeMillis(), watching())) continue
            refresh(source.id)
            jobs[source.id]?.join()
            started++
        }
        return started
    }

    fun line(status: Map<Long, SyncStatus> = _status.value): String? {
        // A failure wins over a refresh still running on another source: the failure is what needs saying.
        status.values.filterIsInstance<SyncStatus.Failed>().firstOrNull()?.let { return it.message }
        status.values.filterIsInstance<SyncStatus.Running>().firstOrNull()?.let { return it.message }
        return null
    }
}
