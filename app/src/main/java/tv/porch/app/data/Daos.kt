package tv.porch.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import tv.porch.app.domain.BackupGuide

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources ORDER BY id")
    fun observe(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources ORDER BY id")
    fun list(): List<SourceEntity>

    @Query("SELECT * FROM sources WHERE id = :id")
    fun get(id: Long): SourceEntity?

    @Insert
    fun insert(source: SourceEntity): Long

    @Query("DELETE FROM sources WHERE id = :id")
    fun delete(id: Long)

    @Query("UPDATE sources SET guideOffsetMinutes = :minutes WHERE id = :id")
    fun setOffset(id: Long, minutes: Int)

    @Query("UPDATE sources SET languageFilter = :filter WHERE id = :id")
    fun setLanguage(id: Long, filter: String?)

    @Query("UPDATE sources SET guideGeneration = :generation, lastSyncAt = :at WHERE id = :id")
    fun setGuideGeneration(id: Long, generation: Long, at: Long)

    @Query("UPDATE sources SET serverTimeZone = :zone WHERE id = :id")
    fun setServerTimeZone(id: Long, zone: String?)

    @Query("UPDATE sources SET lastSyncAt = :at WHERE id = :id")
    fun touch(id: Long, at: Long)

    /** An edit changes only what the form holds, so a refresh writing the generation or zone at the same time is not undone. */
    @Query(
        "UPDATE sources SET name = :name, m3uUrl = :m3uUrl, xtreamServer = :server, xtreamUsername = :username, " +
            "xtreamPassword = :password, xmltvUrl = :xmltvUrl WHERE id = :id",
    )
    fun edit(id: Long, name: String, m3uUrl: String?, server: String?, username: String?, password: String?, xmltvUrl: String?)
}

// The same effective group as Mappers: a blank override falls back to the playlist group.
private const val GROUP_ROWS =
    "SELECT COALESCE(NULLIF(TRIM(groupOverride), ''), groupName) AS groupName, name, divider " +
        "FROM channels WHERE sourceId = :sourceId"

private const val BACKUP_ROW = "channelKey GLOB '${BackupGuide.KEY_PREFIX}*'"

// What the programme lists read. The picture address comes back empty, so a list of 10,000 rows doesn't grow by it.
private const val PROGRAMME_LIST = "p.id, p.sourceId, p.generation, p.channelKey, p.title, p.description, p.startUtc, p.stopUtc, p.category, NULL AS iconUrl"

/** One channel's effective group and name, for counting what a group holds. */
data class ChannelGroupRow(val groupName: String, val name: String, val divider: Boolean?)

