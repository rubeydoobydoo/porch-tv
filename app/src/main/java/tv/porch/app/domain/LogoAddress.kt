package tv.porch.app.domain

/** Which logo addresses may be fetched. Anything else keeps the letter badge. */
object LogoAddress {
    /** The trimmed address when it is http or https with something after the scheme, else null. */
    fun clean(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        val prefix = when {
            text.startsWith("https://", ignoreCase = true) -> "https://"
            text.startsWith("http://", ignoreCase = true) -> "http://"
            else -> return null
        }
        return text.takeIf { it.length > prefix.length }
    }
}
