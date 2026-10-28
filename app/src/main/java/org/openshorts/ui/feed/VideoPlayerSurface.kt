package org.openshorts.ui.feed

import android.graphics.Color
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Renders [player] without any controls; the feed draws its own minimal overlay.
 * [fill] crops the video to the full screen (for vertical clips), otherwise it is letterboxed.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerSurface(player: Player, fill: Boolean, modifier: Modifier = Modifier) {
    val resize = if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                resizeMode = resize
                setShutterBackgroundColor(Color.TRANSPARENT)
                setKeepContentOnPlayerReset(false)
                this.player = player
            }
        },
        update = { view ->
            view.player = player
            view.resizeMode = resize
        },
        onRelease = { view -> view.player = null },
    )
}
