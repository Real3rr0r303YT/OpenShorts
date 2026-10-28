package org.openshorts

import org.junit.Assert.assertEquals
import org.junit.Test
import org.openshorts.data.SearchQuery
import org.openshorts.data.TrendCategory
import org.openshorts.data.parseSearchQuery
import org.openshorts.data.peertube.Video
import org.openshorts.data.rankShorts

class FeedRankingTest {

    private fun video(id: String, seconds: Int, aspect: Float? = null, live: Boolean = false) = Video(
        uuid = id, title = id, channelName = "c", accountHandle = "@c", thumbnailUrl = null,
        durationSeconds = seconds, views = 0, likes = 0, isLive = live, watchUrl = "https://x/w/$id", aspectRatio = aspect,
    )

    @Test
    fun `keeps clips under 60 seconds, vertical first, server order otherwise`() {
        val ranked = rankShorts(
            listOf(video("a", 30, 1.78f), video("b", 400), video("c", 45, 0.5625f), video("d", 20), video("e", 10, live = true)),
            shortsOnly = true,
        )
        assertEquals(listOf("c", "a", "d"), ranked.map { it.uuid })
    }

    @Test
    fun `falls back to clips up to 3 minutes when there are no shorts`() {
        val ranked = rankShorts(listOf(video("a", 150), video("b", 600)), shortsOnly = true)
        assertEquals(listOf("a"), ranked.map { it.uuid })
    }

    @Test
    fun `without shorts filter only reorders`() {
        assertEquals(listOf("b", "a"), rankShorts(listOf(video("a", 600), video("b", 900, 0.5f)), shortsOnly = false).map { it.uuid })
    }

    @Test
    fun `plain category words become category searches`() {
        assertEquals(SearchQuery.Category(TrendCategory.EDITS), parseSearchQuery("Edits"))
        assertEquals(SearchQuery.Category(TrendCategory.GAMING), parseSearchQuery(" gaming "))
        assertEquals(SearchQuery.Tag("gaming"), parseSearchQuery("#gaming"))
    }
}
