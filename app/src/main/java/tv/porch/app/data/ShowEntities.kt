package tv.porch.app.data

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "show_categories", primaryKeys = ["sourceId", "categoryId"])
data class ShowCategoryEntity(
    val sourceId: Long,
    val categoryId: String,
    val rawName: String,
    val shownName: String,
    val chosen: Boolean,
    val position: Int,
)

@Entity(
    tableName = "shows",
    primaryKeys = ["sourceId", "seriesId"],
    indices = [Index("sourceId", "categoryId"), Index("sourceId", "name")],
)
data class ShowEntity(
    val sourceId: Long,
    val seriesId: Long,
    val name: String,
    val categoryId: String,
    val coverUrl: String? = null,
    val rating: Double? = null,
    val year: Int? = null,
    val plot: String? = null,
    val genre: String? = null,
    val addedAt: Long? = null,
    val listedAt: Long,
)

/**
 * One row for each category a show is listed under, so a show the provider lists in two categories is in both
 * chips. [listedAt] is the last download that listed it there; pruning a category deletes its older rows, and a
 * show goes only when its last listing does. The show's own [ShowEntity.categoryId] is kept but no longer decides
 * which chip it is in.
 */
@Entity(
    tableName = "show_listings",
    primaryKeys = ["sourceId", "categoryId", "seriesId"],
    indices = [Index("sourceId", "seriesId")],
)
data class ShowListingEntity(
    val sourceId: Long,
    val categoryId: String,
    val seriesId: Long,
    val listedAt: Long,
)

/** Where the viewer is in a show: one row per show, the episode to resume or the next one to watch. */
@Entity(tableName = "show_progress", primaryKeys = ["sourceId", "seriesId"])
data class ShowProgressEntity(
    val sourceId: Long,
    val seriesId: Long,
    val episodeId: Long,
    val season: Int,
    val episode: Int,
    val episodeTitle: String,
    val ext: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)
