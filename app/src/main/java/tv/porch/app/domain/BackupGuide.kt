package tv.porch.app.domain

import java.util.Locale

/**
 * What outside guide files have claimed so far on one refresh. [assigned] is channel identity to backup key.
 * [seenIds] holds every guide id an earlier file listed, so the first file wins.
 */
data class BackupPlan(val assigned: Map<String, String> = emptyMap(), val seenIds: Set<String> = emptySet()) {
    /** The keys this plan gained after [before]: the only ones the file being read may write. */
    fun keysAddedSince(before: BackupPlan): Set<String> = assigned.values.toSet() - before.assigned.values.toSet()

    /** Drops matches whose key has no programmes, so a listed but empty channel keeps what it had. */
    fun keeping(written: Set<String>): BackupPlan = copy(assigned = assigned.filterValues { it in written })
}

/**
 * Decides which channels use an outside guide. When the provider's guide worked, only channels it didn't match
 * can take one. When it failed, a channel keeps its match while that key still has programmes to come.
 * Exact matches only. Each channel's names are cleaned once, however many files are read.
 */
class BackupChooser(
    channels: List<MatchableChannel>,
    provider: Map<String, String>,
    providerOk: Boolean,
    keysWithFuture: Set<String>,
) {
    private val open = GuideMatcher.prepare(channels.filter { BackupGuide.mayTake(provider[it.key], providerOk, keysWithFuture) })

    /** Adds one file's channels (or the next part of them) to [plan]. */
    fun add(plan: BackupPlan, file: List<MatchableGuide>): BackupPlan {
        val fresh = file.filter { BackupGuide.idKey(it.xmltvId) !in plan.seenIds }
        val matches = GuideMatcher.matchPrepared(open.filter { it.key !in plan.assigned }, fresh, fuzzy = false)
        return BackupPlan(
            assigned = plan.assigned + matches.mapValues { BackupGuide.key(it.value) },
            seenIds = plan.seenIds + fresh.map { BackupGuide.idKey(it.xmltvId) },
        )
    }
}

/** The result of the last backup load. Holds counts only, never an address. */
data class BackupResult(val at: Long, val matched: Int, val loaded: Int, val failed: Int)

object BackupGuide {
    const val MAX_ADDRESSES = 5

    /** Marks a backup channel's key. Long and odd on purpose, as a provider's id can be almost anything, even "x:". */
    const val KEY_PREFIX = "porch-backup:"
    private val ADDRESS = Regex("""^https?://[^\s/?#]+\S*$""", RegexOption.IGNORE_CASE)

    fun key(xmltvId: String): String = KEY_PREFIX + xmltvId.trim()

    fun isBackupKey(key: String?): Boolean = key != null && key.startsWith(KEY_PREFIX)

    internal fun idKey(xmltvId: String): String = xmltvId.trim().lowercase()

    internal fun mayTake(current: String?, providerOk: Boolean, keysWithFuture: Set<String>): Boolean = when {
        current == null || isBackupKey(current) -> true
        providerOk -> false
        else -> current !in keysWithFuture
    }

    /** While the provider's guide comes in: its match wins, and a channel it doesn't match stays on a working backup. */
    fun keepBackup(providerMatch: String?, old: String?): String? = providerMatch ?: old.takeIf { isBackupKey(it) }

    /**
     * True when a run shows what the backups hold now, so a backup match it didn't renew can go.
     * A failed file might still hold that channel, and a run that matched nothing proves nothing.
     */
    fun cleanRun(failed: Int, plan: BackupPlan): Boolean = failed == 0 && plan.assigned.isNotEmpty()

    /** A channel's match when a run finishes. A new backup wins, and after a clean run an old backup match goes. */
    fun finalMatch(current: String?, assigned: String?, clean: Boolean): String? =
        assigned ?: current.takeUnless { clean && isBackupKey(it) }

    /** Old backup rows to delete as the new ones go live: those being replaced, and those nothing uses any more. */
    fun oldKeysToDelete(old: Collection<String>, staged: Set<String>, inUse: Set<String>): Set<String> =
        old.filterTo(HashSet()) { it in staged || it !in inUse }

    /** One address per line: http or https only, duplicates dropped, at most [MAX_ADDRESSES]. */
    fun cleanAddresses(text: String): List<String> =
        text.lineSequence().map { it.trim() }.filter { ADDRESS.matches(it) }.distinct().take(MAX_ADDRESSES).toList()

    /** The new list from the phone page, or null to change nothing. Only an empty field clears the list. */
    fun submission(field: String?): List<String>? {
        if (field == null) return null
        if (field.isBlank()) return emptyList()
        return cleanAddresses(field).ifEmpty { null }
    }

    /** The part of an address that's safe to show. A login or a token in the address never gets through. */
    fun host(address: String): String {
        val rest = address.substringAfter("://").takeWhile { it !in "?#" }
        val first = rest.takeWhile { it != '/' }
        // A colon not followed by a port means a password, which may itself hold a slash.
        val login = !first.startsWith("[") && ('@' in first || first.substringAfter(':', "").any { !it.isDigit() })
        val authority = if (login) rest.substringAfterLast('@').takeWhile { it != '/' } else first
        return if (authority.startsWith("[")) authority.substringBefore(']') + "]" else authority.substringBefore(':')
    }

    /** "Last loaded 8:10 pm, 836 channels matched" or "Last load failed". [time] is the result's time, already written out. */
    fun resultLine(result: BackupResult, time: String): String {
        if (result.loaded == 0) return "Last load failed"
        val channels = if (result.matched == 1) "1 channel" else "${result.matched} channels"
        val problems = when (result.failed) {
            0 -> ""
            1 -> ". 1 address failed"
            else -> ". ${result.failed} addresses failed"
        }
        return "Last loaded $time, $channels matched$problems"
    }

    /** Progress while a file is read. It counts only what fits the viewer's channels, because most of a file doesn't. */
    fun readingLine(file: Int, files: Int, programmes: Int): String {
        val where = "Backup guide $file of $files"
        return if (programmes == 0) where else "$where: ${String.format(Locale.US, "%,d", programmes)} programmes for your channels"
    }

    const val SAVING_LINE = "Saving the backup guide"
}