/** Just what the backup guide step needs from each channel, so it doesn't hold 49,000 whole rows. */
data class ChannelMatchRow(val id: Long, val identity: String, val matchedXmltvId: String?)

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels ORDER BY number, name")
    fun observeAll(): Flow<List<ChannelEntity>>

    // The same channels in the same order as observeAll, without the columns the screens never show (identity, guide names). The logo address is the one long text column kept.
    @Query(
        "SELECT id, sourceId, name, number, groupName, groupOverride, favourite, streamUrl, xtreamStreamId, archiveDays, " +
            "matchedXmltvId, lastPlayedAt, logoUrl, shownTitle, shownTag, shownCode, divider FROM channels ORDER BY number, name",
    )
    fun observeRows(): Flow<List<ChannelRow>>

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId ORDER BY number, name")
    fun observeBySource(sourceId: Long): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId")
    fun listBySource(sourceId: Long): List<ChannelEntity>

    @Query("SELECT id, identity, matchedXmltvId FROM channels WHERE sourceId = :sourceId")
    fun matchRows(sourceId: Long): List<ChannelMatchRow>

    @Query("SELECT COUNT(*) FROM channels WHERE sourceId = :sourceId")
    fun countBySource(sourceId: Long): Int

    @Query(GROUP_ROWS)
    fun observeGroupRows(sourceId: Long): Flow<List<ChannelGroupRow>>

    @Query(GROUP_ROWS)
    fun groupRows(sourceId: Long): List<ChannelGroupRow>

    @Query("SELECT DISTINCT COALESCE(NULLIF(TRIM(groupOverride), ''), groupName) FROM channels WHERE sourceId = :sourceId")
    fun distinctGroups(sourceId: Long): List<String>

    @Query("SELECT * FROM channels")
    fun listAll(): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE id = :id")
    fun get(id: Long): ChannelEntity?

    // Reminders name a channel by source and identity, which survive a refresh; ids do not.
    @Query("SELECT identity FROM channels WHERE id = :id")
    fun identityOf(id: Long): String?

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND identity = :identity LIMIT 1")
    fun byIdentity(sourceId: Long, identity: String): ChannelEntity?

    @Query("SELECT * FROM channels WHERE id IN (:ids)")
    fun getMany(ids: List<Long>): List<ChannelEntity>

    @Query("DELETE FROM channels WHERE sourceId = :sourceId")
    fun deleteBySource(sourceId: Long)

    @Insert
    fun insertAll(items: List<ChannelEntity>)

    @Query("UPDATE channels SET matchedXmltvId = :xmltvId WHERE id = :id")
    fun setMatched(id: Long, xmltvId: String?)

    @Query("UPDATE channels SET favourite = :favourite WHERE id = :id")
    fun setFavourite(id: Long, favourite: Boolean)

    @Query("UPDATE channels SET groupOverride = :groupName WHERE id = :id")
    fun setGroupOverride(id: Long, groupName: String?)

    @Query("UPDATE channels SET lastPlayedAt = :at WHERE id = :id")
    fun setPlayed(id: Long, at: Long)

    @Query("SELECT * FROM channels WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT 1")
    fun lastPlayed(): ChannelEntity?

    // Same effective group as Mappers (a blank override falls back to the playlist group), in observeRows order.
    @Query(
        "SELECT id FROM channels WHERE sourceId = :sourceId " +
            "AND COALESCE(NULLIF(TRIM(groupOverride), ''), groupName) IS :group ORDER BY number, name",
    )
    fun idsInGroup(sourceId: Long, group: String?): List<Long>

    // How many channels a group holds, by the same effective group.
    @Query(
        "SELECT COUNT(*) FROM channels WHERE sourceId = :sourceId " +
            "AND COALESCE(NULLIF(TRIM(groupOverride), ''), groupName) IS :group",
    )
    fun countInGroup(sourceId: Long, group: String?): Int
}

@Dao
interface ProgrammeDao {
    /** Backup rows are stored with the offset taken off, so a new offset moves them to keep their real times. */
    @Query("UPDATE programmes SET startUtc = startUtc + :deltaMs, stopUtc = stopUtc + :deltaMs WHERE sourceId = :sourceId AND $BACKUP_ROW")
    fun shiftBackupRows(sourceId: Long, deltaMs: Long)

    @Insert
    fun insertAll(items: List<ProgrammeEntity>)

    @Query("DELETE FROM programmes WHERE sourceId = :sourceId AND generation != :keep")
    fun deleteOtherGenerations(sourceId: Long, keep: Long)

    @Query("DELETE FROM programmes WHERE sourceId = :sourceId")
    fun deleteBySource(sourceId: Long)

    @Query("SELECT COUNT(*) FROM programmes WHERE sourceId = :sourceId AND generation = :generation")
    fun countInGeneration(sourceId: Long, generation: Long): Int

    // Backup guide rows. BACKUP_ROW's GLOB is a literal with no wildcard up front, so SQLite turns it into an index range.
    @Query("SELECT COUNT(*) FROM programmes WHERE sourceId = :sourceId AND generation = :generation AND $BACKUP_ROW")
    fun countBackupRows(sourceId: Long, generation: Long): Int

    @Query("SELECT DISTINCT channelKey FROM programmes WHERE sourceId = :sourceId AND generation = :generation AND $BACKUP_ROW")
    fun backupKeys(sourceId: Long, generation: Long): List<String>

    @Query("UPDATE programmes SET generation = :to WHERE sourceId = :sourceId AND generation = :from AND $BACKUP_ROW")
    fun moveBackupRows(sourceId: Long, from: Long, to: Long)

    @Query("UPDATE programmes SET generation = :to WHERE sourceId = :sourceId AND generation = :from")
    fun moveGeneration(sourceId: Long, from: Long, to: Long)

    @Query("DELETE FROM programmes WHERE sourceId = :sourceId AND generation = :generation")
    fun deleteGeneration(sourceId: Long, generation: Long)

    @Query("DELETE FROM programmes WHERE sourceId = :sourceId AND generation = :generation AND channelKey IN (:keys)")
    fun deleteKeys(sourceId: Long, generation: Long, keys: List<String>)

