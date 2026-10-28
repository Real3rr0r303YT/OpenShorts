package org.openshorts.ui.components

import java.util.Locale

/** Compact, locale-aware counts: 950 → "950", 1_234 → "1.2K", 3_400_000 → "3.4M". */
fun formatCount(count: Long, locale: Locale = Locale.getDefault()): String {
    if (count < 1_000) return count.toString()
    val (value, suffix) = when {
        count < 1_000_000 -> count / 1_000.0 to "K"
        count < 1_000_000_000 -> count / 1_000_000.0 to "M"
        else -> count / 1_000_000_000.0 to "B"
    }
    val formatted = if (value < 10) String.format(locale, "%.1f", value) else value.toLong().toString()
    return formatted.removeSuffix(".0").removeSuffix(",0") + suffix
}

/** 75 → "1:15", 3_725 → "1:02:05". */
fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, m, s) else "%d:%02d".format(Locale.ROOT, m, s)
}
