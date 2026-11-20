package tv.porch.app.data

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import tv.porch.app.domain.ShowLookup
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** What TVmaze knows about a show: its poster address and IMDb id, either null. */
class ShowPoster(val url: String?, val imdb: String?) {
    companion object {
        val NONE = ShowPoster(null, null)
    }
}

/**
 * How a TVmaze answer is saved, as plain text so it can be tested without a phone.
 * "" is an old "nothing", written before ids were kept: it may be a show that matched without artwork, so it is asked
 * about once more. "2|<poster>|<imdb>" is the current form, and "2||" means TVmaze was asked with ids in view and has nothing.
 * Anything else is a poster address saved before ids were kept.
 */
object SavedPoster {
    private const val CURRENT = "2|"

    /** What is saved when TVmaze has nothing: current form, so it is not asked about again. */
    const val NOTHING = "2||"

    fun encode(poster: String?, imdb: String?): String = if (poster == null && imdb == null) NOTHING else "$CURRENT${poster.orEmpty()}|${imdb.orEmpty()}"

    /** True for an entry saved before ids were kept, so an id-wanting caller should ask once more. */
    fun lacksId(saved: String): Boolean = !saved.startsWith(CURRENT)

    fun read(saved: String): ShowPoster = when {
        saved.isEmpty() -> ShowPoster.NONE
        saved.startsWith(CURRENT) -> {
            val parts = saved.removePrefix(CURRENT).split('|')
            ShowPoster(parts.getOrNull(0)?.ifEmpty { null }, parts.getOrNull(1)?.ifEmpty { null })
        }
        else -> ShowPoster(saved, null)
    }
}

/**
 * Posters for the 24/7 tab, from TVmaze's free, keyless search (data is CC BY-SA, credited on the screen).
 *
 * A tile asks when it comes on screen. The answer, including "no poster", is kept per search text in its own
 * preferences file, so a show is asked about once. A failed call is not kept. Requests go one at a time, at most
 * two a second, off the main thread; a tile that leaves the screen drops out of the line. A 429 holds everything for a minute.
 * Only the show's name is sent, and nothing is logged.
 */
class ShowPosters(
    context: Context,
    private val client: OkHttpClient = defaultClient(),
    private val now: () -> Long = { SystemClock.elapsedRealtime() },
) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // Search text to what was saved (see [SavedPoster]). Filled from the file on first use.
    private val known = ConcurrentHashMap<String, String>()

    @Volatile
    private var loaded = false

    // A fair mutex is the line: waiters go in order, and one that is cancelled leaves it.
    private val turn = Mutex()
    private var lastStart = 0L
    private var holdUntil = 0L
    private val failedAt = HashMap<String, Long>()

    /**
     * What TVmaze has for [search]; [ShowPoster.NONE] when nothing or not yet. With [wantId], an entry saved before IMDb ids
     * were kept is asked about once more. Cancel it to leave the line.
     */
    suspend fun poster(search: String, wantId: Boolean = false): ShowPoster {
        val key = search.trim().lowercase()
        if (key.isEmpty()) return ShowPoster.NONE
        if (!loaded) withContext(Dispatchers.IO) { load() }
        known[key]?.let { if (!(wantId && lacksId(it))) return read(it) }
        return withContext(Dispatchers.IO) {
            turn.withLock {
                known[key]?.let { if (!(wantId && lacksId(it))) return@withLock read(it) }
                // A recent failure isn't retried at once; an old entry still has its poster.
                failedAt[key]?.let { if (now() - it < RETRY_AFTER_MS) return@withLock known[key]?.let(::read) ?: ShowPoster.NONE }
                ask(key, search.trim()) ?: known[key]?.let(::read) ?: ShowPoster.NONE
            }
        }
    }

    private fun lacksId(saved: String): Boolean = SavedPoster.lacksId(saved)

    private fun read(saved: String): ShowPoster = SavedPoster.read(saved)

    private suspend fun ask(key: String, search: String): ShowPoster? {
        var refused = 0
        while (true) {
            val wait = maxOf(holdUntil - now(), lastStart + SPACING_MS - now())
            if (wait > 0) delay(wait)
            lastStart = now()
            val outcome = try {
                call(search)
            } catch (error: IOException) {
                failedAt[key] = now()
                return null
            }
            when (outcome.code) {
                429 -> {
                    holdUntil = now() + BACK_OFF_MS
                    // Don't hold the line for ever: give up for now and let the next ask try again later.
                    if (++refused >= MAX_REFUSALS) {
                        failedAt[key] = now()
                        return null
                    }
                }
                404 -> return remember(key, SavedPoster.NOTHING)
                in 200..299 -> {
                    val found = try {
                        ShowLookup.found(outcome.body, search)
                    } catch (error: IllegalArgumentException) {
                        failedAt[key] = now()
                        return null
                    }
                    return remember(key, SavedPoster.encode(found?.poster, found?.imdb))
                }
                else -> {
                    failedAt[key] = now()
                    return null
                }
            }
        }
    }

    private fun remember(key: String, value: String): ShowPoster {
        known[key] = value
        failedAt.remove(key)
        prefs.edit().putString(key, value).apply()
        return read(value)
    }

    private fun load() {
        if (loaded) return
        for ((key, value) in prefs.all) if (value is String) known[key] = value
        loaded = true
    }

    private class Answer(val code: Int, val body: String)

    private suspend fun call(search: String): Answer {
        val url = ENDPOINT.toHttpUrl().newBuilder().addQueryParameter("q", search).build()
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val answer = try {
                            Answer(it.code, if (it.isSuccessful) it.body?.string().orEmpty() else "")
                        } catch (e: IOException) {
                            if (continuation.isActive) continuation.resumeWithException(e)
                            return
                        }
                        if (continuation.isActive) continuation.resume(answer)
                    }
                }
            })
        }
    }

    companion object {
        private const val FILE = "show_posters"
        private const val ENDPOINT = "https://api.tvmaze.com/singlesearch/shows"
        const val SPACING_MS = 500L
        const val MAX_REFUSALS = 3
        const val BACK_OFF_MS = 60_000L
        const val RETRY_AFTER_MS = 5 * 60_000L
        const val TIMEOUT_SECONDS = 6L

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(TIMEOUT_SECONDS * 2, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}
