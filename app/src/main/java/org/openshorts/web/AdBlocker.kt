package org.openshorts.web

import java.net.URI

/**
 * Request filter for the in-app WebViews (like a small uBlock Origin list).
 *
 * Blocks, by host (incl. subdomains):
 * - ad networks and trackers,
 * - cookie-consent platforms (their banners then never load),
 * - TikTok/YouTube telemetry endpoints that are not needed for playback.
 * Plus a few URL path patterns used by ad/analytics scripts on any host.
 *
 * Pure Kotlin (no Android types), so it is unit-tested.
 */
object AdBlocker {

    /** Hosts whose requests are dropped (subdomains included). */
    val BLOCKED_HOSTS: Set<String> = setOf(
        // Ads
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
        "pagead2.googlesyndication.com", "amazon-adsystem.com", "adnxs.com", "criteo.com", "criteo.net",
        "taboola.com", "outbrain.com", "pubmatic.com", "rubiconproject.com", "openx.net", "moatads.com",
        "adsrvr.org", "smartadserver.com", "teads.tv", "ads.tiktok.com", "ads-api.tiktok.com",
        // Trackers / analytics
        "google-analytics.com", "googletagmanager.com", "googletagservices.com", "analytics.google.com",
        "facebook.net", "connect.facebook.net", "scorecardresearch.com", "hotjar.com", "mixpanel.com",
        "segment.io", "segment.com", "branch.io", "app-measurement.com", "appsflyer.com", "adjust.com",
        "analytics.tiktok.com", "analytics-sg.tiktok.com", "mon.tiktokv.com", "mon-va.tiktokv.com",
        "mcs.tiktokv.com", "mcs-va.tiktokv.com", "mssdk.tiktokw.us", "log.tiktokv.com", "log-va.tiktokv.com",
        // Cookie-consent platforms (banners)
        "cookielaw.org", "onetrust.com", "cookiebot.com", "consentcdn.cookiebot.com", "usercentrics.eu",
        "privacy-mgmt.com", "consensu.org", "quantcast.com", "trustarc.com", "didomi.io", "cookiepro.com",
        "iubenda.com", "termly.io", "cookieyes.com", "consentmanager.net",
    )

    /** URL fragments of ad/analytics scripts that can appear on any host. */
    private val BLOCKED_PATHS = listOf(
        "/pagead/", "/ads?", "/adview", "/ptracking", "/api/stats/ads", "/pcs/activeview",
        "/analytics.js", "/gtag/js", "/gtm.js", "/collect?", "/log/sentry", "/web/report",
    )

    /** Hosts that must never be blocked, even if a pattern matches (video playback). */
    private val ALLOWED_HOSTS = setOf("googlevideo.com", "ytimg.com", "tiktokcdn.com", "tiktokcdn-us.com", "ttwstatic.com")

    fun shouldBlock(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        if (matches(host, ALLOWED_HOSTS) && !matches(host, BLOCKED_HOSTS)) return false
        if (matches(host, BLOCKED_HOSTS)) return true
        val path = (uri.rawPath.orEmpty() + if (uri.rawQuery != null) "?" + uri.rawQuery else "").lowercase()
        return BLOCKED_PATHS.any { it in path }
    }

    private fun matches(host: String, list: Set<String>): Boolean =
        list.any { host == it || host.endsWith(".$it") }
}

/** CSS selectors of cookie banners, consent dialogs and ad overlays hidden after page load. */
val CONSENT_AND_AD_SELECTORS = listOf(
    // TikTok (own banner, a custom element with shadow DOM)
    "tiktok-cookie-banner", "[class*=\"CookieBanner\"]", "[data-e2e=\"cookie-banner\"]",
    // YouTube mobile consent
    "ytm-consent-bump-v2-renderer", ".consent-bump-v2-lightbox", "ytm-promoted-sparkles-web-renderer",
    "ytm-companion-slot", "ad-slot-renderer", "ytm-statement-banner-renderer",
    // Consent platforms
    "#onetrust-consent-sdk", "#onetrust-banner-sdk", "#CybotCookiebotDialog", "#usercentrics-root",
    ".qc-cmp2-container", "#didomi-host", ".fc-consent-root", "#sp_message_container",
    "#truste-consent-track", ".cky-consent-container", "#cmpbox", "#cmpbox2",
    // Generic
    "[id*=\"cookie-banner\" i]", "[class*=\"cookie-banner\" i]", "[id*=\"cookie-consent\" i]",
    "[class*=\"cookie-consent\" i]", "[aria-label*=\"cookie\" i][role=\"dialog\"]",
    "[class*=\"adsbygoogle\"]", "iframe[src*=\"doubleclick\"]",
)
