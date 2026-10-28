package org.openshorts.ui.feed

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.openshorts.R
import org.openshorts.data.peertube.Video
import org.openshorts.ui.components.Avatar
import org.openshorts.ui.components.RemoteImage
import org.openshorts.ui.components.formatCount
import org.openshorts.ui.components.formatDuration
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** TikTok-ish accent colors, independent of the selected color scheme. */
private val LikeRed = Color(0xFFFE2C55)
private val SaveAmber = Color(0xFFFFC107)
private val GlowCyan = Color(0xFF25F4EE)

/** Readable white text on any video. */
private val TextShadow = Shadow(color = Color.Black.copy(alpha = 0.6f), offset = Offset(0f, 2f), blurRadius = 6f)

@Composable
internal fun VideoPage(
    video: Video,
    /** Player holding this video (playing or pre-buffered), null if none. */
    player: Player?,
    /** Player of the video on screen, for the progress bar. */
    progressPlayer: Player,
    isActive: Boolean,
    bottomInset: Dp,
    cleanMode: Boolean,
    onLongPress: () -> Unit,
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
    val haptics = LocalHapticFeedback.current
    val shareTitle = stringResource(R.string.share)
    val hearts = remember { mutableStateListOf<HeartBurst>() }
    val currentOnTogglePlay by rememberUpdatedState(onTogglePlay)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTapLike)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    // Vertical clips fill the whole screen (cropped) like on TikTok; landscape videos are letterboxed.
    val fill = video.isVertical

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(video.uuid) {
                detectTapGestures(
                    onTap = { currentOnTogglePlay() },
                    onDoubleTap = { position ->
                        hearts += HeartBurst(id = System.nanoTime(), position = position)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOnDoubleTap()
                    },
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOnLongPress()
                    },
                )
            },
    ) {
        RemoteImage(
            url = video.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = if (fill) ContentScale.Crop else ContentScale.Fit,
        )

        if (player != null) {
            VideoPlayerSurface(player = player, fill = fill, modifier = Modifier.fillMaxSize())
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
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.align(Alignment.Center).size(88.dp),
            )

            showBuffering -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(36.dp),
                color = Color.White.copy(alpha = 0.8f),
                strokeWidth = 3.dp,
            )
        }

        AnimatedVisibility(
            visible = !cleanMode,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                // Soft bottom gradient so white text stays readable on bright videos.
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(320.dp + bottomInset)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))),
                )

                // Bottom-left: creator, title, stats.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 84.dp, bottom = bottomInset + 14.dp),
                ) {
                    Text(
                        text = video.accountHandle.ifBlank { video.channelName },
                        style = MaterialTheme.typography.titleMedium.copy(shadow = TextShadow),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickableNoRipple(onOpenCreator),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.bodyMedium.copy(shadow = TextShadow),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.video_stats, formatCount(video.views), formatDuration(video.durationSeconds)),
                        style = MaterialTheme.typography.labelMedium.copy(shadow = TextShadow),
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }

                // Right: floating action rail.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 6.dp, bottom = bottomInset + 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CreatorAvatar(video = video, isFollowing = isFollowing, onClick = onOpenCreator, onFollow = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onFollow()
                    })
                    LikeButton(
                        liked = isLiked,
                        count = video.likes + if (isLiked) 1 else 0,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleLike()
                        },
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
                        active = isSaved,
                        activeColor = SaveAmber,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleSaved()
                        },
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
            }
        }

        if (isActive) {
            PlaybackProgress(
                player = progressPlayer,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomInset)
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

/** Scales a control down while pressed and springs back – the "snappy" touch feel. */
@Composable
private fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.82f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Soft radial glow behind a rail icon. */
@Composable
private fun Glow(color: Color, intensity: Float, size: Dp = 56.dp) {
    Box(
        Modifier
            .size(size)
            .background(
                Brush.radialGradient(
                    listOf(color.copy(alpha = 0.55f * intensity), color.copy(alpha = 0.18f * intensity), Color.Transparent),
                ),
                CircleShape,
            ),
    )
}

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
    Box(
        modifier = Modifier
            .offset { IntOffset((heart.position.x - half).roundToInt(), (heart.position.y - half - p * 160f).roundToInt()) }
            .size(size),
        contentAlignment = Alignment.Center,
    ) {
        Glow(LikeRed, intensity = (1f - p).coerceAtLeast(0f), size = size)
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            tint = LikeRed,
            modifier = Modifier
                .size(size * 0.8f)
                .rotate(rotation)
                .graphicsLayer {
                    val pop = if (p < 0.2f) 0.6f + p * 3f else 1.2f + (p - 0.2f) * 0.5f
                    scaleX = pop
                    scaleY = pop
                    alpha = if (p < 0.6f) 1f else 1f - (p - 0.6f) / 0.4f
                },
        )
    }
}

