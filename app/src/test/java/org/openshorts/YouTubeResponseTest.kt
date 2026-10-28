package org.openshorts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openshorts.data.youtube.looksLikeHtml

class YouTubeResponseTest {

    @Test
    fun `detects html error pages`() {
        assertTrue(looksLikeHtml("<!DOCTYPE html><html><body>502 Bad Gateway</body></html>", null))
        assertTrue(looksLikeHtml("  \n<html>", "application/json"))
        assertTrue(looksLikeHtml("{}", "text/html; charset=utf-8"))
    }

    @Test
    fun `accepts json`() {
        assertFalse(looksLikeHtml("""{"items":[]}""", "application/json"))
        assertFalse(looksLikeHtml("[]", null))
    }
}
