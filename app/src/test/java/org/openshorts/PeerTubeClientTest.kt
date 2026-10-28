package org.openshorts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.openshorts.data.peertube.Resolution
import org.openshorts.data.peertube.VideoFile
import org.openshorts.data.peertube.selectBestFile

class PeerTubeClientTest {

    private fun file(height: Int) = VideoFile(fileUrl = "https://x/$height.mp4", resolution = Resolution(height, "${height}p"))

    @Test
    fun `picks highest resolution up to 720p`() {
        val files = listOf(file(240), file(1080), file(720), file(480))
        assertEquals(720, selectBestFile(files)?.resolution?.id)
    }

    @Test
    fun `falls back to smallest file when all are too large`() {
        assertEquals(1080, selectBestFile(listOf(file(2160), file(1080)))?.resolution?.id)
    }

    @Test
    fun `ignores audio-only and url-less files`() {
        val files = listOf(file(0), VideoFile(fileUrl = null, resolution = Resolution(480)))
        assertNull(selectBestFile(files))
    }
}