    // The provider's keys with something still to come. [after] already has the guide offset taken off. Run only when the
    // provider's guide failed. The startUtc bound skips the old rows, so a programme over two days long doesn't count.
    @Query(
        "SELECT DISTINCT channelKey FROM programmes WHERE sourceId = :sourceId AND generation = :generation " +
            "AND startUtc > :after - 172800000 AND stopUtc > :after AND NOT $BACKUP_ROW",
    )
    fun keysEndingAfter(sourceId: Long, generation: Long, after: Long): List<String>

    // The offset expression below cannot use an index, so each query also carries a plain range
    // on startUtc (offsets are minutes, never beyond +-12 h = 43,200,000 ms). That lets SQLite
    // bound the scan with the (sourceId, generation, startUtc) index instead of reading every row.
    // onNow has no safe lower bound on startUtc (a programme can run for days), so the index scan
    // is bounded from above by startUtc only. The stopUtc term is a plain filter, not an index bound.
    @Query(
        """
        SELECT $PROGRAMME_LIST FROM programmes p
        INNER JOIN sources s ON s.id = p.sourceId AND s.guideGeneration = p.generation
        WHERE p.startUtc <= :now + 43200000
          AND p.stopUtc >= :now - 43200000
          AND p.startUtc + (s.guideOffsetMinutes * 60000) <= :now
          AND p.stopUtc + (s.guideOffsetMinutes * 60000) > :now
        """,
    )
    fun onNow(now: Long): Flow<List<ProgrammeEntity>>

    // The lists that read this never show a description, so it comes back empty: the 3-hour sport window is large.
    @Query(
        """
        SELECT p.id, p.sourceId, p.generation, p.channelKey, p.title, NULL AS description, p.startUtc, p.stopUtc, p.category, NULL AS iconUrl
        FROM programmes p
        INNER JOIN sources s ON s.id = p.sourceId AND s.guideGeneration = p.generation
        WHERE p.startUtc BETWEEN :now - 43200000 AND :until + 43200000
          AND p.startUtc + (s.guideOffsetMinutes * 60000) > :now
          AND p.startUtc + (s.guideOffsetMinutes * 60000) <= :until
        """,
    )
    fun startingUntil(now: Long, until: Long): Flow<List<ProgrammeEntity>>

    @Query(
        """
        SELECT $PROGRAMME_LIST FROM programmes p
        INNER JOIN sources s ON s.id = p.sourceId AND s.guideGeneration = p.generation
        WHERE p.startUtc BETWEEN :fromEffective - 43200000 AND :toEffective + 43200000
          AND p.startUtc + (s.guideOffsetMinutes * 60000) >= :fromEffective
          AND p.startUtc + (s.guideOffsetMinutes * 60000) < :toEffective
        """,
    )
    fun startingBetween(fromEffective: Long, toEffective: Long): Flow<List<ProgrammeEntity>>

    @Query(
        """
        SELECT $PROGRAMME_LIST FROM programmes p
        WHERE sourceId = :sourceId AND generation = :generation
          AND channelKey IN (:keys)
          AND stopUtc > :fromUtc AND startUtc < :toUtc
        """,
    )
    fun window(
        sourceId: Long,
        generation: Long,
        keys: List<String>,
        fromUtc: Long,
        toUtc: Long,
    ): List<ProgrammeEntity>

    /** [window] without descriptions: the Sport tab reads two weeks of titles and never shows a description. */
    @Query(
        """
        SELECT p.id, p.sourceId, p.generation, p.channelKey, p.title, NULL AS description, p.startUtc, p.stopUtc, p.category, NULL AS iconUrl
        FROM programmes p
        WHERE sourceId = :sourceId AND generation = :generation
          AND channelKey IN (:keys)
          AND stopUtc > :fromUtc AND startUtc < :toUtc
        """,
    )
    fun windowTitles(
        sourceId: Long,
        generation: Long,
        keys: List<String>,
        fromUtc: Long,
        toUtc: Long,
    ): List<ProgrammeEntity>

    @Query(
        """
        SELECT $PROGRAMME_LIST FROM programmes p
        INNER JOIN sources s ON s.id = p.sourceId AND s.guideGeneration = p.generation
        WHERE p.title LIKE '%' || :query || '%' COLLATE NOCASE
          AND p.stopUtc > :notBefore AND p.startUtc < :notAfter
        ORDER BY p.startUtc
        LIMIT 80
        """,
    )
    fun search(query: String, notBefore: Long, notAfter: Long): List<ProgrammeEntity>

