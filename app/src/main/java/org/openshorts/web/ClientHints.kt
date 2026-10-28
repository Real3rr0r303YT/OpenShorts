package org.openshorts.web

import android.os.Build
import android.webkit.WebSettings
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/** "…Chrome/130.0.6723.58 Mobile…" → "130.0.6723.58". */
fun chromeVersionOf(userAgent: String): String? = Regex("""Chrome/([\d.]+)""").find(userAgent)?.groupValues?.get(1)

/**
 * Makes the User-Agent client hints (Sec-CH-UA, Sec-CH-UA-Mobile, Sec-CH-UA-Platform, …) match a
 * regular Chrome on Android instead of "Android WebView". Needs a WebView that supports
 * [WebViewFeature.USER_AGENT_METADATA]; on older ones nothing changes.
 */
fun applyChromeClientHints(settings: WebSettings, userAgent: String) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) return
    val fullVersion = chromeVersionOf(userAgent) ?: return
    val major = fullVersion.substringBefore('.')
    fun brand(name: String, major: String, full: String) =
        UserAgentMetadata.BrandVersion.Builder().setBrand(name).setMajorVersion(major).setFullVersion(full).build()
    val metadata = UserAgentMetadata.Builder()
        .setBrandVersionList(
            listOf(
                brand("Chromium", major, fullVersion),
                brand("Google Chrome", major, fullVersion),
                brand("Not?A_Brand", "99", "99.0.0.0"),
            ),
        )
        .setFullVersion(fullVersion)
        .setPlatform("Android")
        .setPlatformVersion(Build.VERSION.RELEASE.orEmpty())
        .setModel(Build.MODEL.orEmpty())
        .setMobile(true)
        .build()
    WebSettingsCompat.setUserAgentMetadata(settings, metadata)
}
