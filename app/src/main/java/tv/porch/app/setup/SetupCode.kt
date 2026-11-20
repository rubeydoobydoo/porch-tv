package tv.porch.app.setup

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Random

/**
 * The one-time part of the phone page's address. Without it, anything else on the Wi-Fi, or a web page open on another
 * device there, could read or post to the page while it's on screen.
 */
object SetupCode {
    /** No 0, O, 1, I or L, so it can be read off a TV and typed. */
    private const val LETTERS = "abcdefghjkmnpqrstuvwxyz23456789"

    fun generate(random: Random): String = String(CharArray(6) { LETTERS[random.nextInt(LETTERS.length)] })

    fun allows(path: String, code: String): Boolean = MessageDigest.isEqual(
        path.removeSuffix("/").toByteArray(StandardCharsets.UTF_8),
        "/$code".toByteArray(StandardCharsets.UTF_8),
    )
}
