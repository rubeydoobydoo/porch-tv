package tv.porch.app.parse

/** A channel row produced by the M3U parser or the Xtream mapper, before it is stored. */
data class ParsedChannel(
    val identity: String,
    val name: String,
    val number: Int,
    val tvgId: String?,
    val tvgName: String?,
    val group: String,
    val logo: String?,
    val streamUrl: String,
    val xtreamStreamId: Int?,
    val archiveDays: Int,
)

data class ParsedGuideChannel(
    val xmltvId: String,
    val names: List<String>,
)

data class ParsedProgramme(
    val channelKey: String,
    val title: String,
    val description: String?,
    val startUtc: Long,
    val stopUtc: Long,
    val category: String?,
    /** The programme's own picture. Null when the guide gives none, or gives anything but http or https. */
    val iconUrl: String? = null,
)
