package org.openshorts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openshorts.data.UploadValidation
import org.openshorts.data.normalizeTag

class UploadValidationTest {

    @Test
    fun `normalizes tags`() {
        assertEquals("cybersecurity", normalizeTag(" #CyberSecurity "))
        assertNull(normalizeTag("#a"))
        assertNull(normalizeTag("two words"))
        assertNull(normalizeTag("x".repeat(31)))
    }

    @Test
    fun `validates PeerTube limits`() {
        assertTrue(UploadValidation.check("My short", listOf("tech"), hasVideo = true, hasChannel = true).isValid)
        assertFalse(UploadValidation.check("ab", emptyList(), hasVideo = true, hasChannel = true).isValid)
        assertFalse(UploadValidation.check("My short", List(6) { "tag$it" }, hasVideo = true, hasChannel = true).isValid)
        assertFalse(UploadValidation.check("My short", emptyList(), hasVideo = false, hasChannel = true).isValid)
    }
}
