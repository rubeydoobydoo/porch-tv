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
import tv.porch.app.domain.MovieShelf
import tv.porch.app.domain.VodRefresh
import tv.porch.app.parse.XtreamFilm
import tv.porch.app.parse.XtreamMapper
import tv.porch.app.parse.XtreamUrls
import tv.porch.app.parse.XtreamVod

/** A film as the Movies tab reads it. */
typealias Film = FilmEntity

/**
 * Movies for an Xtream source. Opening a shelf never waits for the whole library: a shelf downloads only its
 * own categories, and the full title list fills in later on a background thread ([syncTitles]).
 * Any other source kind gives empty results. Nothing here runs at app start.
 */
class MovieRepository(
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
            }, "porch-movies").apply { isDaemon = true }
        }.asCoroutineDispatcher()
    }

    private val syncing = AtomicBoolean(false)

    /**
     * Forgets when this source's categories and titles were last fetched, so the next visit asks the provider again. Called
     * when an edit changes the server or username: the old account's titles then leave as their categories refresh.
     */
    fun forgetFetched(sourceId: Long) {
        val keys = prefs.all.keys.filter { key ->
            key == "movies_cats_v2_at_$sourceId" || key == "movies_titles_at_$sourceId" || key.startsWith("movies_cat_at_${sourceId}_")
        }
        if (keys.isNotEmpty()) prefs.edit().apply { keys.forEach { remove(it) } }.apply()
    }
    private val nameCache = HashMap<Long, Pair<LongArray, List<String>>>()

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
        val key = "movies_cats_v2_at_$sourceId"
        val stored = db.filmCategories().list(sourceId)
        val stale = stored.isEmpty() || now() - prefs.getLong(key, 0L) > DAY
        if (stale) {
            try {
                val body = readText(XtreamUrls.vodCategories(login.server, login.user, login.pass))
                val fresh = XtreamMapper.categories(body)
                // Movies has no category chooser, so each refresh picks again with the current rule.
                val shelfOf = Films.shelves(fresh.map { it.categoryId to it.categoryName }, country())
                    .flatMap { shelf -> shelf.categoryIds.map { it to shelf } }.toMap()
                val rows = fresh.mapIndexed { index, item ->
                    val shelf = shelfOf[item.categoryId]
                    FilmCategoryEntity(sourceId, item.categoryId, item.categoryName, shelf?.title ?: "", shelf?.chosen ?: false, index)
                }
                // An empty list is suspect: keep the stored rows and ask again on the next open.
                if (VodRefresh.replaceCategories(rows.size)) {
                    db.runInTransaction {
                        db.filmCategories().deleteBySource(sourceId)
                        db.filmCategories().insertAll(rows)
                    }
                    prefs.edit().putLong(key, now()).apply()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Offline or refused: whatever the table has.
            }
        }
        val rows = db.filmCategories().list(sourceId).filter { it.shownName.isNotEmpty() }
        // A picked and an unpicked category with the same name stay on separate shelves (see Films.shelves).
        val order = LinkedHashMap<Pair<String, Boolean>, MutableList<FilmCategoryEntity>>()
        rows.forEach { order.getOrPut(it.shownName to it.chosen) { mutableListOf() }.add(it) }
        order.map { (key, list) -> MovieShelf(key.first, list.map { it.categoryId }, key.second) }
            .let { Films.shown(it) }
    }

    suspend fun films(sourceId: Long, shelf: MovieShelf, limit: Int = 500): List<Film> = withContext(Dispatchers.IO) {
        val login = login(sourceId) ?: return@withContext emptyList()
        for (id in shelf.categoryIds) {
            val key = "movies_cat_at_v2_${sourceId}_$id"
            if (now() - prefs.getLong(key, 0L) < DAY) continue
            try {
                val fetched = download(XtreamUrls.vodStreams(login.server, login.user, login.pass, id), sourceId, id)
                val after = VodRefresh.afterCategory(fetched.rows, fetched.wasList)
                if (after.prune) {
                    // Only now, with the whole category written: rows it did not list still have an older stamp.
                    db.runInTransaction {
                        db.films().dropOnlyListedIn(sourceId, id, fetched.stamp)
                        db.films().dropListings(sourceId, id, fetched.stamp)
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
        db.films().inCategories(sourceId, shelf.categoryIds, limit)
    }

    suspend fun details(sourceId: Long, streamId: Long): Film? = withContext(Dispatchers.IO) {
        val film = db.films().get(sourceId, streamId) ?: return@withContext null
        if (film.detailsAt != null) return@withContext film
        val login = login(sourceId) ?: return@withContext film
        try {
            val body = readText(XtreamUrls.vodInfo(login.server, login.user, login.pass, streamId))
            val info = XtreamVod.filmInfo(body, film.name) ?: return@withContext film
            db.films().saveDetails(
                sourceId, streamId, info.plot, info.durationSecs, info.genre,
                info.year, info.rating, info.iconUrl, now(),
            )
            db.films().get(sourceId, streamId) ?: film
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            film
        }
    }

    suspend fun continueWatching(sourceId: Long): List<Film> = withContext(Dispatchers.IO) {
        val points = db.resumePoints().recent(sourceId).filter { Films.started(it.positionMs, it.durationMs) }
        if (points.isEmpty()) return@withContext emptyList()
        val byId = db.films().getMany(sourceId, points.map { it.streamId }).associateBy { it.streamId }
        // A film the provider dropped is hidden here; its resume point stays (see VodRefresh.continueTiles).
        VodRefresh.continueTiles(points, byId, { it.streamId }, CONTINUE_MAX).map { it.second }
    }

    suspend fun resumeAt(sourceId: Long, streamId: Long): Long? = withContext(Dispatchers.IO) {
        db.resumePoints().get(sourceId, streamId)?.takeIf { Films.started(it.positionMs, it.durationMs) }?.positionMs
    }

    /** The resume point of a started film, or null. */
    suspend fun resumePoint(sourceId: Long, streamId: Long): ResumePointEntity? = withContext(Dispatchers.IO) {
        db.resumePoints().get(sourceId, streamId)?.takeIf { Films.started(it.positionMs, it.durationMs) }
    }

    suspend fun saveResume(sourceId: Long, streamId: Long, positionMs: Long, durationMs: Long) = withContext(Dispatchers.IO) {
        if (durationMs <= 0L && positionMs <= 0L) return@withContext
        if (Films.finished(positionMs, durationMs)) {
            db.resumePoints().delete(sourceId, streamId)
        } else {
            db.resumePoints().put(ResumePointEntity(sourceId, streamId, positionMs, durationMs, now()))
        }
    }

    /** The background full list. At most once a day per source; the Movies tab calls it after it has drawn. */
    suspend fun syncTitles(sourceId: Long) {
        val key = "movies_titles_at_$sourceId"
        if (now() - prefs.getLong(key, 0L) < DAY) return
        if (!syncing.compareAndSet(false, true)) return
        try {
            withContext(importDispatcher) {
                val login = login(sourceId) ?: return@withContext
                try {
                    val fetched = download(XtreamUrls.vodStreams(login.server, login.user, login.pass, null), sourceId, null)
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

    suspend fun search(sourceId: Long, needle: String): List<Film> = withContext(Dispatchers.IO) {
        val text = needle.trim()
        if (text.isEmpty()) return@withContext emptyList()
        val escaped = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val exact = db.films().searchName(sourceId, escaped, FilmSearch.LIMIT)
        if (exact.isNotEmpty() || !tv.porch.app.domain.FuzzyText.worthTrying(text)) return@withContext exact
        val (ids, names) = synchronized(nameCache) {
            nameCache.getOrPut(sourceId) {
                val all = db.films().names(sourceId)
                LongArray(all.size) { all[it].streamId } to all.map { it.name }
            }
        }
        val hits = FilmSearch.find(names, text).map { ids[it] }
        if (hits.isEmpty()) return@withContext emptyList()
        val byId = db.films().getMany(sourceId, hits).associateBy { it.streamId }
        hits.mapNotNull { byId[it] }
    }

    fun playUrl(source: SourceEntity, film: Film): String? {
        val login = login(source) ?: return null
        return XtreamUrls.movie(login.server, login.user, login.pass, film.streamId, film.ext)
    }

    /**
     * Downloads to a temp file first (no database transaction is open meanwhile), then parses the file and
     * writes about 1,000 rows per short transaction. [categoryId] null means the whole library.
     * Returns the rows written, whether the reply was a list at all, and the stamp every written row carries.
     * Throws when the download fails, is cancelled or is cut off, so a caller never prunes after a partial one.
     */
    private suspend fun download(url: String, sourceId: Long, categoryId: String?): Fetched {
        clearLeftovers()
        val file = File.createTempFile("vod", ".json", cacheDir)
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
                XtreamVod.filmRows(input) { unread += it }.chunked(BATCH).forEach { batch ->
                    coroutineContext.ensureActive()
                    write(sourceId, categoryId, batch, stamp)
                    written += batch.size
                }
            }
            // Listed but unreadable this time: keep the stored row rather than let pruning drop it.
            // The full list (no category) never prunes, so there is nothing to keep.
            if (categoryId != null) unread.chunked(ID_CHUNK).forEach { db.films().stillListed(sourceId, categoryId, it, stamp) }
            synchronized(nameCache) { nameCache.remove(sourceId) }
            return Fetched(written, wasList, stamp)
        } finally {
            file.delete()
        }
    }

    /** Temp files an earlier crash left behind. */
    private fun clearLeftovers() {
        val old = now() - 60L * 60 * 1000
        cacheDir.listFiles { f -> f.name.startsWith("vod") && f.name.endsWith(".json") && f.lastModified() < old }
            ?.forEach { it.delete() }
    }

    private fun write(sourceId: Long, categoryId: String?, batch: List<XtreamFilm>, stamp: Long) {
        val rows = batch.map {
            FilmEntity(
                sourceId = sourceId, streamId = it.streamId, name = it.name,
                categoryId = it.categoryId.ifBlank { categoryId.orEmpty() },
                iconUrl = it.iconUrl, ext = it.ext, addedAt = it.addedAt, rating = it.rating, year = it.year,
                listedAt = stamp,
            )
        }
        db.runInTransaction {
            val results = db.films().insertNew(rows)
            rows.forEachIndexed { index, row ->
                if (results[index] == -1L) {
                    db.films().refresh(
                        sourceId, row.streamId, row.name, row.categoryId, row.iconUrl, row.ext,
                        row.addedAt, row.rating, row.year, stamp,
                    )
                }
            }
            // A title is in every category the provider lists it under (VodRefresh.listedUnder).
            // A blank listing (full list, no category) is only a placeholder so the title has one; it is not added
            // beside real listings, or it would stop pruning from ever dropping the title.
            val (blank, listings) = batch.flatMap { item ->
                VodRefresh.listedUnder(categoryId, item.categoryId).map { FilmListingEntity(sourceId, it, item.streamId, stamp) }
            }.partition { it.categoryId.isEmpty() }
            blank.forEach { db.films().addBlankIfUnlisted(sourceId, it.streamId, stamp) }
            val added = db.films().insertListings(listings)
            listings.forEachIndexed { index, listing ->
                if (added[index] == -1L) db.films().relist(sourceId, listing.categoryId, listing.streamId, stamp)
            }
            // A category download files a title properly, so a blank listing from the full list can go.
            if (categoryId != null) batch.map { it.streamId }.chunked(ID_CHUNK).forEach { db.films().dropBlankListings(sourceId, it) }
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
    }
}
