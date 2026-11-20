package tv.porch.app.domain

/** Which part of a refresh failed: the channel list (a playlist or an Xtream login) or the guide. */
enum class SyncPart { Playlist, Guide }

/** What went wrong, worked out from the error in the data layer. */
enum class SyncFailure {
    /** The server answered with a code outside 2xx (not 401 or 403). */
    Status,

    /** 401 or 403. */
    Refused,

    /** The server's name didn't resolve, or nothing answered. */
    NoServer,

    /** The server took too long. */
    Slow,

    /** The download stopped part way. */
    CutOff,
    Other,
}

/**
 * A failed refresh in plain words. [line] is the sentence for Home and Settings, [short] fits at the end of a Settings row,
 * and [code] is the HTTP code, for small print only. None of them ever holds an address.
 */
data class SourceProblem(val line: String, val short: String, val code: Int? = null)

/** Turns a failure into words a viewer can act on. "HTTP 404" alone told nobody what to fix. */
object ProblemText {
    /**
     * [xtream] says the channel list came from a login, so "server" and "username and password" are the right words.
     * [detail] is one of Porch's own sentences (an empty guide, no channels found), used for [SyncFailure.Other].
     */
    fun of(part: SyncPart, failure: SyncFailure, code: Int? = null, xtream: Boolean = false, detail: String? = null): SourceProblem {
        val found = code == 404 || code == 410
        val serverFault = code != null && code >= 500
        return if (part == SyncPart.Guide) {
            when (failure) {
                SyncFailure.Status -> when {
                    found -> SourceProblem("Guide address not found. Check it in Settings.", "Guide not found", code)
                    serverFault -> SourceProblem("The guide's server had a problem. Porch will try again later.", "Guide server problem", code)
                    else -> SourceProblem("The guide address didn't work. Check it in Settings.", "Guide didn't load", code)
                }
                SyncFailure.Refused ->
                    if (xtream) SourceProblem("The guide refused the login. Check the username and password in Settings.", "Guide refused login", code)
                    else SourceProblem("The guide address was refused. Check it in Settings.", "Guide refused", code)
                SyncFailure.NoServer -> SourceProblem("Couldn't reach the guide's server. Check the guide address and the internet.", "Can't reach guide", code)
                SyncFailure.Slow -> SourceProblem("The guide's server took too long to answer. Porch will try again later.", "Guide too slow", code)
                SyncFailure.CutOff -> SourceProblem("The guide download stopped part way. Porch will try again later.", "Guide cut off", code)
                SyncFailure.Other -> SourceProblem(detail ?: "The guide didn't load. Porch will try again later.", "Guide didn't load", code)
            }
        } else {
            when (failure) {
                SyncFailure.Status -> when {
                    found && xtream -> SourceProblem("Server not found. Check the server address in Settings.", "Server not found", code)
                    found -> SourceProblem("Playlist address not found. Check it in Settings.", "Playlist not found", code)
                    serverFault -> SourceProblem("The provider's server had a problem. Porch will try again later.", "Server problem", code)
                    xtream -> SourceProblem("The server didn't answer properly. Check the server address in Settings.", "Server didn't answer", code)
                    else -> SourceProblem("The playlist address didn't work. Check it in Settings.", "Playlist didn't load", code)
                }
                SyncFailure.Refused ->
                    if (xtream) SourceProblem("Login was refused. Check the username and password in Settings.", "Login refused", code)
                    else SourceProblem("The playlist address was refused. Check it in Settings.", "Playlist refused", code)
                SyncFailure.NoServer -> SourceProblem("Couldn't reach the server. Check the address and the internet.", "Can't reach server", code)
                SyncFailure.Slow -> SourceProblem("The server took too long to answer. Porch will try again later.", "Server too slow", code)
                SyncFailure.CutOff -> SourceProblem("The channel list stopped part way. Porch will try again later.", "Channels cut off", code)
                SyncFailure.Other -> SourceProblem(detail ?: "Couldn't update this source. Porch will try again later.", "Didn't update", code)
            }
        }
    }
}
