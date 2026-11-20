package tv.porch.app.setup

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import tv.porch.app.domain.BackupGuide
import tv.porch.app.domain.SourceDraft
import tv.porch.app.domain.cleaned

/**
 * A tiny page on the TV's Wi-Fi so a phone can type the playlist login.
 * It only runs while the screen is open, and only answers at an address with a new [SetupCode] each time.
 * The page is plain HTTP, so use it on a network you trust.
 *
 * [forGuides] runs the same server for the backup guide addresses. It offers only that page.
 * [forPosters] does the same for the poster ratings key. The key is a secret: no page ever shows it,
 * not even the one saved, and it is never put in an error.
 */
class SetupServer private constructor(
    private val onSaved: ((SourceDraft) -> Unit)?,
    private val onGuides: ((List<String>) -> Unit)?,
    private val onKey: ((String?) -> Boolean)? = null,
) {
    constructor(onSaved: (SourceDraft) -> Unit) : this(onSaved, null, null)

    private val code = SetupCode.generate(SecureRandom())
    private var socket: ServerSocket? = null
    @Volatile private var running = false
    private var thread: Thread? = null
    var url: String = ""
        private set

    fun start(): String {
        var bound: ServerSocket? = null
        var port = 0
        var last: Exception? = null
        for (candidate in 8765..8772) {
            try {
                val server = ServerSocket()
                server.reuseAddress = true
                server.bind(InetSocketAddress("0.0.0.0", candidate))
                bound = server
                port = candidate
                break
            } catch (error: Exception) {
                last = error
            }
        }
        val server = bound ?: throw IllegalStateException(last?.message ?: "Couldn't open a port for phone setup")
        socket = server
        running = true
        val ip = lanAddress() ?: throw IllegalStateException("This TV doesn't have a Wi-Fi address yet")
        url = "http://$ip:$port/$code/"
        thread = Thread({
            while (running && !server.isClosed) {
                try {
                    val client = server.accept()
                    Thread({ handle(client) }, "porch-setup-client").apply { isDaemon = true }.start()
                } catch (_: Exception) {
                    if (!running) break
                }
            }
        }, "porch-setup").apply { isDaemon = true; start() }
        return url
    }

    fun stop() {
        running = false
        try {
            socket?.close()
        } catch (_: Exception) {
            // Closing the socket is how the accept loop ends.
        }
    }

    private fun handle(client: Socket) {
        client.use { socket ->
            socket.soTimeout = 8_000
            try {
                val input = BufferedInputStream(socket.getInputStream())
                val request = readHeaders(input)
                val body = if (request.contentLength > 0) {
                    readBody(input, request.contentLength.coerceAtMost(64 * 1024))
                } else {
                    ""
                }
                when {
                    !SetupCode.allows(request.path, code) -> respond(socket, 404, WRONG_ADDRESS)
                    request.method == "GET" -> respond(socket, 200, formPage(null))
                    request.method == "POST" -> when {
                        onKey != null -> submitKey(socket, body, onKey)
                        onGuides != null -> submitGuides(socket, body, onGuides)
                        else -> submit(socket, body)
                    }
                    else -> respond(socket, 405, formPage("Use the form on this page."))
                }
            } catch (_: Exception) {
                try {
                    respond(socket, 400, formPage("That request didn't come through. Try again."))
                } catch (_: Exception) {
                    // The phone already went away.
                }
            }
        }
    }

    private fun formPage(error: String?): String = when {
        onKey != null -> keyPage(error)
        onGuides != null -> guidesPage(error)
        else -> page(error)
    }

    private fun submitKey(socket: Socket, body: String, save: (String?) -> Boolean) {
        val field = parseForm(body)["key"].orEmpty()
        if (!save(field.ifBlank { null })) {
            respond(socket, 400, keyPage("Nothing saved. A key is letters, numbers, dashes and underscores, with no spaces."))
            return
        }
        respond(socket, 200, keySavedPage(field.isBlank()))
    }

    private fun submitGuides(socket: Socket, body: String, save: (List<String>) -> Unit) {
        val field = parseForm(body)["addresses"]
        val clean = BackupGuide.submission(field)
        if (clean == null) {
            respond(socket, 400, guidesPage("Nothing saved. Each line needs to be an address starting with http or https."))
            return
        }
        save(clean)
        val lines = field.orEmpty().lines().count { it.isNotBlank() }
        respond(socket, 200, guidesSavedPage(clean.size, lines - clean.size))
    }

    private fun submit(socket: Socket, body: String) {
        val form = parseForm(body)
        val draft = SourceDraft(
            name = form["name"].orEmpty(),
            kind = form["kind"].orEmpty().ifBlank { "m3u" },
            m3uUrl = form["m3u"].orEmpty(),
            server = form["server"].orEmpty(),
            username = form["username"].orEmpty(),
            password = form["password"].orEmpty(),
            xmltvUrl = form["xmltv"].orEmpty(),
        )
        try {
            onSaved?.invoke(draft.cleaned())
            respond(socket, 200, savedPage())
        } catch (error: IllegalArgumentException) {
            respond(socket, 400, page(error.message))
        } catch (_: Exception) {
            respond(socket, 500, page("The TV couldn't save that. Leave this page open and try again."))
        }
    }

    private fun respond(socket: Socket, code: Int, html: String) {
        val bytes = html.toByteArray(StandardCharsets.UTF_8)
        val reason = if (code == 200) "OK" else "Error"
        val header = buildString {
            append("HTTP/1.1 $code $reason\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n")
            append("Cache-Control: no-store\r\n\r\n")
        }
        val out = BufferedOutputStream(socket.getOutputStream())
        out.write(header.toByteArray(StandardCharsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    companion object {
        /** The server for the Backup guides screen. It replaces the list with what the phone sends. */
        fun forGuides(onGuides: (List<String>) -> Unit): SetupServer = SetupServer(null, onGuides, null)

        /** The server for the Poster ratings screen. [onKey] gets the key, or null to remove it, and says whether it was usable. */
        fun forPosters(onKey: (String?) -> Boolean): SetupServer = SetupServer(null, null, onKey)
    }
}

private const val WRONG_ADDRESS = "<!doctype html><title>Porch</title><p>Use the full address shown on the TV.</p>"

private data class Head(val method: String, val path: String, val contentLength: Int)

private fun readHeaders(input: BufferedInputStream): Head {
    val raw = ByteArrayOutput()
    var last = 0
    var matched = 0
    val marker = "\r\n\r\n"
    var count = 0
    while (count < 16 * 1024) {
        val value = input.read()
        if (value < 0) break
        raw.write(value)
        count++
        if (value == marker[matched].code) {
            matched++
            if (matched == marker.length) break
        } else {
            matched = if (value == marker[0].code) 1 else 0
        }
        last = value
    }
    val text = raw.toByteArray().toString(StandardCharsets.ISO_8859_1)
    val first = text.lineSequence().firstOrNull().orEmpty()
    val method = first.substringBefore(' ').uppercase()
    val path = first.split(' ').getOrElse(1) { "" }.substringBefore('?')
    val length = Regex("""(?i)content-length:\s*(\d+)""")
        .find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    if (last == 0 && text.isEmpty()) return Head("GET", "", 0)
    return Head(method, path, length)
}

private fun readBody(input: BufferedInputStream, length: Int): String {
    val data = ByteArray(length)
    var offset = 0
    while (offset < length) {
        val read = input.read(data, offset, length - offset)
        if (read < 0) break
        offset += read
    }
    return data.copyOf(offset).toString(StandardCharsets.UTF_8)
}

private fun parseForm(body: String): Map<String, String> {
    if (body.isBlank()) return emptyMap()
    return body.split('&').mapNotNull { part ->
        val index = part.indexOf('=')
        if (index < 0) null
        else {
            URLDecoder.decode(part.substring(0, index), StandardCharsets.UTF_8) to
                URLDecoder.decode(part.substring(index + 1), StandardCharsets.UTF_8)
        }
    }.toMap()
}

internal fun lanAddress(): String? {
    val found = mutableListOf<String>()
    val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
    for (network in interfaces) {
        if (!network.isUp || network.isLoopback) continue
        for (address in network.inetAddresses) {
            if (address is Inet4Address && !address.isLoopbackAddress) {
                val host = address.hostAddress ?: continue
                if (!host.startsWith("169.254.")) found.add(host)
            }
        }
    }
    return found.firstOrNull { it.startsWith("192.168.") }
        ?: found.firstOrNull { it.startsWith("10.") }
        ?: found.firstOrNull { private172(it) }
        ?: found.firstOrNull()
}

private fun private172(host: String): Boolean {
    if (!host.startsWith("172.")) return false
    val second = host.split('.').getOrNull(1)?.toIntOrNull() ?: return false
    return second in 16..31
}

private class ByteArrayOutput {
    private val buffer = java.io.ByteArrayOutputStream()
    fun write(value: Int) = buffer.write(value)
    fun toByteArray(): ByteArray = buffer.toByteArray()
}

private fun page(error: String?): String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Porch setup</title>
<style>
  body { margin: 0; background: #0e1117; color: #f4f6fb; font-family: -apple-system, BlinkMacSystemFont, sans-serif; }
  main { max-width: 32rem; margin: 0 auto; padding: 1.5rem; }
  h1 { font-size: 1.6rem; margin: 0 0 .25rem; }
  p { color: #9aa3b5; }
  label { display: block; margin: 1rem 0 .35rem; color: #c5cedd; }
  input, select { width: 100%; box-sizing: border-box; font-size: 1.05rem; padding: .8rem; border-radius: 12px; border: 0; background: #1b2130; color: white; }
  button { margin-top: 1.4rem; width: 100%; font-size: 1.05rem; padding: .9rem; border: 0; border-radius: 999px; background: white; color: #111; font-weight: 650; }
  .error { background: #3a1d22; color: #ffb4b4; padding: .8rem 1rem; border-radius: 12px; }
</style>
</head>
<body>
<main>
  <h1>Add your TV source</h1>
  <p>This page is served by the TV. It closes when you leave phone setup on the TV.</p>
  ${if (error != null) "<p class=\"error\">${escapeHtml(error)}</p>" else ""}
  <form method="post">
    <label for="name">Name</label>
    <input id="name" name="name" placeholder="Living room" autocomplete="off">
    <label for="kind">Type</label>
    <select id="kind" name="kind">
      <option value="m3u">M3U playlist URL</option>
      <option value="xtream">Xtream Codes login</option>
    </select>
    <label for="m3u">Playlist URL</label>
    <input id="m3u" name="m3u" inputmode="url" placeholder="http://example.com/playlist.m3u" autocomplete="off">
    <label for="server">Xtream server</label>
    <input id="server" name="server" inputmode="url" placeholder="http://example.com:8080" autocomplete="off">
    <label for="username">Username</label>
    <input id="username" name="username" autocomplete="off">
    <label for="password">Password</label>
    <input id="password" name="password" type="password" autocomplete="off">
    <label for="xmltv">Guide URL (optional for an Xtream login)</label>
    <input id="xmltv" name="xmltv" inputmode="url" placeholder="http://example.com/guide.xml" autocomplete="off">
    <button type="submit">Save on the TV</button>
  </form>
</main>
</body>
</html>
""".trimIndent()

private fun savedPage(): String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Saved</title>
<style>
  body { margin: 0; background: #0e1117; color: #f4f6fb; font-family: -apple-system, BlinkMacSystemFont, sans-serif; }
  main { max-width: 32rem; margin: 0 auto; padding: 2rem 1.5rem; }
  p { color: #9aa3b5; }
</style>
</head>
<body>
<main>
  <h1>Saved</h1>
  <p>Look at the TV. Porch is fetching the channels and the guide. You can close this page.</p>
</main>
</body>
</html>
""".trimIndent()

// Saved addresses are never put on this page or the next one: an address can hold a login.
private fun guidesPage(error: String?): String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Porch backup guides</title>
<style>
  body { margin: 0; background: #0e1117; color: #f4f6fb; font-family: -apple-system, BlinkMacSystemFont, sans-serif; }
  main { max-width: 32rem; margin: 0 auto; padding: 1.5rem; }
  h1 { font-size: 1.6rem; margin: 0 0 .25rem; }
  p { color: #9aa3b5; }
  label { display: block; margin: 1rem 0 .35rem; color: #c5cedd; }
  textarea { width: 100%; box-sizing: border-box; min-height: 10rem; font-size: 1.05rem; padding: .8rem; border-radius: 12px; border: 0; background: #1b2130; color: white; }
  button { margin-top: 1.4rem; width: 100%; font-size: 1.05rem; padding: .9rem; border: 0; border-radius: 999px; background: white; color: #111; font-weight: 650; }
  .error { background: #3a1d22; color: #ffb4b4; padding: .8rem 1rem; border-radius: 12px; }
</style>
</head>
<body>
<main>
  <h1>Backup guides</h1>
  <p>This page is served by the TV and closes when you leave Backup guides there. Only use it on a network you trust.</p>
  ${if (error != null) "<p class=\"error\">${escapeHtml(error)}</p>" else ""}
  <form method="post">
    <label for="addresses">Guide addresses, one per line, up to ${BackupGuide.MAX_ADDRESSES}</label>
    <textarea id="addresses" name="addresses" inputmode="url" placeholder="http://example.com/guide.xml" autocomplete="off" autocapitalize="off" spellcheck="false"></textarea>
    <p>Sending replaces the whole list. Addresses already saved aren't shown here.</p>
    <button type="submit">Send</button>
  </form>
</main>
</body>
</html>
""".trimIndent()

private fun guidesSavedPage(saved: Int, leftOut: Int): String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Saved</title>
<style>
  body { margin: 0; background: #0e1117; color: #f4f6fb; font-family: -apple-system, BlinkMacSystemFont, sans-serif; }
  main { max-width: 32rem; margin: 0 auto; padding: 2rem 1.5rem; }
  p { color: #9aa3b5; }
</style>
</head>
<body>
<main>
  <h1>Saved</h1>
  <p>${if (saved == 0) "The list is now empty." else if (saved == 1) "1 address saved." else "$saved addresses saved."}${if (leftOut > 0) " Repeats, lines that weren't addresses and anything past ${BackupGuide.MAX_ADDRESSES} were left out." else ""}</p>
  <p>Porch uses the list from the next refresh. Press Refresh now on the TV to do it straight away. You can close this page.</p>
</main>
</body>
</html>
""".trimIndent()

// The saved key is never put on this page or the next one.
private fun keyPage(error: String?): String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Porch poster ratings</title>
<style>
  body { margin: 0; background: #0e1117; color: #f4f6fb; font-family: -apple-system, BlinkMacSystemFont, sans-serif; }
  main { max-width: 32rem; margin: 0 auto; padding: 1.5rem; }
  h1 { font-size: 1.6rem; margin: 0 0 .25rem; }
  p { color: #9aa3b5; }
  label { display: block; margin: 1rem 0 .35rem; color: #c5cedd; }
  input { width: 100%; box-sizing: border-box; font-size: 1.05rem; padding: .8rem; border-radius: 12px; border: 0; background: #1b2130; color: white; }
  button { margin-top: 1.4rem; width: 100%; font-size: 1.05rem; padding: .9rem; border: 0; border-radius: 999px; background: white; color: #111; font-weight: 650; }
  .error { background: #3a1d22; color: #ffb4b4; padding: .8rem 1rem; border-radius: 12px; }
</style>
</head>
<body>
<main>
  <h1>Poster ratings</h1>
  <p>This page is served by the TV and closes when you leave Poster ratings there. Only use it on a network you trust.</p>
  ${if (error != null) "<p class=\"error\">${escapeHtml(error)}</p>" else ""}
  <form method="post">
    <label for="key">Your RatingPosterDB key</label>
    <input id="key" name="key" type="password" autocomplete="off" autocapitalize="off" spellcheck="false">
    <p>Sending replaces the saved key. Send it blank to remove the key. A saved key isn't shown here.</p>
    <button type="submit">Send</button>
  </form>
</main>
</body>
</html>
""".trimIndent()

private fun keySavedPage(removed: Boolean): String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Saved</title>
<style>
  body { margin: 0; background: #0e1117; color: #f4f6fb; font-family: -apple-system, BlinkMacSystemFont, sans-serif; }
  main { max-width: 32rem; margin: 0 auto; padding: 2rem 1.5rem; }
  p { color: #9aa3b5; }
</style>
</head>
<body>
<main>
  <h1>${if (removed) "Removed" else "Saved"}</h1>
  <p>${if (removed) "The key is removed and Porch has stopped using it." else "The key is saved on the TV. Posters with ratings show on the 24/7 tab as they load."} You can close this page.</p>
</main>
</body>
</html>
""".trimIndent()

private fun escapeHtml(text: String): String = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
