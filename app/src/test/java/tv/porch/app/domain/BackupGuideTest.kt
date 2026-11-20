package tv.porch.app.domain

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.porch.app.parse.ParsedGuideChannel
import tv.porch.app.parse.ParsedProgramme
import tv.porch.app.parse.XmltvParser
import tv.porch.app.parse.XmltvSink

class BackupGuideTest {
    private fun key(id: String) = BackupGuide.KEY_PREFIX + id

    private fun channel(key: String, name: String) = MatchableChannel(key, null, null, name)

    private fun guide(id: String, name: String) = MatchableGuide(id, listOf(name))

    @Test
    fun theProviderKeepsItsMatchesWhileItsDataLasts() {
        val everyone = listOf(guide("backup.harbour", "Harbour News"), guide("backup.hilltop", "Hilltop Movies"), guide("backup.valley", "Valley Sport"))
        val channels = listOf(channel("left", "Harbour News"), channel("gone", "Hilltop Movies"), channel("none", "Valley Sport"))
        val provider = mapOf("left" to "provider.harbour", "gone" to "provider.hilltop")

        val worked = BackupChooser(channels, provider, providerOk = true, keysWithFuture = emptySet()).add(BackupPlan(), everyone)
        assertEquals(mapOf("none" to key("backup.valley")), worked.assigned)

        val failed = BackupChooser(channels, provider, providerOk = false, keysWithFuture = setOf("provider.harbour")).add(BackupPlan(), everyone)
        assertEquals(mapOf("gone" to key("backup.hilltop"), "none" to key("backup.valley")), failed.assigned)

        // While the provider's guide comes in, a channel it doesn't match stays on its working backup.
        assertEquals(key("old"), BackupGuide.keepBackup(null, key("old")))
        assertEquals("provider.new", BackupGuide.keepBackup("provider.new", key("old")))
        assertNull(BackupGuide.keepBackup(null, "provider.old"))
    }

    @Test
    fun onlyExactMatchesCountTheFirstFileWinsAndKeysCannotCollide() {
        val near = listOf(channel("a", "Redfield Sports"))
        val nearGuide = listOf(guide("backup.redfield", "Redfield Sport"))
        // The provider's guide still matches close names, so this pair is near enough to prove the point.
        assertEquals("backup.redfield", GuideMatcher.match(near, nearGuide)["a"])
        assertEquals(emptyMap<String, String>(), BackupChooser(near, emptyMap(), true, emptySet()).add(BackupPlan(), nearGuide).assigned)

        val choose = BackupChooser(listOf(channel("a", "Harbour News"), channel("b", "Second Name")), emptyMap(), true, emptySet())
        val first = choose.add(BackupPlan(), listOf(guide("same.id", "Harbour News")))
        val second = choose.add(first, listOf(guide("same.id", "Second Name"), guide("other.id", "Harbour News")))
        assertEquals(mapOf("a" to key("same.id")), second.assigned)
        assertEquals(setOf(key("same.id")), first.keysAddedSince(BackupPlan()))
        assertEquals(emptySet<String>(), second.keysAddedSince(first))

        assertTrue(BackupGuide.isBackupKey(BackupGuide.key("same.id")))
        assertFalse(BackupGuide.isBackupKey("x:a.provider.id"))
    }

    @Test
    fun aFailedOrEmptyRunLeavesWorkingBackupsAlone() {
        val someMatched = BackupPlan(assigned = mapOf("a" to key("new")))
        assertFalse(BackupGuide.cleanRun(failed = 1, plan = someMatched))
        assertFalse(BackupGuide.cleanRun(failed = 0, plan = BackupPlan()))
        assertTrue(BackupGuide.cleanRun(failed = 0, plan = someMatched))

        assertEquals(key("old"), BackupGuide.finalMatch(key("old"), null, clean = false))
        assertNull(BackupGuide.finalMatch(key("old"), null, clean = true))
        assertEquals("provider.a", BackupGuide.finalMatch("provider.a", null, clean = true))
        assertEquals(key("new"), BackupGuide.finalMatch(key("old"), key("new"), clean = false))

        // Replaced keys and keys nothing uses go. A key a failed file left in use stays.
        val drop = BackupGuide.oldKeysToDelete(
            old = listOf(key("replaced"), key("kept"), key("unused")),
            staged = setOf(key("replaced")),
            inUse = setOf(key("replaced"), key("kept")),
        )
        assertEquals(setOf(key("replaced"), key("unused")), drop)
    }

    @Test
    fun addressesAreCleanedAndOnlyTheHostIsEverShown() {
        val text = listOf(
            " https://one.example.com/guide.xml ",
            "ftp://two.example.com/guide.xml",
            "not an address",
            "",
            "https://one.example.com/guide.xml",
            "HTTP://three.example.com/a.xml.gz",
            "https://four.example.com/a",
            "https://five.example.com/a",
            "https://six.example.com/a",
            "https://seven.example.com/a",
        ).joinToString("\r\n")
        assertEquals(
            listOf(
                "https://one.example.com/guide.xml",
                "HTTP://three.example.com/a.xml.gz",
                "https://four.example.com/a",
                "https://five.example.com/a",
                "https://six.example.com/a",
            ),
            BackupGuide.submission(text),
        )
        // No field or only bad lines change nothing. Only an empty field clears the list.
        assertNull(BackupGuide.submission(null))
        assertNull(BackupGuide.submission("ftp://two.example.com/guide.xml"))
        assertEquals(emptyList<String>(), BackupGuide.submission("  \r\n"))

        assertEquals("h.example.com", BackupGuide.host("https://someone:pa/ss@h.example.com/get"))
        val host = BackupGuide.host("https://someone:secret@guide.example.com:8080/get.php?password=secret")
        assertEquals("guide.example.com", host)
    }

    @Test
    fun aFileOnlyPaysForTheProgrammesItKeepsAndProgressSaysSo() {
        val xml = """
            <tv>
            <channel id="wanted"><display-name>Wanted</display-name></channel>
            <programme start="20261102100000 +0000" stop="20261102110000 +0000" channel="other"><title>Skip one</title><desc>a &lt;b&gt; c</desc></programme>
            <programme start="20261102100000 +0000" stop="20261102110000 +0000" channel="wanted"><title>Keep one</title></programme>
            <programme channel="other" start="20261102110000 +0000" stop="20261102120000 +0000"/>
            <programme start="20261102110000 +0000" stop="20261102120000 +0000" channel="wanted"><title>Keep two</title></programme>
            </tv>
        """.trimIndent().toByteArray()
        val kept = mutableListOf<ParsedProgramme>()
        var seen = 0
        XmltvParser().parse(
            input = ByteArrayInputStream(xml),
            sink = object : XmltvSink {
                override fun onChannels(channels: List<ParsedGuideChannel>) = Unit
                override fun onProgrammes(batch: List<ParsedProgramme>) {
                    kept += batch
                }
            },
            wanted = { id ->
                seen++
                id == "wanted"
            },
        )
        assertEquals(listOf("Keep one", "Keep two"), kept.map { it.title })
        assertEquals(4, seen)
        // The count is only what fits the viewer's channels, and it isn't on screen yet.
        assertEquals("Backup guide 2 of 3: 62,053 programmes for your channels", BackupGuide.readingLine(2, 3, 62_053))
        assertEquals("Backup guide 1 of 3", BackupGuide.readingLine(1, 3, 0))
    }
}
