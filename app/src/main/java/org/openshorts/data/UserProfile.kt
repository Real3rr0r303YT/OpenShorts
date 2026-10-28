package org.openshorts.data

/**
 * The local OpenShorts account. It never leaves the device: there is no server,
 * no sign-up and no password – the profile is just personal data stored in the
 * app's private storage.
 */
data class UserProfile(
    val displayName: String = "",
    val username: String = "",
    val bio: String = "",
    /** Absolute path of the avatar image in app-private storage. */
    val avatarPath: String? = null,
    /** Absolute path of the banner image in app-private storage. */
    val bannerPath: String? = null,
    /** Hue (0–360) of the gradient banner used when no banner image is set. */
    val bannerHue: Float = 200f,
    /** The user's own tip/donation link (e.g. Buy Me a Coffee, Liberapay, Ko-fi). */
    val supportUrl: String = "",
    /** Shows a "Supporter" badge on the profile. Purely self-declared and local. */
    val showSupporterBadge: Boolean = false,
    val createdAtMillis: Long = 0L,
) {
    val isSetUp: Boolean get() = displayName.isNotBlank() || username.isNotBlank()

    companion object {
        const val MAX_BIO_LENGTH = 160
        const val MAX_NAME_LENGTH = 40
        const val MAX_USERNAME_LENGTH = 24
    }
}

/** Lowercase letters, digits, "." and "_" — like most social networks. */
fun sanitizeUsername(input: String): String =
    input.trim().removePrefix("@").lowercase()
        .filter { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '_' }
        .take(UserProfile.MAX_USERNAME_LENGTH)

/** Accepts `https://…` links (scheme added if missing); returns null when invalid. */
fun normalizeSupportUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return ""
    val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
    val uri = runCatching { java.net.URI(withScheme) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank() || '.' !in uri.host) return null
    return withScheme
}
