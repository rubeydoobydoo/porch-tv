package tv.porch.app.data

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class AuthRejectedException(val code: Int) : IOException("Login was refused ($code)")

/** Any other answer outside 2xx. The message is the code only, never the address. */
class HttpStatusException(val code: Int) : IOException("HTTP $code")

interface HttpFetcher {
    fun open(url: String): InputStream
}

/** Small HTTP GET client. Redirects between http and https are followed. Passwords are never put in errors. */
class UrlConnectionFetcher : HttpFetcher {
    override fun open(url: String): InputStream {
        var current = url
        repeat(5) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 45_000
                setRequestProperty("User-Agent", "Porch/1.0 (Android TV)")
                setRequestProperty("Accept", "*/*")
            }
            try {
                connection.connect()
            } catch (error: IOException) {
                connection.disconnect()
                throw error
            }
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) throw IOException("The server redirected without a destination")
                current = URL(URL(current), location).toString()
                return@repeat
            }
            if (code == 401 || code == 403) {
                connection.disconnect()
                throw AuthRejectedException(code)
            }
            if (code !in 200..299) {
                connection.disconnect()
                throw HttpStatusException(code)
            }
            return object : FilterInputStream(connection.inputStream) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        connection.disconnect()
                    }
                }
            }
        }
        throw IOException("Too many redirects")
    }
}
