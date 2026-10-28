package org.openshorts.ui.feed

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import org.openshorts.R
import org.openshorts.data.peertube.Video
import org.openshorts.ui.components.Avatar
import org.openshorts.ui.components.RemoteImage
import org.openshorts.ui.components.formatCount
import org.openshorts.ui.components.formatDuration
import kotlin.math.roundToInt

/** Red used for the like heart, independent of the selected color scheme. */
private val LikeRed = Color(0xFFFE2C55)

@Composable
internal fun VideoPage(
    video: Video,
    player: Player,
    isActive: Boolean,
    isLiked: Boolean,
    isSaved: Boolean,
    isFollowing: Boolean,
    showPlayIcon: Boolean,
    showBuffering: Boolean,
    playbackFailed: Boolean,
    onTogglePlay: () -> Unit,
    onToggleLike: () -> Unit,
    onDoubleTapLike: () -> Unit,
    onToggleSaved: () -> Unit,
    onFollow: () -> Unit,
    onOpenComments: () -> Unit,
    onOpenCreator: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.share)
    val hearts = remember { mutableStateListOf<HeartBurst>() }
    val currentOnTogglePlay by rememberUpdatedState(onTogglePlay)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTapLike)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(video.uuid) {
                detectTapGestures(
                    onTap = { currentOnTogglePlay() },
                    onDoubleTap = { position ->
                        hearts += HeartBurst(id = System.nanoTime(), position = position)
                        currentOnDoubleTap()
                    },
                )
            },
    ) {
        RemoteImage(
            url = video.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )

        if (isActive) {
            VideoPlayerSurface(player = player, modifier = Modifier.fillMaxSize())
        }

        when {
            playbackFailed -> Text(
                text = stringResource(R.string.playback_error),
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                textAlign = TextAlign.Center,
            )

            showPlayIcon -> Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.play),
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.Center).size(96.dp),
            )

            showBuffering -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
        }

        // Bottom gradient so white text stays readable on bright videos.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(260.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))),
        )

        // Bottom-left: creator, title, stats.
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 14.dp, end = 88.dp, bottom = 18.dp),
        ) {
            Text(
                text = video.accountHandle.ifBlank { video.channelName },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickableNoRipple(onOpenCreator),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.video_stats, formatCount(video.views), formatDuration(video.durationSeconds)),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f),
            )
        }

        // Right: floating action rail.
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CreatorAvatar(video = video, isFollowing = isFollowing, onClick = onOpenCreator, onFollow = onFollow)
            LikeButton(
                liked = isLiked,
                count = video.likes + if (isLiked) 1 else 0,
                onClick = onToggleLike,
            )
            RailButton(
                icon = Icons.Filled.ChatBubble,
                label = formatCount(video.comments),
                contentDescription = stringResource(R.string.comments),
                onClick = onOpenComments,
            )
            RailButton(
                icon = Icons.Filled.Bookmark,
                label = stringResource(if (isSaved) R.string.saved else R.string.save_video),
                contentDescription = stringResource(R.string.save_video),
                tint = if (isSaved) Color(0xFFFFC107) else Color.White,
                onClick = onToggleSaved,
            )
            RailButton(
                icon = Icons.Filled.Share,
                label = shareTitle,
                contentDescription = shareTitle,
                onClick = {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, video.title)
                        .putExtra(Intent.EXTRA_TEXT, video.watchUrl)
                    context.startActivity(Intent.createChooser(send, shareTitle))
                },
            )
            SpinningDisc(avatarUrl = video.creatorAvatarUrl, name = video.channelName, spinning = isActive && !showPlayIcon)
        }

        if (isActive) {
            PlaybackProgress(
                player = player,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            )
        }

        hearts.forEach { heart ->
            key(heart.id) {
                FlyingHeart(heart = heart, onFinished = { hearts.remove(heart) })
            }
        }
    }
}

private data class HeartBurst(val id: Long, val position: Offset)

