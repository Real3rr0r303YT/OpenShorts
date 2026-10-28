package org.openshorts.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import org.openshorts.ui.components.RemoteImage

/** Profile banner: the user's image, or a gradient in their chosen hue. */
@Composable
fun ProfileBanner(bannerPath: String?, hue: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                listOf(
                    Color.hsl(hue, 0.75f, 0.45f),
                    Color.hsl((hue + 40f) % 360f, 0.70f, 0.30f),
                ),
            ),
        ),
    ) {
        RemoteImage(
            url = bannerPath,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}
