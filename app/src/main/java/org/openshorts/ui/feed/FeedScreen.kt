package org.openshorts.ui.feed

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import kotlinx.coroutines.flow.distinctUntilChanged
import org.openshorts.R
import org.openshorts.data.peertube.Video
import org.openshorts.ui.components.RemoteImage
import org.openshorts.ui.components.formatCount
import org.openshorts.ui.components.formatDuration

/** Full-screen vertical video feed: swipe up/down to switch videos, tap to pause. */
@Composable
fun FeedScreen(
    onOpenSettings: () -> Unit,
    viewModel: FeedViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Pause when the app goes to the background or the feed leaves the screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.pausePlayback()
                Lifecycle.Event.ON_RESUME -> viewModel.resumePlayback()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.pausePlayback()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when {
            state.videos.isEmpty() && state.error != null ->
                FeedMessage(
                    text = stringResource(R.string.feed_error, state.error.orEmpty()),
                    actionLabel = stringResource(R.string.retry),
                    onAction = viewModel::retry,
                )

            state.videos.isEmpty() && state.endReached ->
                FeedMessage(
                    text = stringResource(R.string.feed_empty),
                    actionLabel = stringResource(R.string.settings),
                    onAction = onOpenSettings,
                )

            state.videos.isEmpty() ->
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                )

            else -> key(state.generation) {
                VideoPager(state = state, viewModel = viewModel)
            }
        }

        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.settings),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun VideoPager(state: FeedUiState, viewModel: FeedViewModel) {
    val pagerState = rememberPagerState { state.videos.size }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { viewModel.onPageSelected(it) }
    }

    VerticalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        key = { index -> state.videos[index].uuid },
    ) { index ->
        val video = state.videos[index]
        val isActive = video.uuid == state.playingUuid
        VideoPage(
            video = video,
            player = viewModel.player,
            isActive = isActive,
            showPlayIcon = isActive && state.userPaused,
            showBuffering = isActive && state.isBuffering && !state.userPaused,
            playbackFailed = isActive && state.playbackFailed,
            onTogglePlay = viewModel::togglePlayPause,
        )
    }
}

@Composable
private fun VideoPage(
    video: Video,
    player: Player,
    isActive: Boolean,
    showPlayIcon: Boolean,
    showBuffering: Boolean,
    playbackFailed: Boolean,
    onTogglePlay: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val shareTitle = stringResource(R.string.share)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTogglePlay,
            ),
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
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.align(Alignment.Center).size(96.dp),
            )

            showBuffering -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
        }

        // Bottom overlay: metadata on the left, actions on the right.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 8.dp, top = 48.dp, bottom = 16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.channelName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (video.accountHandle.isNotEmpty()) {
                    Text(
                        text = video.accountHandle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.video_stats,
                        formatCount(video.views),
                        formatCount(video.likes),
                        formatDuration(video.durationSeconds),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, video.title)
                        .putExtra(Intent.EXTRA_TEXT, video.watchUrl)
                    context.startActivity(Intent.createChooser(send, shareTitle))
                }) {
                    Icon(Icons.Filled.Share, contentDescription = shareTitle, tint = Color.White)
                }
                IconButton(onClick = { uriHandler.openUri(video.watchUrl) }) {
                    Icon(
                        Icons.Filled.OpenInBrowser,
                        contentDescription = stringResource(R.string.open_in_browser),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedMessage(text: String, actionLabel: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = text, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAction) { Text(actionLabel) }
    }
}