    /** [search] for Following: a longer list, because a followed name can be on many channels. */
    @Query(
        """
        SELECT $PROGRAMME_LIST FROM programmes p
        INNER JOIN sources s ON s.id = p.sourceId AND s.guideGeneration = p.generation
        WHERE p.title LIKE '%' || REPLACE(REPLACE(REPLACE(:query, '\', '\\'), '%', '\%'), '_', '\_') || '%' ESCAPE '\' COLLATE NOCASE
          AND p.stopUtc > :notBefore AND p.startUtc < :notAfter
        ORDER BY p.startUtc
        LIMIT 300
        """,
    )
    fun following(query: String, notBefore: Long, notAfter: Long): List<ProgrammeEntity>

    @Query(
        """
        SELECT DISTINCT p.title FROM programmes p
        INNER JOIN sources s ON s.id = p.sourceId AND s.guideGeneration = p.generation
        WHERE p.stopUtc > :notBefore AND p.startUtc < :notAfter
        """,
    )
    fun titles(notBefore: Long, notAfter: Long): List<String>

    @Query(
        """
        SELECT $PROGRAMME_LIST FROM programmes p
        INNER JOIN sources s ON s.id = p.sourceId AND s.guideGeneration = p.generation
        WHERE p.title IN (:titles)
          AND p.stopUtc > :notBefore AND p.startUtc < :notAfter
        ORDER BY p.startUtc
        LIMIT 400
        """,
    )
    fun withTitles(titles: List<String>, notBefore: Long, notAfter: Long): List<ProgrammeEntity>

    @Query(
        """
        SELECT $PROGRAMME_LIST FROM programmes p
        INNER JOIN sources s ON s.id = p.sourceId AND s.guideGeneration = p.generation
        INNER JOIN channels c ON c.id = :channelId
          AND c.sourceId = p.sourceId
          AND c.matchedXmltvId = p.channelKey
        WHERE p.stopUtc + (s.guideOffsetMinutes * 60000) > :now
        ORDER BY p.startUtc
        LIMIT :limit
        """,
    )
    fun upcoming(channelId: Long, now: Long, limit: Int): List<ProgrammeEntity>

    // One whole row, the only read of the picture address: Home's preview, once focus has settled on a row.
    @Query("SELECT * FROM programmes WHERE id = :id")
    fun get(id: Long): ProgrammeEntity?
}

@Dao
interface HiddenGroupDao {
    @Query("SELECT * FROM hidden_groups")
    fun observe(): Flow<List<HiddenGroupEntity>>

    @Query("SELECT * FROM hidden_groups")
    fun list(): List<HiddenGroupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: HiddenGroupEntity)

    @Query("DELETE FROM hidden_groups WHERE sourceId = :sourceId AND groupName = :groupName")
    fun delete(sourceId: Long, groupName: String)

    @Query("SELECT groupName FROM hidden_groups WHERE sourceId = :sourceId")
    fun namesBySource(sourceId: Long): List<String>

    @Query("DELETE FROM hidden_groups WHERE sourceId = :sourceId")
    fun deleteBySource(sourceId: Long)
}

@Dao
interface ChosenGroupDao {
    @Query("SELECT * FROM chosen_groups")
    fun observe(): Flow<List<ChosenGroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: ChosenGroupEntity)

    @Query("DELETE FROM chosen_groups WHERE sourceId = :sourceId AND groupName = :groupName")
    fun delete(sourceId: Long, groupName: String)

    @Query("SELECT COUNT(*) FROM chosen_groups WHERE sourceId = :sourceId")
    fun countBySource(sourceId: Long): Int

    @Query("SELECT groupName FROM chosen_groups WHERE sourceId = :sourceId")
    fun namesBySource(sourceId: Long): List<String>

    @Query("DELETE FROM chosen_groups WHERE sourceId = :sourceId")
    fun deleteBySource(sourceId: Long)

    @Query("DELETE FROM chosen_groups")
    fun clear()
}

@Dao
interface FilmCategoryDao {
    @Query("SELECT * FROM film_categories WHERE sourceId = :sourceId ORDER BY position")
    fun list(sourceId: Long): List<FilmCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(items: List<FilmCategoryEntity>)

    @Query("DELETE FROM film_categories WHERE sourceId = :sourceId")
    fun deleteBySource(sourceId: Long)
}

