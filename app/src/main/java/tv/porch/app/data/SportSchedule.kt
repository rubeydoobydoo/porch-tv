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
import tv.porch.app.domain.SportFixture
import tv.porch.app.domain.SportLeague
import tv.porch.app.domain.SportRules
import tv.porch.app.parse.EspnScoreboard

@Serializable
private data class LeagueCopy(val fetchedAt: Long, val fixtures: List<SportFixture>)

@Serializable
private data class SavedSport(val v: Int = VERSION, val leagues: Map<String, LeagueCopy> = emptyMap()) {
    companion object {
        const val VERSION = 1
    }
}

/**
 * The sport schedule from ESPN's public scoreboards. Each league is asked for on its own, one request at a time.
 * The last good copy of every league lives in [file]; a league that fails keeps its old copy, and a league whose copy
 * is under an hour old is not asked for. Nothing here reads a score. Never logs an address.
 */
class SportSchedule(
    private val http: HttpFetcher,
    private val file: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** [failed] holds chip names in chip order. [updatedAt] is when the newest copy was made, null with no copy. */
    data class State(val fixtures: List<SportFixture>, val failed: List<String>, val allFailed: Boolean, val updatedAt: Long?)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Mutex()

    private fun read(): SavedSport = runCatching {
        json.decodeFromString<SavedSport>(file.readText()).takeIf { it.v == SavedSport.VERSION }
    }.getOrNull() ?: SavedSport()

    private fun stateOf(saved: SavedSport, failed: List<String>, allFailed: Boolean): State {
        val fixtures = SportRules.leagues.flatMap { saved.leagues[it.path]?.fixtures.orEmpty() }
        return State(fixtures, failed, allFailed, saved.leagues.values.maxOfOrNull { it.fetchedAt })
    }

    /** The saved copy as it is, or null when there is none. Blocking file read: call off the main thread. */
    fun saved(): State? = read().takeIf { it.leagues.isNotEmpty() }?.let { stateOf(it, emptyList(), false) }

    /**
     * Asks for every league whose copy is missing or an hour old ([force] asks for all). Merges per league: each league
     * is saved and handed to [onProgress] (on a background thread) as soon as it finishes, so a cancelled round keeps
     * what it already saved.
     */
    suspend fun refresh(force: Boolean = false, onProgress: (State) -> Unit = {}): State = lock.withLock {
        withContext(Dispatchers.IO) {
            val now = clock()
            val kept = read().leagues.toMutableMap()
            val failed = mutableListOf<String>()
            var failures = 0
            for (league in SportRules.leagues) {
                val copy = kept[league.path]
                if (!force && copy != null && now - copy.fetchedAt < SportRules.FRESH_MS) continue
                val loaded = try {
                    fetch(league, now, copy?.fixtures.orEmpty())
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
                    kept[league.path] = LeagueCopy(now, loaded)
                    write(SavedSport(leagues = kept))
                }
                onProgress(stateOf(SavedSport(leagues = kept), failed.toList(), false))
            }
            stateOf(SavedSport(leagues = kept), failed, failures == SportRules.leagues.size)
        }
    }

    private fun fetch(league: SportLeague, now: Long, old: List<SportFixture>): List<SportFixture> {
        if (league.byRange) return load(SportRules.rangeUrl(league, now), league).fixtures
        val plain = load(SportRules.plainUrl(league), league)
        val all = plain.fixtures.toMutableList()
        val days = SportRules.daysToAsk(plain.calendar, now)
        val failedDays = mutableListOf<LocalDate>()
        for (day in days) {
            // One day that will not load is skipped; the rest of the league still counts.
            try {
                all += load(SportRules.dayUrl(league, day), league).fixtures
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failedDays += day
            }
        }
        // Every day failing with nothing from the plain reply is a failed league, so it is retried at the next open.
        if (days.isNotEmpty() && failedDays.size == days.size && all.isEmpty()) throw IOException("every day failed")
        // A day that failed keeps what the old copy had for it; a day that loaded is believed, even when it is empty now.
        if (failedDays.isNotEmpty()) all += old.filter { SportRules.espnDay(it.startMs) in failedDays }
        return all.distinctBy { it.id }
    }

    private fun load(url: String, league: SportLeague): EspnScoreboard.Parsed =
        EspnScoreboard.parse(http.open(url).use { text(it) }, league)

    /** ESPN sometimes sends gzip without being asked, so the first two bytes decide. */
    private fun text(input: InputStream): String {
        val bytes = input.readBytes()
        val zipped = bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
        val source: InputStream = if (zipped) GZIPInputStream(ByteArrayInputStream(bytes)) else ByteArrayInputStream(bytes)
        return source.use { it.readBytes().decodeToString() }
    }

    private fun write(saved: SavedSport) {
        runCatching {
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(json.encodeToString(SavedSport.serializer(), saved))
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        }
    }
}
