package org.openshorts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.openshorts.data.tiktok.findTikTokUrl
import org.openshorts.data.tiktok.videoIdFrom
import org.openshorts.data.youtube.extractJsonObject
import org.openshorts.data.youtube.parseLength

class WebSourcesTest {

    @Test
    fun `extracts the initial data object from a page`() {
        val html = """<script>var ytInitialData = {"a":{"b":"x}{\"y"},"c":[1,2]};</script><script>other()</script>"""
        assertEquals("""{"a":{"b":"x}{\"y"},"c":[1,2]}""", extractJsonObject(html, "ytInitialData"))
        assertNull(extractJsonObject(html, "ytInitialPlayerResponse"))
    }

    @Test
    fun `parses video lengths`() {
        assertEquals(59, parseLength("0:59"))
        assertEquals(83, parseLength("1:23"))
        assertEquals(3723, parseLength("1:02:03"))
    }

    @Test
    fun `finds TikTok links and ids in shared text`() {
        val shared = "Schau mal! https://www.tiktok.com/@hacker/video/7312345678901234567?is_from_webapp=1 #cyber"
        val url = findTikTokUrl(shared)
        assertEquals("https://www.tiktok.com/@hacker/video/7312345678901234567?is_from_webapp=1", url)
        assertEquals("7312345678901234567", videoIdFrom(url!!))
        assertEquals("https://vm.tiktok.com/ZMabc123/", findTikTokUrl("https://vm.tiktok.com/ZMabc123/"))
        assertNull(videoIdFrom("https://vm.tiktok.com/ZMabc123/"))
        assertNull(findTikTokUrl("https://example.org/video/123"))
    }
}
