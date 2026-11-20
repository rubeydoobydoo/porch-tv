package tv.porch.app.data

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tv.porch.app.domain.Channel
import tv.porch.app.domain.HomeLists
import tv.porch.app.domain.LiveRows

/** One saved row of Home: the channel (just enough to draw it and play it by id) and the programme on it. */
@Serializable
data class SavedRow(
    val id: Long,
    val name: String,
    val title: String,
    val logo: String? = null,
    val group: String = "",
    val favourite: Boolean = false,
    val programme: String,
    val programmeId: Long = 0L,
    val start: Long,
    val stop: Long,
    val match: Boolean = false,
)

@Serializable
data class SavedList(val rows: List<SavedRow> = emptyList(), val more: Int = 0)

/** What Home last showed, small enough to read in a blink and draw before the database has answered. */
@Serializable
data class SavedHome(
    val v: Int = VERSION,
    val sport: SavedList = SavedList(),
    val yours: SavedList = SavedList(),
    val soon: SavedList = SavedList(),
    /** Absent in older files, which read as no follows. */
    val following: SavedList = SavedList(),
) {
    fun isEmpty(): Boolean = sport.rows.isEmpty() && yours.rows.isEmpty() && soon.rows.isEmpty() && following.rows.isEmpty()

    /**
     * The lists to draw at [now]: rows whose programme has ended are dropped, and so are starting-soon rows that have
     * started (they now belong on the other lists, which the database will say). Null when nothing is left.
     */
    fun live(now: Long): HomeLists.Lists? {
        val lists = HomeLists.Lists(
            sport = section(sport, now, dropStarted = false),
            favourites = HomeLists.Section(emptyList()),
            recent = HomeLists.Section(emptyList()),
            soon = section(soon, now, dropStarted = true),
            yours = section(yours, now, dropStarted = false),
            following = section(following, now, dropStarted = false),
        )
        val none = lists.sport.all.isEmpty() && lists.soon.all.isEmpty() && lists.yours.all.isEmpty() && lists.following.all.isEmpty()
        return if (none) null else lists
    }

    private fun section(list: SavedList, now: Long, dropStarted: Boolean): HomeLists.Section {
        val rows = list.rows
            .filterNot { it.stop in 1..now }
            .filterNot { dropStarted && it.start <= now }
            .map { it.toRow() }
        // "See all" is only believable while the list still fills Home.
        return HomeLists.Section(rows, more = if (rows.size >= HomeLists.ROWS) list.more else 0)
    }

    companion object {
        const val VERSION = 1

        /** A few rows past what Home shows, so dropping an ended one still leaves Home full. */
        const val SPARES = 3

        fun from(lists: HomeLists.Lists): SavedHome = SavedHome(
            sport = save(lists.sport),
            yours = save(lists.yours),
            soon = save(lists.soon),
            following = save(lists.following),
        )

        private fun save(section: HomeLists.Section) =
            SavedList(section.all.take(HomeLists.ROWS + SPARES).map { it.toSaved() }, section.more)
    }
}

private fun LiveRows.Row.toSaved(): SavedRow {
    val channel = channels.first()
    return SavedRow(
        id = channel.id,
        name = channel.name,
        title = channel.shownTitle,
        logo = channel.logoUrl,
        group = channel.group,
        favourite = channel.favourite,
        programme = title,
        programmeId = programmeId,
        start = startMs,
        stop = endMs,
        match = match,
    )
}

/** A stand-in channel: enough to draw the row and to play by id. Playing and the menus look the channel up afresh. */
private fun SavedRow.toRow(): LiveRows.Row {
    val channel = Channel(
        id = id, sourceId = 0L, name = name, number = 0, group = group, favourite = favourite,
        streamUrl = "", xtreamStreamId = null, archiveDays = 0, matchedXmltvId = null, lastPlayedAt = null,
        logoUrl = logo, title = title,
    )
    return LiveRows.Row(programme, start, stop, listOf(channel), match = match, programmeId = programmeId)
}

/**
 * Home's last lists in one small JSON file in the app's private files. Writes go through [scope] (never the main
 * thread), at most one a minute, to a temporary file that is then renamed over the real one. A missing or broken file
 * reads as nothing.
 */
class HomeSnapshot(private val file: File, private val scope: CoroutineScope) {
    private val temp = File(file.parentFile, file.name + ".tmp")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val ioLock = Any()
    private val lock = Any()
    private var epoch = 0
    private var lastWriteAt = 0L
    private var lastWritten: SavedHome? = null
    private var pending: SavedHome? = null
    private var trailing: Job? = null

    fun read(): SavedHome? = runCatching {
        json.decodeFromString<SavedHome>(file.readText()).takeIf { it.v == SavedHome.VERSION }
    }.getOrNull()

    /** Writes now, on this thread, atomically. Callers on the main thread use [offer] instead. */
    fun write(saved: SavedHome) {
        synchronized(ioLock) { put(saved) }
    }

    /** Forgets everything: the file and anything waiting to be written. */
    fun delete() {
        synchronized(lock) {
            epoch++
            pending = null
            lastWritten = null
            lastWriteAt = 0L
        }
        synchronized(ioLock) {
            file.delete()
            temp.delete()
        }
    }

    /** Home built [lists]. Saves them in the background, but not more than once a minute and never an empty Home. */
    fun offer(lists: HomeLists.Lists, now: Long = System.currentTimeMillis()) {
        val saved = SavedHome.from(lists)
        if (saved.isEmpty()) return
        synchronized(lock) {
            if (saved == lastWritten) {
                pending = null
                return
            }
            val wait = (lastWriteAt + MIN_GAP_MS - now).coerceAtMost(MIN_GAP_MS)
            if (wait <= 0L) {
                pending = null
                save(saved, now)
            } else {
                pending = saved
                if (trailing == null) {
                    val mine = epoch
                    trailing = scope.launch {
                        delay(wait)
                        synchronized(lock) {
                            trailing = null
                            val next = pending
                            pending = null
                            if (next != null && mine == epoch) save(next, System.currentTimeMillis())
                        }
                    }
                }
            }
        }
    }

    // Called with [lock] held. Marks the time now, and does the file work on the background scope.
    private fun save(saved: SavedHome, now: Long) {
        lastWriteAt = now
        lastWritten = saved
        val mine = epoch
        scope.launch {
            synchronized(ioLock) {
                if (mine == synchronized(lock) { epoch }) put(saved)
            }
        }
    }

    private fun put(saved: SavedHome) {
        runCatching {
            temp.writeText(json.encodeToString(SavedHome.serializer(), saved))
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        }
    }

    private companion object {
        const val MIN_GAP_MS = 60_000L
    }
}
