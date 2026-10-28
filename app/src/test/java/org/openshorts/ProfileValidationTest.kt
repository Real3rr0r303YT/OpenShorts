package org.openshorts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.openshorts.data.normalizeSupportUrl
import org.openshorts.data.peertube.htmlToPlainText
import org.openshorts.data.sanitizeUsername

class ProfileValidationTest {

    @Test
    fun `sanitizes usernames`() {
        assertEquals("hacker_42.dev", sanitizeUsername("@Hacker_42.Dev!"))
        assertEquals("ab", sanitizeUsername(" a b "))
    }

    @Test
    fun `validates support links`() {
        assertEquals("https://buymeacoffee.com/3rr0r303yt", normalizeSupportUrl("buymeacoffee.com/3rr0r303yt"))
        assertEquals("", normalizeSupportUrl("  "))
        assertNull(normalizeSupportUrl("http://example.com"))
        assertNull(normalizeSupportUrl("not a link"))
    }

    @Test
    fun `converts comment html to plain text`() {
        assertEquals("Hello\nworld & <you>", htmlToPlainText("<p>Hello</p><p>world &amp; &lt;you&gt;</p>"))
        assertEquals("a\nb", htmlToPlainText("a<br>b"))
    }
}
