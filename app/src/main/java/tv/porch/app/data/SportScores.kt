package tv.porch.app.data

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.LocalDate
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tv.porch.app.domain.Ladder
import tv.porch.app.domain.LadderSource
import tv.porch.app.domain.SportLeague
import tv.porch.app.domain.SportResult
import tv.porch.app.domain.SportRules
import tv.porch.app.domain.SportScoreRules
import tv.porch.app.parse.EspnLadders
import tv.porch.app.parse.EspnResults

@Serializable
private data class ResultsCopy(val fetchedAt: Long, val results: List<SportResult>)

@Serializable
private data class SavedResults(val v: Int = VERSION, val leagues: Map<String, ResultsCopy> = emptyMap()) {
    companion object {
        const val VERSION = 1
    }
}

@Serializable
private data class LaddersCopy(val fetchedAt: Long, val ladders: List<Ladder>)

@Serializable
private data class SavedLadders(val v: Int = VERSION, val leagues: Map<String, LaddersCopy> = emptyMap()) {
    companion object {
        const val VERSION = 1
    }
}

/**
 * Results and ladders from ESPN, kept apart from the schedule. Same rules as [SportSchedule]: one league at a time,
 * the last good copy of each league saved, a failed league keeps its old copy, a copy under an hour old is not asked
 * for again. Never logs an address or a result.
 */
