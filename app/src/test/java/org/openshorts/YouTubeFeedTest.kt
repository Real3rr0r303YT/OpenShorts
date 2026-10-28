package org.openshorts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openshorts.data.TrendCategory
import org.openshorts.data.peertube.PLATFORM_YOUTUBE
import org.openshorts.data.peertube.Video
import org.openshorts.ui.feed.FeedContent
import org.openshorts.ui.feed.isYouTube
import org.openshorts.ui.feed.youTubeQueries

class YouTubeFeedTest {

    @Test
    fun `builds shorts queries`() {
        assertEquals(listOf("kali linux #shorts"), youTubeQueries(FeedContent.YouTube(query = "kali linux")))
        assertEquals(listOf("Gaming #shorts"), youTubeQueries(FeedContent.YouTube(category = TrendCategory.GAMING)))
        val mix = youTubeQueries(FeedContent.YouTube())
        assertEquals("#shorts", mix.first())
        assertTrue("Edits #shorts" in mix && "Cybersecurity #shorts" in mix)
    }

    @Test
    fun `detects YouTube content and ids`() {
        val video = Video(
            uuid = "yt:abc123", title = "t", channelName = "c", accountHandle = "@c", thumbnailUrl = null,
            durationSeconds = 30, views = 0, likes = 0, watchUrl = "https://www.youtube.com/shorts/abc123",
            platform = PLATFORM_YOUTUBE,
        )
        assertEquals("abc123", video.youTubeId)
        assertTrue(FeedContent.YouTube().isYouTube)
        assertTrue(FeedContent.Collection("Liked", listOf(video), 0).isYouTube)
    }
}
