package tv.porch.app.data

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "film_categories", primaryKeys = ["sourceId", "categoryId"])
data class FilmCategoryEntity(
    val sourceId: Long,
    val categoryId: String,
    val rawName: String,
    val shownName: String,
    val chosen: Boolean,
    val position: Int,
)

@Entity(
    tableName = "films",
    primaryKeys = ["sourceId", "streamId"],
    indices = [Index("sourceId", "categoryId"), Index("sourceId", "name")],
)
data class FilmEntity(
    val sourceId: Long,
    val streamId: Long,
    val name: String,
    val categoryId: String,
    val iconUrl: String? = null,
    val ext: String? = null,
    val addedAt: Long? = null,
    val rating: Double? = null,
    val year: Int? = null,
    val plot: String? = null,
    val durationSecs: Int? = null,
    val genre: String? = null,
    val detailsAt: Long? = null,
    val listedAt: Long,
)

/** One row for each category a film is listed under, the same as [ShowListingEntity]. */
@Entity(
    tableName = "film_listings",
    primaryKeys = ["sourceId", "categoryId", "streamId"],
    indices = [Index("sourceId", "streamId")],
)
data class FilmListingEntity(
    val sourceId: Long,
    val categoryId: String,
    val streamId: Long,
    val listedAt: Long,
)

@Entity(tableName = "resume_points", primaryKeys = ["sourceId", "streamId"])
data class ResumePointEntity(
    val sourceId: Long,
    val streamId: Long,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)
