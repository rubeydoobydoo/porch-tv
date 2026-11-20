package tv.porch.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import tv.porch.app.domain.SourceDraft
import tv.porch.app.parse.XtreamUrls

class SourceEditTest {
    private val server = "http://tv.example.com"
    private val autoGuide = XtreamUrls.xmltv(server, "viewer", "old-secret")

    private val xtream = SourceEntity(
        id = 7, name = "Home TV", kind = "xtream", m3uUrl = null,
        xtreamServer = server, xtreamUsername = "viewer", xtreamPassword = "old-secret", xmltvUrl = autoGuide,
        guideOffsetMinutes = 30, languageFilter = "EN", guideGeneration = 4, lastSyncAt = 1_000L, serverTimeZone = "Europe/London",
    )

    @Test
    fun anEditKeepsTheSameSourceSoFavouritesGroupsAndResumePointsStay() {
        val playlist = SourceEntity(
            id = 3, name = "Playlist", kind = "m3u", m3uUrl = "http://example.com/list.m3u",
            xtreamServer = null, xtreamUsername = null, xtreamPassword = null, xmltvUrl = "http://example.com/gide.xml",
            guideOffsetMinutes = -60, guideGeneration = 9, lastSyncAt = 5_000L,
        )
        val draft = SourceEdit.draftFor(playlist).copy(xmltvUrl = "http://example.com/guide.xml")
        val edited = SourceEdit.apply(playlist, draft)
        assertEquals(playlist.copy(xmltvUrl = "http://example.com/guide.xml"), edited)

        // An Xtream source can't turn into a playlist by an edit.
        val renamed = SourceEdit.apply(xtream, SourceEdit.draftFor(xtream).copy(kind = "m3u", name = "Lounge"))
        assertEquals(xtream.copy(name = "Lounge"), renamed)
    }

    @Test
    fun theLoginsOwnGuideFollowsANewPasswordAndIsNeverShownInTheForm() {
        val draft = SourceEdit.draftFor(xtream)
        assertEquals("", draft.xmltvUrl)
        assertFalse(draft.xmltvUrl.contains("old-secret"))
        val edited = SourceEdit.apply(xtream, draft.copy(password = "new-secret"))
        assertEquals(XtreamUrls.xmltv(server, "viewer", "new-secret"), edited.xmltvUrl)
        assertEquals("new-secret", edited.xtreamPassword)

        // A guide the viewer typed is shown and kept.
        val typed = xtream.copy(xmltvUrl = "http://guide.example.com/epg.xml")
        assertEquals("http://guide.example.com/epg.xml", SourceEdit.draftFor(typed).xmltvUrl)
        assertEquals(typed.copy(xtreamPassword = "new-secret"), SourceEdit.apply(typed, SourceEdit.draftFor(typed).copy(password = "new-secret")))
    }
}
