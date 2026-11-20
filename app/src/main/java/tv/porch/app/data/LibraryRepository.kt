package tv.porch.app.data

import android.os.Process
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.porch.app.domain.BackupGuide
import tv.porch.app.domain.Kind
import tv.porch.app.domain.Airing
import tv.porch.app.domain.Channel
import tv.porch.app.domain.ChannelFamilies
import tv.porch.app.domain.ChannelFold
import tv.porch.app.domain.ChannelsChange
import tv.porch.app.domain.ChannelsChoice
import tv.porch.app.domain.FollowTerms
import tv.porch.app.domain.FuzzyText
import tv.porch.app.domain.GroupedName
import tv.porch.app.domain.StarterPick
import tv.porch.app.domain.GroupChoice
import tv.porch.app.domain.GroupKey
import tv.porch.app.domain.GuideClock
import tv.porch.app.domain.GuideMatcher
import tv.porch.app.domain.PlayRules
import tv.porch.app.domain.GuideRules
import tv.porch.app.domain.Kinds
import tv.porch.app.domain.LogoAddress
import tv.porch.app.domain.MatchableChannel
import tv.porch.app.domain.MatchableGuide
import tv.porch.app.domain.Programme
import tv.porch.app.domain.ProgrammeDetail
import tv.porch.app.domain.SourceDraft
import tv.porch.app.domain.SyncProgress
import tv.porch.app.domain.cleaned
import tv.porch.app.parse.M3uParser
import tv.porch.app.parse.ParsedChannel
import tv.porch.app.parse.ParsedGuideChannel
import tv.porch.app.parse.XtreamMapper
import tv.porch.app.parse.XtreamUrls
import tv.porch.app.parse.XmltvParser
import tv.porch.app.parse.XmltvSink

/** Programmes saved per insert while a guide streams in. */
private const val GUIDE_BATCH = 1_000

data class SearchHits(
    val onNow: List<Pair<Channel, Programme>>,
    val later: List<Pair<Channel, Programme>>,
    val channels: List<Channel>,
    val offsets: Map<Long, Int> = emptyMap(),
) {
    companion object {
        val Empty = SearchHits(emptyList(), emptyList(), emptyList())
    }
}

data class GroupRow(
    val name: String,
    val count: Int,
    val hidden: Boolean,
    val language: String?,
)

/** How many close-spelled programme titles a search looks up. */
private const val CLOSE_TITLES = 20

/** SQLite allows 999 variables in a query; the other three take a few. */
private const val SPORT_KEY_CHUNK = 900

/** Sport channels' programmes, and each source's guide offset in minutes (by source id). */
data class SportGuide(val airings: List<Airing>, val offsets: Map<Long, Int>)

