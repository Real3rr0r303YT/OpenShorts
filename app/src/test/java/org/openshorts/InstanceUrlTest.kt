package org.openshorts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.openshorts.data.normalizeInstanceUrl

class InstanceUrlTest {

    @Test
    fun `adds https scheme and strips trailing slash`() {
        assertEquals("https://framatube.org", normalizeInstanceUrl("framatube.org/"))
        assertEquals("https://tube.example.org", normalizeInstanceUrl("  https://Tube.Example.org  "))
    }

    @Test
    fun `keeps port and path`() {
        assertEquals("https://tube.example.org:8443/peertube", normalizeInstanceUrl("https://tube.example.org:8443/peertube/"))
    }

    @Test
    fun `rejects plain http and garbage`() {
        assertNull(normalizeInstanceUrl("http://framatube.org"))
        assertNull(normalizeInstanceUrl(""))
        assertNull(normalizeInstanceUrl("not a url"))
        assertNull(normalizeInstanceUrl("ftp://example.org"))
    }
}
