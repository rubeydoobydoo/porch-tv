package tv.porch.app.data

import android.content.Context
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import tv.porch.app.domain.ProblemText
import tv.porch.app.domain.SourceProblem
import tv.porch.app.domain.SyncFailure
import tv.porch.app.domain.SyncPart

/** The guide part of a refresh failed. The channels were still saved. */
class GuideRefreshException(cause: Exception) : Exception(cause.message, cause)

/** Turns a refresh's error into plain words. Only Porch's own sentences are passed through; a parser's message could quote the reply. */
object SourceProblems {
    fun of(error: Throwable, xtream: Boolean): SourceProblem {
        val part = if (error is GuideRefreshException) SyncPart.Guide else SyncPart.Playlist
        val cause = if (error is GuideRefreshException) error.cause ?: error else error
        return when (cause) {
            is AuthRejectedException -> ProblemText.of(part, SyncFailure.Refused, cause.code, xtream)
            is HttpStatusException -> ProblemText.of(part, SyncFailure.Status, cause.code, xtream)
            is UnknownHostException, is ConnectException, is NoRouteToHostException -> ProblemText.of(part, SyncFailure.NoServer, xtream = xtream)
            is SocketTimeoutException -> ProblemText.of(part, SyncFailure.Slow, xtream = xtream)
            is GuideCutOffException -> ProblemText.of(part, SyncFailure.CutOff, xtream = xtream)
            else -> {
                val own = cause.javaClass == IllegalStateException::class.java || cause.javaClass == IllegalArgumentException::class.java
                ProblemText.of(part, SyncFailure.Other, xtream = xtream, detail = if (own) cause.message else null)
            }
        }
    }
}

/**
 * The last failed refresh of each source, kept across restarts. A failed guide still marks the source as refreshed, so
 * nothing tries again for hours, and Home has to know why it has no guide. Plain words and the code only, never an address.
 */
class ProblemStore(context: Context) {
    private val prefs = context.getSharedPreferences("porch", Context.MODE_PRIVATE)

    fun all(): Map<Long, SourceProblem> = prefs.all.mapNotNull { (key, value) ->
        val id = key.removePrefix(PREFIX).takeIf { key.startsWith(PREFIX) }?.toLongOrNull() ?: return@mapNotNull null
        val parts = (value as? String)?.split('\n') ?: return@mapNotNull null
        if (parts.size != 3) return@mapNotNull null
        id to SourceProblem(line = parts[2], short = parts[1], code = parts[0].toIntOrNull())
    }.toMap()

    fun save(id: Long, problem: SourceProblem) {
        prefs.edit().putString("$PREFIX$id", "${problem.code ?: ""}\n${problem.short}\n${problem.line}").apply()
    }

    fun clear(id: Long) {
        prefs.edit().remove("$PREFIX$id").apply()
    }

    private companion object {
        const val PREFIX = "source_problem_"
    }
}
