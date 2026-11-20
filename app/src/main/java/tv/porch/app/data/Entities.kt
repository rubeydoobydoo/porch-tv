package tv.porch.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String,
    val m3uUrl: String?,
    val xtreamServer: String?,
    val xtreamUsername: String?,
    val xtreamPassword: String?,
    val xmltvUrl: String?,
    val guideOffsetMinutes: Int = 0,
    val languageFilter: String? = null,
    val guideGeneration: Long = 0,
    val lastSyncAt: Long? = null,
    val serverTimeZone: String? = null,
)

/** The columns a channel on screen needs. Reading only these is lighter than reading the whole row. */
interface ChannelFields {
    val id: Long
    val sourceId: Long
    val name: String
    val number: Int
    val groupName: String
    val groupOverride: String?
    val favourite: Boolean
    val streamUrl: String
    val xtreamStreamId: Int?
    val archiveDays: Int
    val matchedXmltvId: String?
    val lastPlayedAt: Long?

    /** Where the channel logo lives, as the playlist gave it. Checked by LogoAddress before use. */
    val logoUrl: String?

    /** The cleaned name, stored at import. Null on a row stored before that; then the name is cleaned at read. */
    val shownTitle: String?
    val shownTag: String?
    val shownCode: String?

    /** Whether the name is a playlist divider. Null means "not stored yet", and [shownTitle] is ignored. */
    val divider: Boolean?
}

data class ChannelRow(
    override val id: Long,
    override val sourceId: Long,
    override val name: String,
    override val number: Int,
    override val groupName: String,
    override val groupOverride: String?,
    override val favourite: Boolean,
    override val streamUrl: String,
    override val xtreamStreamId: Int?,
    override val archiveDays: Int,
    override val matchedXmltvId: String?,
    override val lastPlayedAt: Long?,
    override val logoUrl: String? = null,
    override val shownTitle: String? = null,
    override val shownTag: String? = null,
    override val shownCode: String? = null,
    override val divider: Boolean? = null,
) : ChannelFields

@Entity(
    tableName = "channels",
    indices = [
        Index("sourceId"),
        Index(value = ["sourceId", "identity"]),
        Index(value = ["sourceId", "matchedXmltvId"]),
    ],
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) override val id: Long = 0,
    override val sourceId: Long,
    val identity: String,
    override val name: String,
    override val number: Int,
    val tvgId: String?,
    val tvgName: String?,
    override val groupName: String,
    override val groupOverride: String?,
    override val logoUrl: String?,
    override val streamUrl: String,
    override val xtreamStreamId: Int?,
    override val archiveDays: Int,
    override val favourite: Boolean,
    override val matchedXmltvId: String?,
    override val lastPlayedAt: Long?,
    override val shownTitle: String? = null,
    override val shownTag: String? = null,
    override val shownCode: String? = null,
    override val divider: Boolean? = null,
) : ChannelFields

@Entity(
    tableName = "programmes",
    indices = [
        Index(value = ["sourceId", "generation", "channelKey", "startUtc"]),
        Index(value = ["sourceId", "generation", "startUtc"]),
    ],
)
data class ProgrammeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val generation: Long,
    val channelKey: String,
    val title: String,
    val description: String?,
    val startUtc: Long,
    val stopUtc: Long,
    val category: String?,
    /** The programme's picture. Lists leave it out; only the single-row query reads it. */
    val iconUrl: String? = null,
)

@Entity(tableName = "hidden_groups", primaryKeys = ["sourceId", "groupName"])
data class HiddenGroupEntity(
    val sourceId: Long,
    val groupName: String,
)

/** A group the viewer picked in "My groups". */
@Entity(tableName = "chosen_groups", primaryKeys = ["sourceId", "groupName"])
data class ChosenGroupEntity(
    val sourceId: Long,
    val groupName: String,
)