class SportScores(
    private val http: HttpFetcher,
    private val resultsFile: File,
    private val laddersFile: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** [failed] holds chip names in chip order. [updatedAt] is when the newest copy was made, null with no copy. */
    data class Results(val results: List<SportResult>, val failed: List<String>, val allFailed: Boolean, val updatedAt: Long?)
    data class Ladders(val ladders: List<Ladder>, val failed: List<String>, val allFailed: Boolean, val updatedAt: Long?)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val resultsLock = Mutex()
    private val laddersLock = Mutex()

    private fun readResults(): SavedResults = runCatching {
        json.decodeFromString<SavedResults>(resultsFile.readText()).takeIf { it.v == SavedResults.VERSION }
    }.getOrNull() ?: SavedResults()

    private fun readLadders(): SavedLadders = runCatching {
        json.decodeFromString<SavedLadders>(laddersFile.readText()).takeIf { it.v == SavedLadders.VERSION }
    }.getOrNull() ?: SavedLadders()

    private fun resultsOf(saved: SavedResults, failed: List<String>, allFailed: Boolean) = Results(
        SportRules.leagues.flatMap { saved.leagues[it.path]?.results.orEmpty() }, failed, allFailed, saved.leagues.values.maxOfOrNull { it.fetchedAt },
    )

    private fun laddersOf(saved: SavedLadders, failed: List<String>, allFailed: Boolean) = Ladders(
        SportRules.leagues.flatMap { saved.leagues[it.path]?.ladders.orEmpty() }, failed, allFailed, saved.leagues.values.maxOfOrNull { it.fetchedAt },
    )

    /**
     * The saved results as they are, or null when there are none. Not pruned: the 7-day filter in
     * [SportScoreRules.resultSections] is what the UI relies on. Blocking file read: call off the main thread. */
    fun savedResults(): Results? = readResults().takeIf { it.leagues.isNotEmpty() }?.let { resultsOf(it, emptyList(), false) }

    /** The saved ladders as they are, or null when there are none. Blocking file read: call off the main thread. */
    fun savedLadders(): Ladders? = readLadders().takeIf { it.leagues.isNotEmpty() }?.let { laddersOf(it, emptyList(), false) }

    /** Asks for every league whose copy is missing or an hour old ([force] asks for all). Saves each league as it finishes. */
    suspend fun refreshResults(force: Boolean = false, onProgress: (Results) -> Unit = {}): Results = resultsLock.withLock {
        withContext(Dispatchers.IO) {
            val now = clock()
            val kept = readResults().leagues.toMutableMap()
            val failed = mutableListOf<String>()
            var asked = 0
            var failures = 0
            for (league in SportRules.leagues) {
                val copy = kept[league.path]
                if (!force && copy != null && now - copy.fetchedAt < SportRules.FRESH_MS) continue
                asked++
                val loaded = try {
                    fetchResults(league, now, copy?.results.orEmpty())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    null
                }
                ensureActive()
                if (loaded == null) {
                    failures++
                    if (league.chip !in failed) failed += league.chip
                } else {
                    kept[league.path] = ResultsCopy(now, loaded.filter { it.playedMs >= now - 8 * 86_400_000L })
                    writeAtomic(resultsFile, json.encodeToString(SavedResults.serializer(), SavedResults(leagues = kept)))
                }
                onProgress(resultsOf(SavedResults(leagues = kept), failed.toList(), false))
            }
            resultsOf(SavedResults(leagues = kept), failed, asked > 0 && failures == asked)
        }
    }

    /** Like [refreshResults] for ladders. A league with no ladder is never asked. */
    suspend fun refreshLadders(force: Boolean = false, onProgress: (Ladders) -> Unit = {}): Ladders = laddersLock.withLock {
        withContext(Dispatchers.IO) {
            val now = clock()
            val kept = readLadders().leagues.toMutableMap()
            val failed = mutableListOf<String>()
            var asked = 0
            var failures = 0
            for (league in SportRules.leagues) {
                val source = SportScoreRules.ladderSource(league)
                if (source == LadderSource.None) continue
                val copy = kept[league.path]
                if (!force && copy != null && now - copy.fetchedAt < SportRules.FRESH_MS) continue
                asked++
                val loaded = try {
                    val url = if (source == LadderSource.Rankings) SportScoreRules.rankingsUrl(league) else SportScoreRules.standingsUrl(league)
                    val text = http.open(url).use { text(it) }
                    val ladders = if (source == LadderSource.Rankings) EspnLadders.rankings(text, league) else EspnLadders.standings(text, league)
                    // No tables means ESPN changed shape: a failure, so the old copy stays.
                    ladders.takeIf { it.isNotEmpty() }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    null
                }
                ensureActive()
                if (loaded == null) {
                    failures++
                    if (league.chip !in failed) failed += league.chip
                } else {
                    kept[league.path] = LaddersCopy(now, loaded)
                    writeAtomic(laddersFile, json.encodeToString(SavedLadders.serializer(), SavedLadders(leagues = kept)))
                }
                onProgress(laddersOf(SavedLadders(leagues = kept), failed.toList(), false))
            }
            laddersOf(SavedLadders(leagues = kept), failed, asked > 0 && failures == asked)
        }
    }

    private fun fetchResults(league: SportLeague, now: Long, old: List<SportResult>): List<SportResult> {
        if (league.byRange) return load(SportScoreRules.resultRangeUrl(league, now), league, now).results
        // A failed plain reply counts as empty; with no calendar every day of the past window is asked.
        val plain = try {
            load(SportRules.plainUrl(league), league, now)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            null
        }
        val all = plain?.results.orEmpty().toMutableList()
        val days = if (plain != null) SportScoreRules.pastDaysToAsk(plain.calendar, now) else {
            val (from, to) = SportScoreRules.pastWindow(now)
            generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.toList()
        }
        val failedDays = mutableListOf<LocalDate>()
        for (day in days) {
            try {
                all += load(SportRules.dayUrl(league, day), league, now).results
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failedDays += day
            }
        }
        if ((plain == null || days.isNotEmpty()) && failedDays.size == days.size && all.isEmpty()) throw IOException("every day failed")
        // A day that failed keeps what the old copy had for it.
        if (failedDays.isNotEmpty()) all += old.filter { SportRules.espnDay(it.playedMs) in failedDays }
        return all.distinctBy { it.id }
    }

    private fun load(url: String, league: SportLeague, now: Long): EspnResults.Parsed =
        EspnResults.parse(http.open(url).use { text(it) }, league, now)

    /** ESPN sometimes sends gzip without being asked, so the first two bytes decide. */
    private fun text(input: InputStream): String {
        val bytes = input.readBytes()
        val zipped = bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
        val source: InputStream = if (zipped) GZIPInputStream(ByteArrayInputStream(bytes)) else ByteArrayInputStream(bytes)
        return source.use { it.readBytes().decodeToString() }
    }

    private fun writeAtomic(file: File, content: String) {
        runCatching {
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(content)
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        }
    }
}
