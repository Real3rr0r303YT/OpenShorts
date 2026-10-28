package org.openshorts

import org.junit.Assert.assertEquals
import org.junit.Test
import org.openshorts.ui.components.formatCount
import org.openshorts.ui.components.formatDuration
import java.util.Locale

class FormatTest {

    @Test
    fun `formats counts compactly`() {
        assertEquals("950", formatCount(950, Locale.US))
        assertEquals("1.2K", formatCount(1_234, Locale.US))
        assertEquals("1K", formatCount(1_000, Locale.US))
        assertEquals("45K", formatCount(45_600, Locale.US))
        assertEquals("3.4M", formatCount(3_400_000, Locale.US))
    }

    @Test
    fun `formats durations`() {
        assertEquals("0:59", formatDuration(59))
        assertEquals("1:15", formatDuration(75))
        assertEquals("1:02:05", formatDuration(3_725))
    }
}