/** Big heart that pops up where the user double-tapped and floats away. */
@Composable
private fun FlyingHeart(heart: HeartBurst, onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val size = 110.dp
    val half = with(LocalDensity.current) { (size / 2).toPx() }
    val rotation = remember { listOf(-18f, -8f, 8f, 18f).random() }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 750, easing = LinearEasing))
        onFinished()
    }
    val p = progress.value
    Icon(
        imageVector = Icons.Filled.Favorite,
        contentDescription = null,
        tint = LikeRed,
        modifier = Modifier
            .offset { IntOffset((heart.position.x - half).roundToInt(), (heart.position.y - half - p * 160f).roundToInt()) }
            .size(size)
            .rotate(rotation)
            .graphicsLayer {
                val pop = if (p < 0.2f) 0.6f + p * 3f else 1.2f + (p - 0.2f) * 0.5f
                scaleX = pop
                scaleY = pop
                alpha = if (p < 0.6f) 1f else 1f - (p - 0.6f) / 0.4f
            },
    )
}

@Composable
private fun CreatorAvatar(video: Video, isFollowing: Boolean, onClick: () -> Unit, onFollow: () -> Unit) {
    Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.padding(bottom = 10.dp)) {
        Avatar(
            source = video.creatorAvatarUrl,
            name = video.channelName,
            size = 50.dp,
            modifier = Modifier
                .border(2.dp, Color.White, CircleShape)
                .clickableNoRipple(onClick),
        )
        Box(
            modifier = Modifier
                .offset(y = 10.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (isFollowing) Color.White else LikeRed)
                .clickableNoRipple(onFollow),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isFollowing) Icons.Filled.Check else Icons.Filled.Add,
                contentDescription = stringResource(if (isFollowing) R.string.unfollow else R.string.follow),
                tint = if (isFollowing) LikeRed else Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun LikeButton(liked: Boolean, count: Long, onClick: () -> Unit) {
    // Bounce when the heart is switched on.
    val scale = remember { Animatable(1f) }
    LaunchedEffect(liked) {
        if (liked) {
            scale.snapTo(0.6f)
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickableNoRipple(onClick)) {
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = stringResource(if (liked) R.string.unlike else R.string.like),
            tint = if (liked) LikeRed else Color.White,
            modifier = Modifier
                .size(36.dp)
                .scale(scale.value),
        )
        // Live counter: digits roll up/down when the value changes.
        AnimatedContent(
            targetState = count,
            transitionSpec = {
                val up = targetState > initialState
                (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith
                    (slideOutVertically { if (up) -it else it } + fadeOut())
            },
            label = "likeCount",
        ) { value ->
            RailLabel(formatCount(value))
        }
    }
}

@Composable
private fun RailButton(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickableNoRipple(onClick)) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(32.dp))
        RailLabel(label)
    }
}

@Composable
private fun RailLabel(text: String) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}

/** Rotating creator "disc" at the bottom of the rail, spinning while the video plays. */
@Composable
private fun SpinningDisc(avatarUrl: String?, name: String, spinning: Boolean) {
    // Keeps its angle when paused and continues from there.
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(spinning) {
        while (spinning) {
            rotation.animateTo(rotation.value + 360f, tween(durationMillis = 6000, easing = LinearEasing))
        }
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .graphicsLayer { rotationZ = rotation.value }
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF333333), Color(0xFF111111)))),
        contentAlignment = Alignment.Center,
    ) {
        Avatar(source = avatarUrl, name = name, size = 26.dp)
    }
}

/** Thin progress bar at the bottom of the active video. */
@Composable
private fun PlaybackProgress(player: Player, modifier: Modifier = Modifier) {
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(player) {
        while (true) {
            val duration = player.duration
            progress = if (duration > 0) (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
            delay(100)
        }
    }
    val animated by animateFloatAsState(progress, label = "progress")
    Box(
        modifier
            .height(2.dp)
            .background(Color.White.copy(alpha = 0.25f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(2.dp)
                .background(Color.White),
        )
    }
}
