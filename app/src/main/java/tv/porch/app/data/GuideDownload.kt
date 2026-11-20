package tv.porch.app.data

import java.io.File
import java.io.IOException

/** The guide download stopped part way, twice. */
class GuideCutOffException(cause: Throwable?) : IOException("The guide download stopped part way. Try again later.", cause)

/**
 * Saves the whole guide to a file before any of it is parsed. Reading straight off the network at
 * the speed a TV box can store programmes kept the connection open for many minutes, and providers
 * cut it off part way. A cut-off download is tried once more; a refused login or an HTTP error is not.
 */
object GuideDownload {
    fun toFile(http: HttpFetcher, url: String, file: File, attempts: Int = 2) {
        var last: IOException? = null
        var cutOff = false
        repeat(attempts) {
            var received = 0L
            try {
                http.open(url).use { input ->
                    file.outputStream().buffered(256 * 1024).use { out ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            received += read
                        }
                    }
                }
                return
            } catch (refused: AuthRejectedException) {
                throw refused
            } catch (status: HttpStatusException) {
                throw status
            } catch (error: IOException) {
                last = error
                if (received > 0) cutOff = true
            }
        }
        if (cutOff) throw GuideCutOffException(last)
        throw checkNotNull(last)
    }

    /** Removes guide files a killed download left behind. Call before any refresh starts. */
    fun clearLeftovers(dir: File) {
        dir.listFiles { file -> file.name.startsWith("guide-") && file.name.endsWith(".tmp") }?.forEach { it.delete() }
    }
}
