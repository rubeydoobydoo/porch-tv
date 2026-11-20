package tv.porch.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class GuideDownloadTest {
    @get:Rule val folder = TemporaryFolder()

    private val guide = "<tv><channel id=\"a.example\"/></tv>".toByteArray()

    /** Hands out one stream per call, in order, and records whether each was closed. */
    private class FakeFetcher(private val streams: List<() -> InputStream>) : HttpFetcher {
        var opened = 0
        val closed = mutableListOf<Boolean>()
        override fun open(url: String): InputStream {
            val index = opened++
            closed += false
            val inner = streams[index]()
            return object : InputStream() {
                override fun read() = inner.read()
                override fun read(b: ByteArray, off: Int, len: Int) = inner.read(b, off, len)
                override fun close() { closed[index] = true }
            }
        }
    }

    private fun cutOff(bytes: ByteArray) = object : InputStream() {
        private var sent = false
        override fun read(): Int = throw UnsupportedOperationException()
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (sent) throw IOException("unexpected end of stream")
            sent = true
            val n = minOf(len, bytes.size / 2)
            System.arraycopy(bytes, 0, b, off, n)
            return n
        }
    }

    @Test
    fun theWholeGuideIsSavedAndTheConnectionClosedBeforeReading() {
        val http = FakeFetcher(listOf({ ByteArrayInputStream(guide) }))
        val file = folder.newFile("guide.tmp")
        GuideDownload.toFile(http, "http://example.com/xmltv.xml", file)
        assertEquals(String(guide), file.readText())
        assertEquals(listOf(true), http.closed)
    }

    @Test
    fun aDownloadCutOffPartWayIsTriedOnceMore() {
        val http = FakeFetcher(listOf({ cutOff(guide) }, { ByteArrayInputStream(guide) }))
        val file = folder.newFile("guide.tmp")
        GuideDownload.toFile(http, "http://example.com/xmltv.xml", file)
        assertEquals(2, http.opened)
        assertEquals(String(guide), file.readText())
    }

    @Test
    fun aRefusedLoginIsNotRetried() {
        val http = object : HttpFetcher {
            var opened = 0
            override fun open(url: String): InputStream { opened++; throw AuthRejectedException(403) }
        }
        try {
            GuideDownload.toFile(http, "http://example.com/xmltv.xml", folder.newFile("guide.tmp"))
            fail("expected the refusal")
        } catch (expected: AuthRejectedException) {
            assertEquals(1, http.opened)
        }
    }

    @Test
    fun anHttpErrorIsNotRetriedAndKeepsItsCode() {
        val http = object : HttpFetcher {
            var opened = 0
            override fun open(url: String): InputStream { opened++; throw HttpStatusException(404) }
        }
        try {
            GuideDownload.toFile(http, "http://example.com/xmltv.xml", folder.newFile("guide.tmp"))
            fail("expected the error")
        } catch (expected: HttpStatusException) {
            assertEquals(1, http.opened)
            assertEquals("HTTP 404", expected.message)
        }
    }

    @Test
    fun leftoverGuideFilesAreCleared() {
        folder.newFile("guide-7.tmp").writeText("half a guide")
        val other = folder.newFile("logo.png")
        GuideDownload.clearLeftovers(folder.root)
        assertEquals(listOf(other.name), folder.root.list()!!.toList())
    }

    @Test
    fun twoCutOffsSayItPlainly() {
        val http = FakeFetcher(listOf({ cutOff(guide) }, { cutOff(guide) }))
        try {
            GuideDownload.toFile(http, "http://example.com/xmltv.xml", folder.newFile("guide.tmp"))
            fail("expected a failure")
        } catch (expected: IOException) {
            assertTrue(expected.message!!.startsWith("The guide download stopped part way"))
        }
    }
}