class LibraryRepository(
    private val db: PorchDatabase,
    private val http: HttpFetcher,
    private val cacheDir: File,
    backupGuides: BackupGuides,
    // The TV's country, read here and passed into the pure starting pick. A test can pass its own.
    private val country: () -> String = { Locale.getDefault().country },
) {
    // One background-priority thread for the guide download, parse and inserts, so a big import
    // does not fight the screen for the CPU.
    init {
        GuideDownload.clearLeftovers(cacheDir)
    }

    private val backups = BackupGuideLoader(db, http, cacheDir, backupGuides)

    private val importDispatcher: CoroutineDispatcher by lazy {
        Executors.newSingleThreadExecutor { task ->
            Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                task.run()
            }, "porch-import").apply { isDaemon = true }
        }.asCoroutineDispatcher()
    }

    fun observeSources(): Flow<List<SourceEntity>> = db.sources().observe()

    // Home, the guide, What's on now, the player and Settings all read the channel list. One shared copy, so the
    // query, the cleaning of 49,000 names and the kinds run once per database change, not once per screen.
    private val shareScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var warmJob: Job? = null

    private val sharedChannels: Flow<List<Channel>> by lazy {
        combine(
            db.channels().observeRows(),
            db.hiddenGroups().observe(),
            db.sources().observe(),
        ) { channels, hidden, sources ->
            val hide = hidden.map { it.sourceId to it.groupName }.toSet()
            val languages = sources.associate { it.id to it.languageFilter }
            channels.visibleInParallel(hide, languages)
        }.onEach { warmKinds(it) }
            .flowOn(Dispatchers.Default)
            .shareIn(shareScope, SharingStarted.WhileSubscribed(30_000), replay = 1)
    }

    fun observeChannels(): Flow<List<Channel>> = sharedChannels

    // The guide's kind pills need every channel's kind. Read them once, in the background, so opening the guide finds them ready.
    private fun warmKinds(list: List<Channel>) {
        warmJob?.cancel()
        warmJob = shareScope.launch { Kinds.indexInParallel(list) }
    }

    fun observeOnNow(now: Long): Flow<List<Programme>> =
        db.programmes().onNow(now).map { rows -> rows.map { it.toModel() } }

    fun observeStartingUntil(now: Long, until: Long): Flow<List<Programme>> =
        db.programmes().startingUntil(now, until).map { rows -> rows.map { it.toModel() } }

    fun observeStartingBetween(fromEffective: Long, toEffective: Long): Flow<List<Programme>> =
        db.programmes().startingBetween(fromEffective, toEffective).map { rows -> rows.map { it.toModel() } }

    fun observeChannelsBySource(sourceId: Long): Flow<List<ChannelEntity>> =
        db.channels().observeBySource(sourceId)

    /** Channels per group on one source, dividers left out. */
    fun observeGroupCounts(sourceId: Long): Flow<Map<String, Int>> =
        db.channels().observeGroupRows(sourceId)
            .map { rows -> ChannelsChoice.groupCounts(rows.map { GroupedName(it.groupName, it.name, it.divider) }) }
            .flowOn(Dispatchers.Default)

    fun observeHidden(): Flow<List<HiddenGroupEntity>> = db.hiddenGroups().observe()

    fun observeChosenGroups(): Flow<Set<GroupKey>> =
        db.chosenGroups().observe().map { rows -> rows.map { GroupKey(it.sourceId, it.groupName) }.toSet() }

    suspend fun addSource(draft: SourceDraft): Long = withContext(Dispatchers.IO) {
        val clean = draft.cleaned()
        val entity = SourceEntity(
            name = clean.name,
            kind = clean.kind,
            m3uUrl = if (clean.kind == "m3u") clean.m3uUrl else null,
            xtreamServer = if (clean.kind == "xtream") XtreamUrls.normalizeServer(clean.server) else null,
            xtreamUsername = if (clean.kind == "xtream") clean.username else null,
            xtreamPassword = if (clean.kind == "xtream") clean.password else null,
            xmltvUrl = clean.xmltvUrl,
        )
        db.sources().insert(entity)
    }

    /**
     * Saves an edit to a source in place. The id stays, so everything tied to it stays. Throws with a sentence the form can
     * show. Returns true when the Xtream server or username changed, so Movies and Series should ask the new account again.
     */
    suspend fun editSource(id: Long, draft: SourceDraft): Boolean = withContext(Dispatchers.IO) {
        val source = db.sources().get(id) ?: throw IllegalArgumentException("That source has gone.")
        val edited = SourceEdit.apply(source, draft)
        db.sources().edit(id, edited.name, edited.m3uUrl, edited.xtreamServer, edited.xtreamUsername, edited.xtreamPassword, edited.xmltvUrl)
        source.kind == "xtream" && (source.xtreamServer != edited.xtreamServer || source.xtreamUsername != edited.xtreamUsername)
    }

    suspend fun deleteSource(id: Long) = withContext(Dispatchers.IO) {
        db.runInTransaction {
            db.programmes().deleteBySource(id)
            db.channels().deleteBySource(id)
            db.hiddenGroups().deleteBySource(id)
            db.chosenGroups().deleteBySource(id)
            db.sources().delete(id)
        }
    }

    suspend fun setOffset(id: Long, minutes: Int) = withContext(Dispatchers.IO) {
        val next = minutes.coerceIn(-12 * 60, 12 * 60)
        db.runInTransaction {
            val before = db.sources().get(id)?.guideOffsetMinutes ?: next
            db.sources().setOffset(id, next)
            if (before != next) db.programmes().shiftBackupRows(id, (before - next) * 60_000L)
        }
    }

    suspend fun setLanguage(id: Long, filter: String?) = withContext(Dispatchers.IO) {
        db.sources().setLanguage(id, filter?.trim()?.uppercase()?.ifBlank { null })
    }

    suspend fun setGroupHidden(sourceId: Long, group: String, hidden: Boolean) = withContext(Dispatchers.IO) {
        if (hidden) db.hiddenGroups().insert(HiddenGroupEntity(sourceId, group))
        else db.hiddenGroups().delete(sourceId, group)
    }

    suspend fun setGroupChosen(sourceId: Long, group: String, chosen: Boolean) = withContext(Dispatchers.IO) {
        if (chosen) db.chosenGroups().insert(ChosenGroupEntity(sourceId, group))
        else db.chosenGroups().delete(sourceId, group)
    }

    private fun applyChange(sourceId: Long, change: ChannelsChange) {
        change.removeHidden.forEach { db.hiddenGroups().delete(sourceId, it) }
        change.removePicks.forEach { db.chosenGroups().delete(sourceId, it) }
        change.addPicks.forEach { db.chosenGroups().insert(ChosenGroupEntity(sourceId, it)) }
    }

    /**
     * Shows or hides [groups] on one source. The change is worked out inside one transaction from the saved rows,
     * so quick repeated presses cannot act on a stale screen. [show] null means "switch": all go hidden if any is
     * showing, otherwise all go showing.
     */
    suspend fun setGroupsShowing(sourceId: Long, groups: Collection<String>, show: Boolean?) = withContext(Dispatchers.IO) {
        db.runInTransaction {
            val present = groupNames(sourceId)
            val change = ChannelsChoice.change(
                targets = groups,
                show = show,
                chosen = ChannelsChoice.effective(db.chosenGroups().namesBySource(sourceId).toSet(), present),
                hidden = db.hiddenGroups().namesBySource(sourceId).toSet(),
            ) { present }
            applyChange(sourceId, change)
        }
    }

    /** Replaces this source's choices with the starting pick a new source gets. */
    suspend fun useStartingPick(sourceId: Long) = withContext(Dispatchers.IO) {
        db.runInTransaction {
            db.chosenGroups().namesBySource(sourceId).forEach { db.chosenGroups().delete(sourceId, it) }
            db.hiddenGroups().deleteBySource(sourceId)
            (StarterPick.groups(groupNames(sourceId), country()) + GroupChoice.RESTRICTED).forEach { group ->
                db.chosenGroups().insert(ChosenGroupEntity(sourceId, group))
            }
        }
    }

    private fun groupNames(sourceId: Long): Set<String> =
        ChannelsChoice.groupCounts(db.channels().groupRows(sourceId).map { GroupedName(it.groupName, it.name, it.divider) }).keys

    /**
     * An old single-language filter becomes picks, once, so the viewer sees the same channels and the
     * filter can be cleared. The column and the code that reads it stay.
     */
    suspend fun convertLanguageFilter(sourceId: Long) = withContext(Dispatchers.IO) {
        db.runInTransaction {
            val filter = db.sources().get(sourceId)?.languageFilter
            if (!filter.isNullOrBlank()) {
                val present = groupNames(sourceId)
                val change = ChannelsChoice.convertFilter(
                    present,
                    filter,
                    ChannelsChoice.effective(db.chosenGroups().namesBySource(sourceId).toSet(), present),
                )
                applyChange(sourceId, change)
                db.sources().setLanguage(sourceId, null)
            }
        }
    }

    suspend fun clearChosenGroups() = withContext(Dispatchers.IO) { db.chosenGroups().clear() }

    suspend fun moveChannel(channelId: Long, group: String?) = withContext(Dispatchers.IO) {
        db.channels().setGroupOverride(channelId, group?.trim()?.ifBlank { null })
    }

    suspend fun setFavourite(channelId: Long, favourite: Boolean) = withContext(Dispatchers.IO) {
        db.channels().setFavourite(channelId, favourite)
    }

    suspend fun markPlayed(channelId: Long) = withContext(Dispatchers.IO) {
        if (PlayRules.needsMark(channelId, db.channels().lastPlayed()?.id)) db.channels().setPlayed(channelId, System.currentTimeMillis())
    }

    /** How many channels a group holds in a source, for the quick menu's Hide row. */
    suspend fun groupSize(sourceId: Long, group: String): Int = withContext(Dispatchers.IO) {
        db.channels().countInGroup(sourceId, group)
    }

    suspend fun channel(id: Long): Channel? = withContext(Dispatchers.IO) {
        db.channels().get(id)?.toModel()
    }

    /** The channel's identity, which survives a refresh when its id does not. Null when it has gone. */
    suspend fun channelIdentity(id: Long): String? = withContext(Dispatchers.IO) {
        db.channels().identityOf(id)
    }

    /** The channel as it is now, found by the pair [channelIdentity] gave. Null when a refresh removed it. */
    suspend fun channelByIdentity(sourceId: Long, identity: String): Channel? = withContext(Dispatchers.IO) {
        db.channels().byIdentity(sourceId, identity)?.toModel()
    }

    /** The channel played last and its group's ids for zapping, or null when nothing was ever played. Two small queries, so a cold start doesn't wait for the full channel list. */
    suspend fun resumePoint(): Pair<List<Long>, Int>? = withContext(Dispatchers.IO) {
        val last = db.channels().lastPlayed() ?: return@withContext null
        val group = last.groupOverride?.takeIf { it.isNotBlank() } ?: last.groupName
        val ids = db.channels().idsInGroup(last.sourceId, group).ifEmpty { listOf(last.id) }
        ids to ids.indexOf(last.id).coerceAtLeast(0)
    }

    suspend fun channels(ids: List<Long>): List<Channel> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        val byId = db.channels().getMany(ids).associateBy { it.id }
        ids.mapNotNull { byId[it]?.toModel() }
    }

    suspend fun source(id: Long): SourceEntity? = withContext(Dispatchers.IO) {
        db.sources().get(id)
    }

    suspend fun sources(): List<SourceEntity> = withContext(Dispatchers.IO) {
        db.sources().list()
    }

    suspend fun programmesFor(
        sourceId: Long,
        generation: Long,
        keys: List<String>,
        windowStart: Long,
        windowEnd: Long,
        offsetMinutes: Int,
    ): List<Programme> = withContext(Dispatchers.IO) {
        if (keys.isEmpty()) return@withContext emptyList()
        val delta = offsetMinutes * 60_000L
        db.programmes().window(
            sourceId = sourceId,
            generation = generation,
            keys = keys,
            fromUtc = windowStart - delta,
            toUtc = windowEnd - delta,
        ).map { it.toModel() }
    }

    /**
     * Programmes on Sport channels from [fromMs] to [toMs] (guide-shifted times), for the Sport tab to link games to
     * channels. Channels with no guide match are left out. The offsets say how far each source's guide is shifted.
     */
    suspend fun sportGuide(fromMs: Long, toMs: Long): SportGuide = withContext(Dispatchers.IO) {
        val sources = db.sources().list()
        val offsets = sources.associate { it.id to it.guideOffsetMinutes }
        val bySource = sharedChannels.first()
            .filter { it.matchedXmltvId != null && Kinds.of(it) == Kind.Sport }
            .groupBy { it.sourceId }
        val airings = ArrayList<Airing>()
        for (source in sources) {
            val channels = bySource[source.id] ?: continue
            val byXmltv = channels.groupBy { checkNotNull(it.matchedXmltvId) }
            val delta = (offsets[source.id] ?: 0) * 60_000L
            for (keys in byXmltv.keys.chunked(SPORT_KEY_CHUNK)) {
                val rows = db.programmes().windowTitles(source.id, source.guideGeneration, keys, fromMs - delta, toMs - delta)
                for (row in rows) {
                    val programme = row.toModel()
                    for (channel in byXmltv[row.channelKey].orEmpty()) airings += Airing(channel, programme)
                }
            }
        }
        SportGuide(airings, offsets)
    }

    /** One programme's description and picture, for Home's preview. Null when the row has gone (a refresh replaced it). */
    suspend fun programmeDetail(id: Long): ProgrammeDetail? = withContext(Dispatchers.IO) {
        db.programmes().get(id)?.let { ProgrammeDetail(it.id, it.description, LogoAddress.clean(it.iconUrl)) }
    }

    /** One whole programme, for the details sheet when a list row only carries its id. Null when the row has gone. */
    suspend fun programme(id: Long): Programme? = withContext(Dispatchers.IO) {
        db.programmes().get(id)?.toModel()
    }

    suspend fun upcoming(channelId: Long, now: Long, limit: Int = 6): List<Programme> = withContext(Dispatchers.IO) {
        db.programmes().upcoming(channelId, now, limit).map { it.toModel() }
    }

    /**
     * Programmes on now or in the next 7 days that fit any of [terms], with their channel. A term that finds nothing exactly gets
     * a close-spelling try, and the titles are fetched at most once for all terms.
     */
    suspend fun following(terms: List<String>, now: Long, families: ChannelFamilies = ChannelFamilies.None): List<Pair<Channel, Programme>> = withContext(Dispatchers.IO) {
        if (terms.isEmpty()) return@withContext emptyList()
        val visible = sharedChannels.first()
        val byKey = ChannelFold.foldGuided(visible, families).mapNotNull { channel ->
            val key = channel.matchedXmltvId ?: return@mapNotNull null
            (channel.sourceId to key) to channel
        }.toMap()
        val offsets = db.sources().list().associate { it.id to it.guideOffsetMinutes }
        // 36 hours before now, as search does, so a large guide offset still finds what is on air.
        val from = now - 36L * 60L * 60L * 1000L
        val to = now + 7L * 24L * 60L * 60L * 1000L
        val found = LinkedHashMap<Long, Pair<Channel, Programme>>()
        fun take(rows: List<ProgrammeEntity>, term: String): Int {
            var kept = 0
            for (row in rows) {
                if (!FollowTerms.matches(term, row.title)) continue
                val channel = byKey[row.sourceId to row.channelKey] ?: continue
                val programme = row.toModel()
                if (GuideClock.shift(programme.stopUtc, offsets[row.sourceId] ?: 0) > now) {
                    found.putIfAbsent(programme.id, channel to programme)
                    kept++
                }
            }
            return kept
        }
        var titles: List<String>? = null
        for (term in terms) {
            if (take(db.programmes().following(term, from, to), term) == 0 && FuzzyText.worthTrying(term)) {
                val all = titles ?: db.programmes().titles(from, to).also { titles = it }
                val close = all.filter { FollowTerms.matches(term, it) }.take(CLOSE_TITLES)
                if (close.isNotEmpty()) take(db.programmes().withTitles(close, from, to), term)
            }
        }
        found.values.toList()
    }

    suspend fun search(query: String, now: Long, families: ChannelFamilies = ChannelFamilies.None): SearchHits = withContext(Dispatchers.IO) {
        val needle = query.trim()
        if (needle.length < 1) return@withContext SearchHits.Empty
        val sources = db.sources().list()
        val offsets = sources.associate { it.id to it.guideOffsetMinutes }
        // The shared, already cleaned list: no 49,000-row read per keystroke.
        val visible = sharedChannels.first()
        // Matched copies only: the best copy overall may have no guide id, and would take its family's programmes with it.
        val byKey = ChannelFold.foldGuided(visible, families).mapNotNull { channel ->
            val key = channel.matchedXmltvId ?: return@mapNotNull null
            (channel.sourceId to key) to channel
        }.toMap()
        // Fold what matched, not the other way round: the best copy's name may not contain what was typed.
        var channels = ChannelFold.fold(visible.filter { it.name.contains(needle, ignoreCase = true) || it.shownTitle.contains(needle, ignoreCase = true) }, families).take(12)
        val from = now - 36L * 60L * 60L * 1000L
        val to = now + 48L * 60L * 60L * 1000L
        val onNow = ArrayList<Pair<Channel, Programme>>()
        val later = ArrayList<Pair<Channel, Programme>>()
        fun sort(rows: List<ProgrammeEntity>) {
            for (row in rows) {
                val channel = byKey[row.sourceId to row.channelKey] ?: continue
                val programme = row.toModel()
                val offset = offsets[row.sourceId] ?: 0
                when {
                    GuideClock.isOnNow(programme.startUtc, programme.stopUtc, now, offset) -> {
                        if (onNow.size < 12) onNow.add(channel to programme)
                    }
                    GuideClock.shift(programme.startUtc, offset) > now -> {
                        if (later.size < 12) later.add(channel to programme)
                    }
                }
                if (onNow.size >= 12 && later.size >= 12) break
            }
        }
        sort(db.programmes().search(needle, from, to))
        // Only when nothing can be shown, so a correct spelling pays nothing for this.
        if (channels.isEmpty() && onNow.isEmpty() && later.isEmpty() && FuzzyText.worthTrying(needle)) {
            val titles = db.programmes().titles(from, to).filter { FuzzyText.matches(needle, it) }.take(CLOSE_TITLES)
            if (titles.isNotEmpty()) sort(db.programmes().withTitles(titles, from, to))
            channels = ChannelFold.fold(visible.filter { FuzzyText.matches(needle, it.shownTitle) }, families).take(12)
        }
        SearchHits(onNow, later, channels, offsets)
    }

    /**
     * Replace channels, then the guide. Channels are saved as soon as the guide's channel list
     * arrives, so Home has channels while the programmes are still streaming in. The new guide
     * only becomes the visible one at the end. A failed guide keeps the previous guide generation
     * and still saves channels, so a bad guide URL does not wipe a working playlist.
     * Favourites, group moves and last-played are copied across when the channel identity matches.
     */
    suspend fun refresh(sourceId: Long, progress: (SyncProgress) -> Unit) = withContext(importDispatcher) {
        val source = db.sources().get(sourceId) ?: return@withContext
        progress(SyncProgress(SyncProgress.Stage.Channels))
        val parsed = fetchChannels(source)
        if (parsed.isEmpty()) {
            throw IllegalStateException("No channels found. Check the address and try again.")
        }
        var zoneRead = false
        var serverZone: String? = null
        if (source.kind == "xtream") {
            try {
                val server = source.xtreamServer
                val user = source.xtreamUsername
                val pass = source.xtreamPassword
                if (server != null && user != null && pass != null) {
                    val reply = readText(XtreamUrls.login(server, user, pass))
                    if (XtreamMapper.isLoginReply(reply)) { // anything else keeps the stored zone
                        serverZone = XtreamMapper.serverTimeZone(reply)
                        zoneRead = true
                    }
                }
            } catch (error: Exception) {
                // Keep the stored zone. The refresh carries on.
            }
        }
        val previous = db.channels().listBySource(sourceId).associateBy { it.identity }

        // Transaction A: swap in the new channels (and the zone). [fresh] is null when there is no
        // usable new guide, which keeps each channel's old match.
        fun saveChannels(fresh: Map<String, String>?, touchAt: Long?) {
            db.runInTransaction {
                // Only the source's first save picks its starting groups, so Home never flashes every channel.
                val firstSave = StarterPick.shouldPick(
                    hadChannels = db.channels().countBySource(sourceId) > 0,
                    hasPicks = db.chosenGroups().countBySource(sourceId) > 0,
                )
                db.channels().deleteBySource(sourceId)
                val rows = parsed.map { item ->
                    val old = previous[item.identity]
                    item.toEntity(sourceId, old, if (fresh != null) BackupGuide.keepBackup(fresh[item.identity], old?.matchedXmltvId) else old?.matchedXmltvId)
                }
                rows.chunked(400).forEach { db.channels().insertAll(it) }
                if (firstSave) {
                    // The reserved row makes the source restricted, even if no group is kept. Groups of only dividers are left out.
                    val groups = ChannelsChoice.groupCounts(parsed.map { GroupedName(it.group, it.name) }).keys
                    (StarterPick.groups(groups, country()) + GroupChoice.RESTRICTED).forEach { group ->
                        db.chosenGroups().insert(ChosenGroupEntity(sourceId, group))
                    }
                }
                if (zoneRead) db.sources().setServerTimeZone(sourceId, serverZone)
                if (touchAt != null) db.sources().touch(sourceId, touchAt)
            }
        }

        fun forMatching() = parsed.map { MatchableChannel(it.identity, it.tvgId, it.tvgName, it.name) }

        if (source.xmltvUrl.isNullOrBlank()) {
            saveChannels(null, System.currentTimeMillis())
            backups.load(sourceId, forMatching(), providerOk = false, progress)
            progress(SyncProgress(SyncProgress.Stage.Done, channels = parsed.size))
            return@withContext
        }

        val generation = source.guideGeneration + 1
        var early: Deferred<Result<Unit>>? = null
        var guideError: Exception? = null
        try {
            db.programmes().deleteOtherGenerations(sourceId, source.guideGeneration)
            val expected = (db.programmes().countInGeneration(sourceId, source.guideGeneration) - backups.rowsIn(sourceId, source.guideGeneration))
                .takeIf { it > 0 }
            progress(SyncProgress(SyncProgress.Stage.Guide, channels = parsed.size, expected = expected))
            val guideChannels = mutableListOf<ParsedGuideChannel>()
            var lateChannels = false
            fun matchAll(guide: List<ParsedGuideChannel>) = GuideMatcher.match(
                forMatching(),
                guide.map { MatchableGuide(it.xmltvId, it.names) },
            )
            readGuide(source.xmltvUrl, sourceId, generation, source.guideGeneration > 0, expected, progress) { list ->
                guideChannels.addAll(list)
                if (early == null) {
                    // Save channels now, on another coroutine, so this thread keeps reading the
                    // guide stream while the matching and the write run.
                    val snapshot = guideChannels.toList()
                    early = async(Dispatchers.IO) {
                        runCatching {
                            progress(SyncProgress(SyncProgress.Stage.Matching, channels = parsed.size))
                            saveChannels(matchAll(snapshot), null)
                            progress(SyncProgress(SyncProgress.Stage.Guide, channels = parsed.size, expected = expected))
                        }
                    }
                } else {
                    lateChannels = true // a file that lists more channels after its programmes
                }
            }
            checkNotNull(early).await().getOrThrow()
            if (lateChannels) {
                // Match once over every guide channel and update the rows in place, so ids,
                // favourites and last-played set during the import survive.
                val matches = matchAll(guideChannels)
                db.runInTransaction {
                    db.channels().listBySource(sourceId).forEach { row ->
                        db.channels().setMatched(row.id, BackupGuide.keepBackup(matches[row.identity], previous[row.identity]?.matchedXmltvId))
                    }
                }
            }
            // Transaction B: the new guide becomes the visible one.
            val now = System.currentTimeMillis()
            db.runInTransaction {
                db.sources().setGuideGeneration(sourceId, generation, now)
                backups.carryForward(sourceId, source.guideGeneration, generation)
                db.programmes().deleteOtherGenerations(sourceId, generation)
            }
            backups.load(sourceId, forMatching(), providerOk = true, progress)
            progress(SyncProgress(SyncProgress.Stage.Done, channels = parsed.size))
            return@withContext
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            guideError = error
        }
        db.programmes().deleteOtherGenerations(sourceId, source.guideGeneration)
        val channelsSaved = early?.await()?.isSuccess == true
        if (channelsSaved) {
            // The channels are good, but their matches point at the failed guide's ids while the
            // old generation stays visible. Put the old matches back.
            db.runInTransaction {
                db.channels().listBySource(sourceId).forEach { row ->
                    db.channels().setMatched(row.id, previous[row.identity]?.matchedXmltvId)
                }
                db.sources().touch(sourceId, System.currentTimeMillis())
            }
        } else {
            saveChannels(null, System.currentTimeMillis())
        }
        backups.load(sourceId, forMatching(), providerOk = false, progress)
        // Tagged, so the words blame the guide and not the playlist.
        throw GuideRefreshException(checkNotNull(guideError))
    }

    private fun fetchChannels(source: SourceEntity): List<ParsedChannel> {
        return when (source.kind) {
            "xtream" -> {
                val server = source.xtreamServer ?: throw IllegalStateException("This Xtream source has no server")
                val user = source.xtreamUsername ?: throw IllegalStateException("This Xtream source has no username")
                val pass = source.xtreamPassword ?: throw IllegalStateException("This Xtream source has no password")
                val categories = readText(XtreamUrls.api(server, user, pass, "get_live_categories"))
                val streams = readText(XtreamUrls.api(server, user, pass, "get_live_streams"))
                XtreamMapper.toChannels(categories, streams, server, user, pass)
            }
            else -> {
                val url = source.m3uUrl ?: throw IllegalStateException("This playlist has no URL")
                http.open(url).use { M3uParser().parse(it) }
            }
        }
    }

    /** Streams the guide into [generation]. Hands the guide's channel list to [onChannels] before any programme. */
    private fun readGuide(
        url: String,
        sourceId: Long,
        generation: Long,
        hadGuide: Boolean,
        expected: Int?,
        progress: (SyncProgress) -> Unit,
        onChannels: (List<ParsedGuideChannel>) -> Unit,
    ) {
        var count = 0
        var channelsSeen = false
        val file = File(cacheDir, "guide-$sourceId.tmp")
        try {
            GuideDownload.toFile(http, url, file)
        } catch (error: Exception) {
            file.delete()
            throw error
        }
        file.inputStream().buffered(256 * 1024).use { input ->
            file.delete() // Unlinked now; the open stream still reads it, and a crash mid-parse leaves nothing behind.
            XmltvParser().parse(
                input = input,
                batchSize = GUIDE_BATCH,
                sink = object : XmltvSink {
                    override fun onChannels(list: List<ParsedGuideChannel>) {
                        channelsSeen = true
                        onChannels(list)
                    }

                    override fun onProgrammes(batch: List<tv.porch.app.parse.ParsedProgramme>) {
                        db.programmes().insertAll(batch.map { it.toEntity(sourceId, generation) })
                        val before = count
                        count += batch.size
                        if (before / 2_000 != count / 2_000) {
                            progress(SyncProgress(SyncProgress.Stage.Guide, programmes = count, expected = expected))
                        }
                    }
                },
            )
        }
        progress(SyncProgress(SyncProgress.Stage.Guide, programmes = count, expected = expected))
        // An empty or HTML "guide" fails the refresh, so the caller keeps the last good guide.
        if (!GuideRules.accepts(count)) throw IllegalStateException(GuideRules.emptyMessage(hadGuide))
        // A guide with programmes but no channel list still saves the channels, with no matches.
        if (!channelsSeen) onChannels(emptyList())
    }

    private fun readText(url: String): String {
        http.open(url).use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > 48 * 1024 * 1024) throw IllegalStateException("The channel list is too large")
                out.write(buffer, 0, read)
            }
            return out.toString(Charset.forName("UTF-8"))
        }
    }
}