/** Creator avatar with an animated gradient ring and a follow badge. */
@Composable
private fun CreatorAvatar(video: Video, isFollowing: Boolean, onClick: () -> Unit, onFollow: () -> Unit) {
    val ring = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) ring.animateTo(ring.value + 360f, tween(durationMillis = 4000, easing = LinearEasing))
    }
    val interaction = remember { MutableInteractionSource() }
    Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.padding(bottom = 10.dp)) {
        Box(contentAlignment = Alignment.Center) {
            // Rotating TikTok-style cyan/red ring.
            Canvas(Modifier.size(56.dp).graphicsLayer { rotationZ = ring.value }) {
                drawCircle(
                    brush = Brush.sweepGradient(listOf(GlowCyan, LikeRed, Color.White, GlowCyan)),
                    style = Stroke(width = 2.5.dp.toPx()),
                )
            }
            Avatar(
                source = video.creatorAvatarUrl,
                name = video.channelName,
                size = 48.dp,
                modifier = Modifier
                    .pressScale(interaction)
                    .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            )
        }
        val followInteraction = remember { MutableInteractionSource() }
        val badgeColor by animateColorAsState(if (isFollowing) Color.White else LikeRed, label = "followBadge")
        Box(
            modifier = Modifier
                .offset(y = 10.dp)
                .pressScale(followInteraction)
                .size(22.dp)
                .clip(CircleShape)
                .background(badgeColor)
                .clickable(interactionSource = followInteraction, indication = null, onClick = onFollow),
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
    val interaction = remember { MutableInteractionSource() }
    // Bounce + particle burst when the heart is switched on.
    val bounce = remember { Animatable(1f) }
    val burst = remember { Animatable(1f) }
    LaunchedEffect(liked) {
        if (liked) {
            burst.snapTo(0f)
            bounce.snapTo(0.55f)
            coroutineScope {
                launch { burst.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
                bounce.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
            }
        }
    }
    val tint by animateColorAsState(if (liked) LikeRed else Color.White, label = "likeTint")
    val glow by animateFloatAsState(if (liked) 1f else 0f, label = "likeGlow")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(52.dp)) {
            Glow(LikeRed, intensity = glow, size = 52.dp)
            if (burst.value < 1f) {
                // Six particles flying outwards.
                val b = burst.value
                Canvas(Modifier.size(52.dp)) {
                    val radius = size.minDimension / 2 * (0.4f + b * 0.6f)
                    repeat(6) { i ->
                        val angle = Math.toRadians(i * 60.0 - 90.0)
                        drawCircle(
                            color = if (i % 2 == 0) LikeRed else GlowCyan,
                            radius = 3.dp.toPx() * (1f - b),
                            center = center + Offset((cos(angle) * radius).toFloat(), (sin(angle) * radius).toFloat()),
                        )
                    }
                }
            }
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = stringResource(if (liked) R.string.unlike else R.string.like),
                tint = tint,
                modifier = Modifier
                    .size(36.dp)
                    .graphicsLayer {
                        scaleX = bounce.value
                        scaleY = bounce.value
                    },
            )
        }
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
    active: Boolean = false,
    activeColor: Color = Color.White,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tint by animateColorAsState(if (active) activeColor else Color.White, label = "railTint")
    val glow by animateFloatAsState(if (active || pressed) 1f else 0f, label = "railGlow")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
            Glow(if (active) activeColor else Color.White, intensity = glow * if (active) 1f else 0.5f, size = 48.dp)
            Icon(imageVector = icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(32.dp))
        }
        RailLabel(label)
    }
}

@Composable
private fun RailLabel(text: String) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.labelMedium.copy(shadow = TextShadow),
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
            .background(Brush.radialGradient(listOf(Color(0xFF333333), Color(0xFF111111))))
            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
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
            delay(50)
        }
    }
    Box(
        modifier
            .height(2.dp)
            .background(Color.White.copy(alpha = 0.2f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress)
                .height(2.dp)
                .background(Brush.horizontalGradient(listOf(GlowCyan, Color.White))),
        )
    }
}
