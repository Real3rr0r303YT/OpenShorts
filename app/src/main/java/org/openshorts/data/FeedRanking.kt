package org.openshorts.data

import org.openshorts.data.peertube.Video

/** Clips up to this length count as "shorts" (TikTok-style). */
const val SHORT_CLIP_MAX_SECONDS = 60

/** Longest clip used as a fallback when a page contains no real shorts at all. */
const val FALLBACK_MAX_SECONDS = 180

/**
 * The short-video ranking used by the feed ("For You" style):
 * - live streams are dropped,
 * - with [shortsOnly], only clips up to [maxSeconds] are kept,
 * - vertical (portrait) clips come first, otherwise the server order is kept,
 * - if nothing matches, the shortest clips up to [FALLBACK_MAX_SECONDS] are used so the feed is never empty.
 */
fun rankShorts(
    videos: List<Video>,
    shortsOnly: Boolean,
    maxSeconds: Int = SHORT_CLIP_MAX_SECONDS,
): List<Video> {
    val playable = videos.filterNot { it.isLive }
    if (!shortsOnly) return playable.sortedByDescending { it.isVertical }
    val shorts = playable.filter { it.durationSeconds in 1..maxSeconds }
    val chosen = shorts.ifEmpty { playable.filter { it.durationSeconds in 1..FALLBACK_MAX_SECONDS } }
    return chosen.sortedByDescending { it.isVertical } // stable: keeps the server order otherwise
}