@Dao
interface FilmDao {
    /** Each title once, however many of [categoryIds] list it. */
    @Query(
        "SELECT * FROM films WHERE sourceId = :sourceId AND streamId IN " +
            "(SELECT streamId FROM film_listings WHERE sourceId = :sourceId AND categoryId IN (:categoryIds)) " +
            "ORDER BY addedAt IS NULL, addedAt DESC, name LIMIT :limit",
    )
    fun inCategories(sourceId: Long, categoryIds: List<String>, limit: Int): List<FilmEntity>

    @Query("SELECT * FROM films WHERE sourceId = :sourceId AND streamId = :streamId")
    fun get(sourceId: Long, streamId: Long): FilmEntity?

    @Query("SELECT * FROM films WHERE sourceId = :sourceId AND streamId IN (:streamIds)")
    fun getMany(sourceId: Long, streamIds: List<Long>): List<FilmEntity>

    /** Adds or refreshes a listing. Details already fetched stay: only the list columns are replaced. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertNew(items: List<FilmEntity>): List<Long>

    @Query(
        "UPDATE films SET name = :name, categoryId = :categoryId, iconUrl = COALESCE(:iconUrl, iconUrl), ext = :ext, " +
            "addedAt = :addedAt, rating = COALESCE(:rating, rating), year = COALESCE(:year, year), listedAt = MAX(listedAt, :listedAt) " +
            "WHERE sourceId = :sourceId AND streamId = :streamId",
    )
    fun refresh(
        sourceId: Long, streamId: Long, name: String, categoryId: String, iconUrl: String?, ext: String?,
        addedAt: Long?, rating: Double?, year: Int?, listedAt: Long,
    )

    @Query(
        "UPDATE films SET plot = :plot, durationSecs = :durationSecs, genre = :genre, " +
            "year = COALESCE(:year, year), rating = COALESCE(:rating, rating), iconUrl = COALESCE(:iconUrl, iconUrl), " +
            "detailsAt = :at WHERE sourceId = :sourceId AND streamId = :streamId",
    )
    fun saveDetails(
        sourceId: Long, streamId: Long, plot: String?, durationSecs: Int?, genre: String?,
        year: Int?, rating: Double?, iconUrl: String?, at: Long,
    )

    /** Marks rows the provider still lists but that could not be read this time, so pruning keeps them. */
    @Query("UPDATE film_listings SET listedAt = MAX(listedAt, :stamp) WHERE sourceId = :sourceId AND categoryId = :categoryId AND streamId IN (:ids)")
    fun stillListed(sourceId: Long, categoryId: String, ids: List<Long>, stamp: Long)

    /** Adds category listings that are new (-1 in the result for one already there); [relist] updates it. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertListings(items: List<FilmListingEntity>): List<Long>

    @Query("UPDATE film_listings SET listedAt = MAX(listedAt, :listedAt) WHERE sourceId = :sourceId AND categoryId = :categoryId AND streamId = :id")
    fun relist(sourceId: Long, categoryId: String, id: Long, listedAt: Long)

    /**
     * Step one of a prune: deletes titles whose only listings are the category's stale ones (older than [stamp]),
     * so a title still listed in another category stays. Resume points and show progress are separate tables and
     * stay. Run [dropListings] next, in the same transaction.
     */
    @Query(
        "DELETE FROM films WHERE sourceId = :sourceId AND streamId IN " +
            "(SELECT streamId FROM film_listings WHERE sourceId = :sourceId AND categoryId = :categoryId AND listedAt < :stamp) " +
            "AND NOT EXISTS (SELECT 1 FROM film_listings o WHERE o.sourceId = films.sourceId AND o.streamId = films.streamId " +
            "AND (o.categoryId != :categoryId OR o.listedAt >= :stamp))",
    )
    fun dropOnlyListedIn(sourceId: Long, categoryId: String, stamp: Long): Int

    /** Step two of a prune: the category's listings the finished download did not refresh. */
    @Query("DELETE FROM film_listings WHERE sourceId = :sourceId AND categoryId = :categoryId AND listedAt < :stamp")
    fun dropListings(sourceId: Long, categoryId: String, stamp: Long): Int

    /** Blank listings the full list gave titles with no category, once a category download has filed them. */
    @Query("DELETE FROM film_listings WHERE sourceId = :sourceId AND categoryId = '' AND streamId IN (:ids)")
    fun dropBlankListings(sourceId: Long, ids: List<Long>)

