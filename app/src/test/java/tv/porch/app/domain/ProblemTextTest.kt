package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProblemTextTest {
    @Test
    fun aMissingGuideSaysSoInPlainWordsWithTheCodeKeptForSmallPrint() {
        val problem = ProblemText.of(SyncPart.Guide, SyncFailure.Status, code = 404)
        assertEquals("Guide address not found. Check it in Settings.", problem.line)
        assertEquals("Guide not found", problem.short)
        assertEquals(404, problem.code)
    }

    @Test
    fun everyFailureIsPlainWordsAndBlamesThePartThatFailed() {
        val all = SyncPart.entries.flatMap { part ->
            SyncFailure.entries.flatMap { failure ->
                listOf(404, 401, 500, 400, null).flatMap { code ->
                    listOf(true, false).map { xtream -> ProblemText.of(part, failure, code, xtream) }
                }
            }
        }
        all.forEach { problem ->
            assertFalse(problem.line, problem.line.contains("HTTP"))
            assertFalse(problem.short, problem.short.contains("HTTP"))
        }
        assertEquals("Playlist address not found. Check it in Settings.", ProblemText.of(SyncPart.Playlist, SyncFailure.Status, 404).line)
        assertEquals("Server not found. Check the server address in Settings.", ProblemText.of(SyncPart.Playlist, SyncFailure.Status, 404, xtream = true).line)
        assertEquals(
            "Login was refused. Check the username and password in Settings.",
            ProblemText.of(SyncPart.Playlist, SyncFailure.Refused, 403, xtream = true).line,
        )
        assertEquals(
            "The guide's server took too long to answer. Porch will try again later.",
            ProblemText.of(SyncPart.Guide, SyncFailure.Slow).line,
        )
    }
}
