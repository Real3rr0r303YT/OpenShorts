package org.openshorts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.openshorts.web.browserTarget
import org.openshorts.web.displayUrl

class BrowserInputTest {

    @Test
    fun `turns address bar input into urls`() {
        assertEquals("https://www.tiktok.com/@hacker", browserTarget("https://www.tiktok.com/@hacker"))
        assertEquals("https://example.org/a", browserTarget("http://example.org/a"))
        assertEquals("https://tiktok.com/@hacker", browserTarget("tiktok.com/@hacker"))
        assertEquals("https://www.tiktok.com/@hacker", browserTarget("@hacker"))
        assertEquals("https://www.tiktok.com/search?q=kali+linux+tricks", browserTarget(" kali linux tricks "))
        assertEquals("https://www.tiktok.com/search?q=cybersecurity", browserTarget("cybersecurity"))
        assertNull(browserTarget("   "))
    }

    @Test
    fun `shortens urls for display`() {
        assertEquals("tiktok.com/@hacker", displayUrl("https://www.tiktok.com/@hacker/"))
        assertEquals("youtube.com/shorts/abc", displayUrl("https://m.youtube.com/shorts/abc"))
    }
}
