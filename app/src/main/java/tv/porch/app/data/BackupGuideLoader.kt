package tv.porch.app.data

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import tv.porch.app.domain.BackupChooser
import tv.porch.app.domain.BackupGuide
import tv.porch.app.domain.BackupPlan
import tv.porch.app.domain.BackupResult
import tv.porch.app.domain.GuideRules
import tv.porch.app.domain.MatchableChannel
import tv.porch.app.domain.MatchableGuide
import tv.porch.app.domain.SyncProgress
import tv.porch.app.parse.ParsedGuideChannel
import tv.porch.app.parse.ParsedProgramme
import tv.porch.app.parse.XmltvParser
import tv.porch.app.parse.XmltvSink

/** New backup rows wait here, where no query can see them. Anything left by a crash goes at the next refresh. */
private const val STAGING_GENERATION = -1L

/** Backup programmes saved per insert. Only wanted rows count, and each insert wakes every screen's programme query. */
private const val BACKUP_BATCH = 2_000

/**
 * Reads the viewer's outside guide files after the provider's guide step. Nothing here can fail a refresh
 * or touch the provider's programmes. Exceptions are counted, never logged: their text can name the address.
 *
 * New rows wait out of sight until every file has been tried, then go live in one transaction with their matches.
 * So a dead address, an empty file or a crash leaves the working backup as it was.
 */