    /**
     * The full list's blank listing for a title with no category, added only when the title has no listing yet.
     * A title a category download already filed keeps just its real listings, so pruning can still drop it.
     */
    @Query(
        "INSERT OR IGNORE INTO film_listings (sourceId, categoryId, streamId, listedAt) SELECT :sourceId, '', :id, :stamp " +
            "WHERE NOT EXISTS (SELECT 1 FROM film_listings WHERE sourceId = :sourceId AND streamId = :id)",
    )
    fun addBlankIfUnlisted(sourceId: Long, id: Long, stamp: Long)

    /** Up to [limit] films whose name contains [needle]. Case-insensitive for plain letters. */
    @Query("SELECT * FROM films WHERE sourceId = :sourceId AND name LIKE '%' || :needle || '%' ESCAPE '\\' ORDER BY name LIMIT :limit")
    fun searchName(sourceId: Long, needle: String, limit: Int): List<FilmEntity>

    @Query("SELECT streamId, name FROM films WHERE sourceId = :sourceId")
    fun names(sourceId: Long): List<FilmName>

    @Query("SELECT COUNT(*) FROM films WHERE sourceId = :sourceId")
    fun count(sourceId: Long): Int
}

data class FilmName(val streamId: Long, val name: String)

@Dao
interface ResumePointDao {
    @Query("SELECT * FROM resume_points WHERE sourceId = :sourceId AND streamId = :streamId")
    fun get(sourceId: Long, streamId: Long): ResumePointEntity?

    @Query("SELECT * FROM resume_points WHERE sourceId = :sourceId ORDER BY updatedAt DESC LIMIT 200")
    fun recent(sourceId: Long): List<ResumePointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun put(item: ResumePointEntity)

    @Query("DELETE FROM resume_points WHERE sourceId = :sourceId AND streamId = :streamId")
    fun delete(sourceId: Long, streamId: Long)
}

@Dao
interface ShowCategoryDao {
    @Query("SELECT * FROM show_categories WHERE sourceId = :sourceId ORDER BY position")
    fun list(sourceId: Long): List<ShowCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(items: List<ShowCategoryEntity>)

    @Query("DELETE FROM show_categories WHERE sourceId = :sourceId")
    fun deleteBySource(sourceId: Long)
}

@Dao
interface ShowDao {
    /** Each title once, however many of [categoryIds] list it. */
    @Query(
        "SELECT * FROM shows WHERE sourceId = :sourceId AND seriesId IN " +
            "(SELECT seriesId FROM show_listings WHERE sourceId = :sourceId AND categoryId IN (:categoryIds)) " +
            "ORDER BY addedAt IS NULL, addedAt DESC, name LIMIT :limit",
    )
    fun inCategories(sourceId: Long, categoryIds: List<String>, limit: Int): List<ShowEntity>

    @Query("SELECT * FROM shows WHERE sourceId = :sourceId AND seriesId = :seriesId")
    fun get(sourceId: Long, seriesId: Long): ShowEntity?

    @Query("SELECT * FROM shows WHERE sourceId = :sourceId AND seriesId IN (:seriesIds)")
    fun getMany(sourceId: Long, seriesIds: List<Long>): List<ShowEntity>

    /** Adds listings that are new. A show already there is left alone (-1 in the result); [refresh] updates it. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertNew(items: List<ShowEntity>): List<Long>

    /** Refreshes a listing. Details already fetched stay: only the list columns are replaced. */
    @Query(
        "UPDATE shows SET name = :name, categoryId = :categoryId, coverUrl = COALESCE(:coverUrl, coverUrl), " +
            "addedAt = :addedAt, rating = COALESCE(:rating, rating), year = COALESCE(:year, year), " +
            "plot = COALESCE(:plot, plot), genre = COALESCE(:genre, genre), listedAt = MAX(listedAt, :listedAt) " +
            "WHERE sourceId = :sourceId AND seriesId = :seriesId",
    )
    fun refresh(
        sourceId: Long, seriesId: Long, name: String, categoryId: String, coverUrl: String?, addedAt: Long?,
        rating: Double?, year: Int?, plot: String?, genre: String?, listedAt: Long,
    )

    /** Fills only what the listing left empty. */
    @Query(
        "UPDATE shows SET plot = COALESCE(plot, :plot), genre = COALESCE(genre, :genre), year = COALESCE(year, :year), " +
            "rating = COALESCE(rating, :rating), coverUrl = COALESCE(coverUrl, :coverUrl) " +
            "WHERE sourceId = :sourceId AND seriesId = :seriesId",
    )
    fun fillMissing(sourceId: Long, seriesId: Long, plot: String?, genre: String?, year: Int?, rating: Double?, coverUrl: String?)

