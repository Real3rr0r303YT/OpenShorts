package org.openshorts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openshorts.web.AdBlocker
import org.openshorts.web.chromeUserAgent

class AdBlockerTest {

    @Test
    fun `blocks ads, trackers and consent platforms`() {
        assertTrue(AdBlocker.shouldBlock("https://www.google-analytics.com/analytics.js"))
        assertTrue(AdBlocker.shouldBlock("https://cdn.cookielaw.org/scripttemplates/otSDKStub.js"))
        assertTrue(AdBlocker.shouldBlock("https://consent.cookiebot.com/uc.js"))
        assertTrue(AdBlocker.shouldBlock("https://mon.tiktokv.com/monitor_browser/collect/batch/"))
        assertTrue(AdBlocker.shouldBlock("https://static.doubleclick.net/instream/ad_status.js"))
    }

    @Test
    fun `keeps pages and video streams`() {
        assertFalse(AdBlocker.shouldBlock("https://www.tiktok.com/embed/v2/7312345678901234567"))
        assertFalse(AdBlocker.shouldBlock("https://v16-webapp-prime.tiktok.com/video/tos/abc.mp4"))
        assertFalse(AdBlocker.shouldBlock("https://rr3---sn-4g5e6nzz.googlevideo.com/videoplayback?expire=1&collect=1"))
        assertFalse(AdBlocker.shouldBlock("https://i.ytimg.com/vi/abc/hq720.jpg"))
        assertFalse(AdBlocker.shouldBlock("not a url"))
    }

    @Test
    fun `never blocks what the YouTube player needs`() {
        assertFalse(AdBlocker.shouldBlock("https://www.youtube.com/s/player/a1b2c3d4/player_ias.vflset/de_DE/base.js"))
        assertFalse(AdBlocker.shouldBlock("https://m.youtube.com/s/player/a1b2c3d4/player-plasma-ias-phone-de_DE.vflset/base.js"))
        assertFalse(AdBlocker.shouldBlock("https://play.google.com/log?format=json&hasfast=true"))
        assertFalse(AdBlocker.shouldBlock("https://m.youtube.com/youtubei/v1/player?key=x"))
        assertFalse(AdBlocker.shouldBlock("https://www.youtube.com/shorts"))
        assertFalse(AdBlocker.shouldBlock("https://i.ytimg.com/vi/abc/oar2.jpg"))
    }

    @Test
    fun `removes WebView markers from the user agent`() {
        val webView = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/AP1A; wv) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/130.0.6723.58 Mobile Safari/537.36"
        assertEquals(
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/AP1A) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/130.0.6723.58 Mobile Safari/537.36",
            chromeUserAgent(webView),
        )
    }
}