class BackupGuideLoader(
    private val db: PorchDatabase,
    private val http: HttpFetcher,
    private val cacheDir: File,
    private val store: BackupGuides,
) {
    /** Backup rows in [generation], which the provider's progress count must leave out. No query unless the source has any. */
    fun rowsIn(sourceId: Long, generation: Long): Int =
        if (store.hasRows(sourceId)) db.programmes().countBackupRows(sourceId, generation) else 0

    /** Call in the transaction that makes the provider's new guide live, so its old-generation delete spares working backups. */
    fun carryForward(sourceId: Long, from: Long, to: Long) {
        if (store.hasRows(sourceId)) db.programmes().moveBackupRows(sourceId, from, to)
    }

    /**
     * [providerOk] says whether the provider's guide worked on this refresh. Rows go live under the generation the
     * source shows now, so they are visible even when no provider guide has ever worked (generation 0 joins too).
     */
    suspend fun load(
        sourceId: Long,
        channels: List<MatchableChannel>,
        providerOk: Boolean,
        progress: (SyncProgress) -> Unit,
    ) {
        val addresses = store.addresses.value
        if (addresses.isEmpty() && !store.hasRows(sourceId)) return
        var loaded = 0
        var failed = 0
        try {
            val source = db.sources().get(sourceId) ?: return
            val generation = source.guideGeneration
            db.programmes().deleteGeneration(sourceId, STAGING_GENERATION)
            val rows = db.channels().matchRows(sourceId)
            if (addresses.isEmpty()) {
                // Remove all: the old backup goes once, then refreshes go back to doing nothing here.
                commit(sourceId, generation, rows, BackupPlan(), clean = true)
                store.setHasRows(sourceId, false)
                return
            }
            val offset = source.guideOffsetMinutes * 60_000L
            val keysWithFuture = if (providerOk) {
                emptySet()
            } else {
                db.programmes().keysEndingAfter(sourceId, generation, System.currentTimeMillis() - offset).toHashSet()
            }
            val provider = HashMap<String, String>(rows.size)
            rows.forEach { row -> row.matchedXmltvId?.let { provider[row.identity] = it } }
            val run = Run(sourceId, offset, addresses.size, BackupChooser(channels, provider, providerOk, keysWithFuture), progress)
            addresses.forEachIndexed { index, address ->
                currentCoroutineContext().ensureActive()
                try {
                    run.read(address, index + 1)
                    loaded++
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    failed++
                }
            }
            progress(SyncProgress(SyncProgress.Stage.Guide, backupLine = BackupGuide.SAVING_LINE))
            commit(sourceId, generation, rows, run.plan, BackupGuide.cleanRun(failed, run.plan))
            store.record(BackupResult(System.currentTimeMillis(), run.plan.assigned.size, loaded, failed))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            store.record(BackupResult(System.currentTimeMillis(), 0, 0, addresses.size))
        }
    }

    /** The swap, all at once. Matches change in place, as the provider's late match does, so ids and favourites survive. */
    private fun commit(sourceId: Long, generation: Long, rows: List<ChannelMatchRow>, plan: BackupPlan, clean: Boolean) {
        db.runInTransaction {
            if (db.sources().get(sourceId) == null) {
                db.programmes().deleteGeneration(sourceId, STAGING_GENERATION)
                return@runInTransaction
            }
            val inUse = HashSet<String>()
            val changes = ArrayList<Pair<Long, String?>>()
            rows.forEach { row ->
                val next = BackupGuide.finalMatch(row.matchedXmltvId, plan.assigned[row.identity], clean)
                if (BackupGuide.isBackupKey(next)) inUse.add(checkNotNull(next))
                if (next != row.matchedXmltvId) changes.add(row.id to next)
            }
            val staged = plan.assigned.values.toSet()
            BackupGuide.oldKeysToDelete(db.programmes().backupKeys(sourceId, generation), staged, inUse)
                .chunked(400)
                .forEach { db.programmes().deleteKeys(sourceId, generation, it) }
            db.programmes().moveGeneration(sourceId, STAGING_GENERATION, generation)
            changes.forEach { (id, next) -> db.channels().setMatched(id, next) }
        }
    }

    private inner class Run(
        private val sourceId: Long,
        private val offset: Long,
        private val files: Int,
        private val chooser: BackupChooser,
        private val progress: (SyncProgress) -> Unit,
    ) {
        var plan = BackupPlan()
            private set
        private val written = HashSet<String>()
        private var count = 0
        private var marked = false

        /** One file, all or nothing: a failure takes back what it added, so the next file can claim those ids. */
        fun read(address: String, fileNumber: Int) {
            val start = plan
            val ownKeys = HashSet<String>()
            val ownIds = HashSet<String>()
            var seen = 0
            progress(SyncProgress(SyncProgress.Stage.Guide, backupLine = BackupGuide.readingLine(fileNumber, files, count)))
            try {
                val file = File(cacheDir, "guide-backup-$sourceId.tmp")
                try {
                    GuideDownload.toFile(http, address, file)
                } catch (error: Exception) {
                    file.delete()
                    throw error
                }
                file.inputStream().buffered(256 * 1024).use { input ->
                    file.delete() // Unlinked now, as the provider's reader does.
                    XmltvParser().parse(
                        input = input,
                        batchSize = BACKUP_BATCH,
                        sink = object : XmltvSink {
                            override fun onChannels(channels: List<ParsedGuideChannel>) {
                                val before = plan
                                plan = chooser.add(before, channels.map { MatchableGuide(it.xmltvId, it.names) })
                                plan.keysAddedSince(before).forEach { key ->
                                    ownKeys.add(key)
                                    ownIds.add(key.removePrefix(BackupGuide.KEY_PREFIX))
                                }
                            }

                            override fun onProgrammes(batch: List<ParsedProgramme>) {
                                if (!marked) {
                                    store.setHasRows(sourceId, true)
                                    marked = true
                                }
                                db.programmes().insertAll(batch.map { toRow(it) })
                                batch.forEach { written.add(BackupGuide.key(it.channelKey)) }
                                count += batch.size
                                progress(SyncProgress(SyncProgress.Stage.Guide, programmes = count, backupLine = BackupGuide.readingLine(fileNumber, files, count)))
                            }
                        },
                        wanted = { id ->
                            seen++
                            id in ownIds
                        },
                    )
                }
                if (!GuideRules.accepts(seen)) throw IllegalStateException("No programmes")
                plan = plan.keeping(written)
            } catch (error: Exception) {
                plan = start
                written -= ownKeys
                ownKeys.chunked(400).forEach { db.programmes().deleteKeys(sourceId, STAGING_GENERATION, it) }
                throw error
            }
        }

        // Every screen adds the source's guide offset to stored times, and backup times are already real UTC.
        // So the row is stored with the offset taken off, and each read lands back on the true time.
        private fun toRow(programme: ParsedProgramme): ProgrammeEntity = programme.toEntity(sourceId, STAGING_GENERATION).copy(
            channelKey = BackupGuide.key(programme.channelKey),
            startUtc = programme.startUtc - offset,
            stopUtc = programme.stopUtc - offset,
        )
    }
}
