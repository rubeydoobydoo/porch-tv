package tv.porch.app.data

import android.content.SharedPreferences
import android.os.Process
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import tv.porch.app.domain.FilmSearch
import tv.porch.app.domain.Films
import tv.porch.app.domain.FuzzyText
import tv.porch.app.domain.MovieShelf
import tv.porch.app.domain.ShowProgressChange
import tv.porch.app.domain.Shows
import tv.porch.app.domain.VodRefresh
import tv.porch.app.parse.XtreamEpisode
import tv.porch.app.parse.XtreamMapper
import tv.porch.app.parse.XtreamSeries
import tv.porch.app.parse.XtreamShow
import tv.porch.app.parse.XtreamShowInfo
import tv.porch.app.parse.XtreamUrls
import tv.porch.app.parse.XtreamVod

/**
 * Series for an Xtream source, built like [MovieRepository]: a shelf downloads only its own categories, and the
 * full title list fills in later on a background thread ([syncTitles]). Any other source kind gives empty results.
 * Nothing here runs at app start.
 */
class ShowRepository(
    private val db: PorchDatabase,
    private val http: HttpFetcher,
    private val cacheDir: File,
    private val prefs: SharedPreferences,
    private val country: () -> String = { Locale.getDefault().country },
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val importDispatcher: CoroutineDispatcher by lazy {
        Executors.newSingleThreadExecutor { task ->
            Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                task.run()
            }, "porch-series").apply { isDaemon = true }
        }.asCoroutineDispatcher()
    }

    private val syncing = AtomicBoolean(false)

    /**
     * Forgets when this source's categories and titles were last fetched, so the next visit asks the provider again. Called
     * when an edit changes the server or username: the old account's titles then leave as their categories refresh.
     */
    fun forgetFetched(sourceId: Long) {
        val keys = prefs.all.keys.filter { key ->
            key == "shows_cats_v2_at_$sourceId" || key == "shows_titles_at_$sourceId" || key.startsWith("shows_cat_at_${sourceId}_")
        }
        if (keys.isNotEmpty()) prefs.edit().apply { keys.forEach { remove(it) } }.apply()
    }
    private val nameCache = HashMap<Long, Pair<LongArray, List<String>>>()
    // Each entry keeps when it was fetched, so a new episode shows within [INFO_FRESH] even if Porch stays open for days.
    private val infoCache = object : LinkedHashMap<Pair<Long, Long>, Pair<XtreamShowInfo, Long>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Long, Long>, Pair<XtreamShowInfo, Long>>?) = size > INFO_KEPT
    }

    private class Login(val server: String, val user: String, val pass: String)

    private fun login(sourceId: Long): Login? {
        val source = db.sources().get(sourceId) ?: return null
        return login(source)
    }

    private fun login(source: SourceEntity): Login? {
        if (source.kind != "xtream") return null
        return Login(
            source.xtreamServer ?: return null,
            source.xtreamUsername ?: return null,
            source.xtreamPassword ?: return null,
        )
    }

    suspend fun shelves(sourceId: Long): List<MovieShelf> = withContext(Dispatchers.IO) {
        val login = login(sourceId) ?: return@withContext emptyList()
        // v2: the pick rule changed (leading bars), so picks stored by an older build refresh on first open.
        val key = "shows_cats_v2_at_$sourceId"
        val stored = db.showCategories().list(sourceId)
        val stale = stored.isEmpty() || now() - prefs.getLong(key, 0L) > DAY
        if (stale) {
            try {
                val body = readText(XtreamUrls.seriesCategories(login.server, login.user, login.pass))
                val fresh = XtreamMapper.categories(body)
                // Series has no category chooser, so each refresh picks again with the current rule.
                val ordered = Shows.shelves(fresh.map { it.categoryId to it.categoryName }, country())
                val shelfOf = ordered.flatMap { shelf -> shelf.categoryIds.map { it to shelf } }.toMap()
                // The stored position is the shelf's place after the genre sort, so reading back keeps that order.
                val position = ordered.flatMapIndexed { index, shelf -> shelf.categoryIds.map { it to index } }.toMap()
                val rows = fresh.map { item ->
                    val shelf = shelfOf[item.categoryId]
                    ShowCategoryEntity(
                        sourceId, item.categoryId, item.categoryName,
                        shelf?.title ?: "",
                        shelf?.chosen ?: false,
                        position[item.categoryId] ?: Int.MAX_VALUE,
                    )
                }
                // An empty list is suspect: keep the stored rows and ask again on the next open.
                if (VodRefresh.replaceCategories(rows.size)) {
                    db.runInTransaction {
                        db.showCategories().deleteBySource(sourceId)
                        db.showCategories().insertAll(rows)
                    }
                    prefs.edit().putLong(key, now()).apply()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Offline or refused: whatever the table has.
            }
        }
        val rows = db.showCategories().list(sourceId).filter { it.shownName.isNotEmpty() }
        // A picked and an unpicked category with the same name stay on separate shelves (see Shows.shelves).
        val order = LinkedHashMap<Pair<String, Boolean>, MutableList<ShowCategoryEntity>>()
        rows.forEach { order.getOrPut(it.shownName to it.chosen) { mutableListOf() }.add(it) }
        order.map { (key, list) -> MovieShelf(key.first, list.map { it.categoryId }, key.second) }
            .let { Films.shown(it) }
    }

    suspend fun shows(sourceId: Long, shelf: MovieShelf, limit: Int = 500): List<ShowEntity> = withContext(Dispatchers.IO) {
        val login = login(sourceId) ?: return@withContext emptyList()
        for (id in shelf.categoryIds) {
            val key = "shows_cat_at_v2_${sourceId}_$id"
            if (now() - prefs.getLong(key, 0L) < DAY) continue
            try {
                val fetched = download(XtreamUrls.seriesList(login.server, login.user, login.pass, id), sourceId, id)
                val after = VodRefresh.afterCategory(fetched.rows, fetched.wasList)
                if (after.prune) {
                    // Only now, with the whole category written: rows it did not list still have an older stamp.
                    db.runInTransaction {
                        db.shows().dropOnlyListedIn(sourceId, id, fetched.stamp)
                        db.shows().dropListings(sourceId, id, fetched.stamp)
                    }
                    synchronized(nameCache) { nameCache.remove(sourceId) }
                }
                if (after.markFetched) prefs.edit().putLong(key, now()).apply()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Offline or refused: the table's rows still show.
            }
        }
        db.shows().inCategories(sourceId, shelf.categoryIds, limit)
    }

    /** The seasons and episodes of a show, or null when offline or not understood. The last few are kept in memory. */
    suspend fun info(sourceId: Long, seriesId: Long): XtreamShowInfo? = withContext(Dispatchers.IO) {
        val key = sourceId to seriesId
        synchronized(infoCache) { infoCache[key] }?.let { (info, at) -> if (now() - at < INFO_FRESH) return@withContext info }
        val login = login(sourceId) ?: return@withContext null
        try {
            val show = db.shows().get(sourceId, seriesId)
            val body = readText(XtreamUrls.seriesInfo(login.server, login.user, login.pass, seriesId))
            val info = XtreamSeries.showInfo(body, show?.name) ?: return@withContext null
            synchronized(infoCache) { infoCache[key] = info to now() }
            db.shows().fillMissing(sourceId, seriesId, info.plot, info.genre, info.year, info.rating, info.coverUrl)
            info
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            null
        }
    }

    /** Shows the viewer is part-way through, most recent first. Only shows still listed; progress is never deleted here. */
    suspend fun continueWatching(sourceId: Long): List<Pair<ShowEntity, ShowProgressEntity>> = withContext(Dispatchers.IO) {
        val points = db.showProgress().recent(sourceId)
        if (points.isEmpty()) return@withContext emptyList()
        val byId = db.shows().getMany(sourceId, points.map { it.seriesId }).associateBy { it.seriesId }
        // A show the provider dropped is hidden here; its progress row stays (see VodRefresh.continueTiles).
        VodRefresh.continueTiles(points, byId, { it.seriesId }, CONTINUE_MAX).map { (point, show) -> show to point }
    }

    suspend fun progress(sourceId: Long, seriesId: Long): ShowProgressEntity? = withContext(Dispatchers.IO) {
        db.showProgress().get(sourceId, seriesId)
    }

    /** Records a watch. [episodes] are the whole show in play order, so a finished episode can move on to the next. */
    suspend fun saveWatch(
        sourceId: Long, seriesId: Long, episodes: List<XtreamEpisode>, episode: XtreamEpisode,
        positionMs: Long, durationMs: Long,
    ) = withContext(Dispatchers.IO) {
        when (val change = Shows.afterWatch(episodes, episode.id, positionMs, durationMs)) {
            is ShowProgressChange.Keep ->
                db.showProgress().put(entry(sourceId, seriesId, episode, change.positionMs, change.durationMs))
            is ShowProgressChange.Next ->
                db.showProgress().put(entry(sourceId, seriesId, change.episode, 0L, (change.episode.durationSecs ?: 0) * 1000L))
            ShowProgressChange.Clear -> db.showProgress().delete(sourceId, seriesId)
            ShowProgressChange.Ignore -> Unit
        }
    }

    private fun entry(sourceId: Long, seriesId: Long, episode: XtreamEpisode, positionMs: Long, durationMs: Long) =
        ShowProgressEntity(
            sourceId, seriesId, episode.id, episode.season, episode.number, episode.title,
            episode.ext, positionMs, durationMs, now(),
        )

    /** The background full list. At most once a day per source; the Series tab calls it after it has drawn. */
    suspend fun syncTitles(sourceId: Long) {
        val key = "shows_titles_at_$sourceId"
        if (now() - prefs.getLong(key, 0L) < DAY) return
        if (!syncing.compareAndSet(false, true)) return
        try {
            withContext(importDispatcher) {
                val login = login(sourceId) ?: return@withContext
                try {
                    val fetched = download(XtreamUrls.seriesList(login.server, login.user, login.pass, null), sourceId, null)
                    if (fetched.rows > 0) prefs.edit().putLong(key, now()).apply()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    // Try again next time the tab opens.
                }
            }
        } finally {
            syncing.set(false)
        }
    }

    suspend fun search(sourceId: Long, needle: String): List<ShowEntity> = withContext(Dispatchers.IO) {
        val text = needle.trim()
        if (text.isEmpty()) return@withContext emptyList()
        val escaped = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val exact = db.shows().searchName(sourceId, escaped, FilmSearch.LIMIT)
        if (exact.isNotEmpty() || !FuzzyText.worthTrying(text)) return@withContext exact
        val (ids, names) = synchronized(nameCache) {
            nameCache.getOrPut(sourceId) {
                val all = db.shows().names(sourceId)
                LongArray(all.size) { all[it].seriesId } to all.map { it.name }
            }
        }
        val hits = FilmSearch.find(names, text).map { ids[it] }
        if (hits.isEmpty()) return@withContext emptyList()
        val byId = db.shows().getMany(sourceId, hits).associateBy { it.seriesId }
        hits.mapNotNull { byId[it] }
    }

    fun playUrl(source: SourceEntity, episodeId: Long, ext: String?): String? {
        val login = login(source) ?: return null
        return XtreamUrls.episode(login.server, login.user, login.pass, episodeId, ext)
    }

    /**
     * Downloads to a temp file first (no database transaction is open meanwhile), then parses the file and
     * writes about 1,000 rows per short transaction. [categoryId] null means the whole library.
     * Returns the rows written, whether the reply was a list at all, and the stamp every written row carries.
     * Throws when the download fails, is cancelled or is cut off, so a caller never prunes after a partial one.
     */
    private suspend fun download(url: String, sourceId: Long, categoryId: String?): Fetched {
        clearLeftovers()
        val file = File.createTempFile("series", ".json", cacheDir)
        try {
            http.open(url).use { input ->
                file.outputStream().use { out ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                    }
                }
            }
            val wasList = file.inputStream().buffered().use { XtreamVod.isList(it) }
            val stamp = now()
            var written = 0
            val unread = ArrayList<Long>()
            file.inputStream().buffered().use { input ->
                XtreamSeries.showRows(input) { unread += it }.chunked(BATCH).forEach { batch ->
                    coroutineContext.ensureActive()
                    write(sourceId, categoryId, batch, stamp)
                    written += batch.size
                }
            }
            // Listed but unreadable this time: keep the stored row rather than let pruning drop it.
            // The full list (no category) never prunes, so there is nothing to keep.
            if (categoryId != null) unread.chunked(ID_CHUNK).forEach { db.shows().stillListed(sourceId, categoryId, it, stamp) }
            synchronized(nameCache) { nameCache.remove(sourceId) }
            return Fetched(written, wasList, stamp)
        } finally {
            file.delete()
        }
    }

    /** Temp files an earlier crash left behind. */
    private fun clearLeftovers() {
        val old = now() - 60L * 60 * 1000
        cacheDir.listFiles { f -> f.name.startsWith("series") && f.name.endsWith(".json") && f.lastModified() < old }
            ?.forEach { it.delete() }
    }

    private fun write(sourceId: Long, categoryId: String?, batch: List<XtreamShow>, stamp: Long) {
        val rows = batch.map {
            ShowEntity(
                sourceId = sourceId, seriesId = it.seriesId, name = it.name,
                categoryId = it.categoryId.ifBlank { categoryId.orEmpty() },
                coverUrl = it.coverUrl, rating = it.rating, year = it.year, plot = it.plot, genre = it.genre,
                addedAt = it.addedAt, listedAt = stamp,
            )
        }
        db.runInTransaction {
            val results = db.shows().insertNew(rows)
            rows.forEachIndexed { index, row ->
                if (results[index] == -1L) {
                    db.shows().refresh(
                        sourceId, row.seriesId, row.name, row.categoryId, row.coverUrl, row.addedAt,
                        row.rating, row.year, row.plot, row.genre, stamp,
                    )
                }
            }
            // A title is in every category the provider lists it under (VodRefresh.listedUnder).
            // A blank listing (full list, no category) is only a placeholder so the title has one; it is not added
            // beside real listings, or it would stop pruning from ever dropping the title.
            val (blank, listings) = batch.flatMap { item ->
                VodRefresh.listedUnder(categoryId, item.categoryId).map { ShowListingEntity(sourceId, it, item.seriesId, stamp) }
            }.partition { it.categoryId.isEmpty() }
            blank.forEach { db.shows().addBlankIfUnlisted(sourceId, it.seriesId, stamp) }
            val added = db.shows().insertListings(listings)
            listings.forEachIndexed { index, listing ->
                if (added[index] == -1L) db.shows().relist(sourceId, listing.categoryId, listing.seriesId, stamp)
            }
            // A category download files a title properly, so a blank listing from the full list can go.
            if (categoryId != null) batch.map { it.seriesId }.chunked(ID_CHUNK).forEach { db.shows().dropBlankListings(sourceId, it) }
        }
    }

    private class Fetched(val rows: Int, val wasList: Boolean, val stamp: Long)

    private fun readText(url: String): String {
        http.open(url).use { input -> return readBounded(input) }
    }

    private fun readBounded(input: InputStream): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > 16 * 1024 * 1024) throw IllegalStateException("The reply is too large")
            out.write(buffer, 0, read)
        }
        return out.toString("UTF-8")
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
        const val BATCH = 1000
        // Older Android SQLite allows 999 values per query; the update adds two more.
        const val ID_CHUNK = 500
        const val CONTINUE_MAX = 20
        const val INFO_KEPT = 8
        const val INFO_FRESH = 60L * 60 * 1000
    }
}
