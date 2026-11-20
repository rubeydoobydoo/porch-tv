package tv.porch.app.domain

/** The backup guides a TV starts with. */
object BackupGuideDefaults {
    /** Australian free-to-air listings, chosen by Ruben on 9 Nov 2026. */
    const val AU_FREE_TO_AIR = "https://i.mjh.nz/au/all/epg.xml.gz"

    /** [saved] is null until the viewer has changed the list. An empty list they saved stays empty. */
    fun addresses(saved: String?): List<String> =
        if (saved == null) listOf(AU_FREE_TO_AIR) else saved.split('\n').filter { it.isNotEmpty() }
}
