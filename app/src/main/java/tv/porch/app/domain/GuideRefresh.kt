package tv.porch.app.domain

/** When a source's guide is old enough to fetch again without being asked. */
object GuideRefresh {
    const val MAX_AGE_MS = 6L * 60L * 60L * 1000L

    /** Never while something is being watched: an import is heavy enough to make the picture stutter on a Chromecast. */
    fun due(lastSyncAt: Long?, now: Long, watching: Boolean): Boolean =
        !watching && (lastSyncAt == null || lastSyncAt > now || now - lastSyncAt > MAX_AGE_MS)
}