    /** Marks rows the provider still lists but that could not be read this time, so pruning keeps them. */
    @Query("UPDATE show_listings SET listedAt = MAX(listedAt, :stamp) WHERE sourceId = :sourceId AND categoryId = :categoryId AND seriesId IN (:ids)")
    fun stillListed(sourceId: Long, categoryId: String, ids: List<Long>, stamp: Long)

    /** Adds category listings that are new (-1 in the result for one already there); [relist] updates it. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertListings(items: List<ShowListingEntity>): List<Long>

    @Query("UPDATE show_listings SET listedAt = MAX(listedAt, :listedAt) WHERE sourceId = :sourceId AND categoryId = :categoryId AND seriesId = :id")
    fun relist(sourceId: Long, categoryId: String, id: Long, listedAt: Long)

    /**
     * Step one of a prune: deletes titles whose only listings are the category's stale ones (older than [stamp]),
     * so a title still listed in another category stays. Resume points and show progress are separate tables and
     * stay. Run [dropListings] next, in the same transaction.
     */
    @Query(
        "DELETE FROM shows WHERE sourceId = :sourceId AND seriesId IN " +
            "(SELECT seriesId FROM show_listings WHERE sourceId = :sourceId AND categoryId = :categoryId AND listedAt < :stamp) " +
            "AND NOT EXISTS (SELECT 1 FROM show_listings o WHERE o.sourceId = shows.sourceId AND o.seriesId = shows.seriesId " +
            "AND (o.categoryId != :categoryId OR o.listedAt >= :stamp))",
    )
    fun dropOnlyListedIn(sourceId: Long, categoryId: String, stamp: Long): Int

    /** Step two of a prune: the category's listings the finished download did not refresh. */
    @Query("DELETE FROM show_listings WHERE sourceId = :sourceId AND categoryId = :categoryId AND listedAt < :stamp")
    fun dropListings(sourceId: Long, categoryId: String, stamp: Long): Int

    /** Blank listings the full list gave titles with no category, once a category download has filed them. */
    @Query("DELETE FROM show_listings WHERE sourceId = :sourceId AND categoryId = '' AND seriesId IN (:ids)")
    fun dropBlankListings(sourceId: Long, ids: List<Long>)

    /**
     * The full list's blank listing for a title with no category, added only when the title has no listing yet.
     * A title a category download already filed keeps just its real listings, so pruning can still drop it.
     */
    @Query(
        "INSERT OR IGNORE INTO show_listings (sourceId, categoryId, seriesId, listedAt) SELECT :sourceId, '', :id, :stamp " +
            "WHERE NOT EXISTS (SELECT 1 FROM show_listings WHERE sourceId = :sourceId AND seriesId = :id)",
    )
    fun addBlankIfUnlisted(sourceId: Long, id: Long, stamp: Long)

    /** Up to [limit] shows whose name contains [needle]. Case-insensitive for plain letters. */
    @Query("SELECT * FROM shows WHERE sourceId = :sourceId AND name LIKE '%' || :needle || '%' ESCAPE '\\' ORDER BY name LIMIT :limit")
    fun searchName(sourceId: Long, needle: String, limit: Int): List<ShowEntity>

    @Query("SELECT seriesId, name FROM shows WHERE sourceId = :sourceId")
    fun names(sourceId: Long): List<ShowName>

    @Query("SELECT COUNT(*) FROM shows WHERE sourceId = :sourceId")
    fun count(sourceId: Long): Int
}

data class ShowName(val seriesId: Long, val name: String)

@Dao
interface ShowProgressDao {
    @Query("SELECT * FROM show_progress WHERE sourceId = :sourceId AND seriesId = :seriesId")
    fun get(sourceId: Long, seriesId: Long): ShowProgressEntity?

    @Query("SELECT * FROM show_progress WHERE sourceId = :sourceId ORDER BY updatedAt DESC LIMIT 200")
    fun recent(sourceId: Long): List<ShowProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun put(item: ShowProgressEntity)

    @Query("DELETE FROM show_progress WHERE sourceId = :sourceId AND seriesId = :seriesId")
    fun delete(sourceId: Long, seriesId: Long)
}
