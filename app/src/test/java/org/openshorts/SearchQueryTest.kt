package org.openshorts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.openshorts.data.SearchQuery
import org.openshorts.data.parseSearchQuery

class SearchQueryTest {

    @Test
    fun `parses free text, tags and channels`() {
        assertEquals(SearchQuery.Text("kali linux"), parseSearchQuery("  kali linux "))
        assertEquals(SearchQuery.Tag("infosec"), parseSearchQuery("#InfoSec"))
        assertEquals(SearchQuery.Channel("alice@tube.example"), parseSearchQuery("@alice@tube.example"))
    }

    @Test
    fun `falls back to text for tags or handles with spaces`() {
        assertEquals(SearchQuery.Text("capture the flag"), parseSearchQuery("#capture the flag"))
    }

    @Test
    fun `ignores blank input`() {
        assertNull(parseSearchQuery("   "))
        assertNull(parseSearchQuery(""))
    }
}
