package tv.porch.app.data

import tv.porch.app.domain.SourceDraft
import tv.porch.app.domain.cleaned
import tv.porch.app.parse.XtreamUrls

/**
 * Editing a source in place. The row keeps its id, so favourites, hidden groups, moved channels, last-played and
 * resume points (all tied to the source id and the channel identity) stay. The kind never changes.
 */
object SourceEdit {
    /** The form's starting values. An Xtream login's own guide address carries the password, so it shows as empty. */
    fun draftFor(source: SourceEntity): SourceDraft = SourceDraft(
        name = source.name,
        kind = source.kind,
        m3uUrl = source.m3uUrl.orEmpty(),
        server = source.xtreamServer.orEmpty(),
        username = source.xtreamUsername.orEmpty(),
        password = source.xtreamPassword.orEmpty(),
        xmltvUrl = if (usesLoginGuide(source)) "" else source.xmltvUrl.orEmpty(),
    )

    /** The source with the form's values. Throws [IllegalArgumentException] with a sentence the form can show. */
    fun apply(source: SourceEntity, draft: SourceDraft): SourceEntity {
        val clean = draft.copy(kind = source.kind).cleaned()
        val xtream = source.kind == "xtream"
        return source.copy(
            name = clean.name,
            m3uUrl = if (xtream) null else clean.m3uUrl,
            xtreamServer = if (xtream) XtreamUrls.normalizeServer(clean.server) else null,
            xtreamUsername = if (xtream) clean.username else null,
            xtreamPassword = if (xtream) clean.password else null,
            xmltvUrl = clean.xmltvUrl,
        )
    }

    private fun usesLoginGuide(source: SourceEntity): Boolean {
        val server = source.xtreamServer ?: return false
        val user = source.xtreamUsername ?: return false
        val pass = source.xtreamPassword ?: return false
        return source.kind == "xtream" && source.xmltvUrl == XtreamUrls.xmltv(server, user, pass)
    }
}
