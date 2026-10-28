package org.openshorts.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import org.openshorts.R
import org.openshorts.data.SearchQuery
import org.openshorts.data.peertube.Video
import kotlin.math.absoluteValue

/** Full-screen vertical video feed: swipe to switch videos, tap to pause, double-tap to like. */
@Composable
fun FeedScreen(
    onOpenSettings: () -> Unit,
    onMessageCreator: (Video) -> Unit,
    viewModel: FeedViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val likedIds by viewModel.likedIds.collectAsStateWithLifecycle()
    val savedIds by viewModel.savedIds.collectAsStateWithLifecycle()
    val followed by viewModel.followedHandles.collectAsStateWithLifecycle()
    var commentsFor by remember { mutableStateOf<Video?>(null) }
    var creatorFor by remember { mutableStateOf<Video?>(null) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }

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
                    text = stringResource(
                        if (state.content is FeedContent.Tab) R.string.feed_empty else R.string.search_no_results,
                    ),
                    actionLabel = stringResource(
                        if (state.content is FeedContent.Tab) R.string.change_instance else R.string.search_clear,
                    ),
                    onAction = if (state.content is FeedContent.Tab) onOpenSettings else viewModel::clearSearch,
                )

            state.videos.isEmpty() ->
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                )

            else -> key(state.generation) {
                VideoPager(
                    state = state,
                    likedIds = likedIds,
                    savedIds = savedIds,
                    followed = followed,
                    viewModel = viewModel,
                    onOpenComments = { commentsFor = it },
                    onOpenCreator = { creatorFor = it },
                )
            }
        }

        FeedHeader(
            content = state.content,
            onSelectTab = { tab ->
                if (state.content == FeedContent.Tab(tab)) viewModel.refresh() else viewModel.selectTab(tab)
            },
            onOpenSearch = { searchOpen = true },
            onClearSearch = viewModel::clearSearch,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding(),
        )

        AnimatedVisibility(visible = searchOpen, enter = fadeIn(), exit = fadeOut()) {
            SearchOverlay(
                onSearch = { query ->
                    searchOpen = false
                    viewModel.search(query)
                },
                onClose = { searchOpen = false },
            )
        }
    }

    commentsFor?.let { video ->
        CommentsSheet(
            video = video,
            instanceUrl = video.instanceUrl.ifEmpty { viewModel.instanceUrl.orEmpty() },
            onDismiss = { commentsFor = null },
        )
    }
    creatorFor?.let { video ->
        CreatorSheet(
            video = video,
            isFollowing = video.toCreator()?.handle in followed,
            onToggleFollow = { viewModel.toggleFollow(video) },
            onShowVideos = {
                creatorFor = null
                video.toCreator()?.let { viewModel.search(SearchQuery.Channel(it.handle)) }
            },
            onMessage = {
                creatorFor = null
                onMessageCreator(video)
            },
            onDismiss = { creatorFor = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoPager(
    state: FeedUiState,
    likedIds: Set<String>,
    savedIds: Set<String>,
    followed: Set<String>,
    viewModel: FeedViewModel,
    onOpenComments: (Video) -> Unit,
    onOpenCreator: (Video) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = state.startPage) { state.videos.size }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { viewModel.onPageSelected(it) }
    }

    PullToRefreshBox(
        isRefreshing = false,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { index -> state.videos[index].uuid },
            // Soft, non-bouncy snap like in other short-video apps.
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapAnimationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
        ) { index ->
            val video = state.videos[index]
            val isActive = video.uuid == state.playingUuid
            VideoPage(
                video = video,
                player = viewModel.player,
                isActive = isActive,
                isLiked = video.uuid in likedIds,
                isSaved = video.uuid in savedIds,
                isFollowing = video.toCreator()?.handle in followed,
                showPlayIcon = isActive && state.userPaused,
                showBuffering = isActive && state.isBuffering && !state.userPaused,
                playbackFailed = isActive && state.playbackFailed,
                onTogglePlay = viewModel::togglePlayPause,
                onToggleLike = { viewModel.toggleLike(video) },
                onDoubleTapLike = { viewModel.like(video) },
                onToggleSaved = { viewModel.toggleSaved(video) },
                onFollow = { viewModel.toggleFollow(video) },
                onOpenComments = { onOpenComments(video) },
                onOpenCreator = { onOpenCreator(video) },
                modifier = Modifier.graphicsLayer {
                    // The leaving page shrinks and fades slightly while swiping.
                    val offset = ((pagerState.currentPage - index) + pagerState.currentPageOffsetFraction)
                        .absoluteValue.coerceIn(0f, 1f)
                    val scale = lerp(1f, 0.88f, offset)
                    scaleX = scale
                    scaleY = scale
                    alpha = lerp(1f, 0.4f, offset)
                },
            )
        }
    }
}

/** Tabs (Tech, Trending, …) plus search button, or the active search with a close button. */
@Composable
private fun FeedHeader(
    content: FeedContent,
    onSelectTab: (FeedTab) -> Unit,
    onOpenSearch: () -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        if (content is FeedContent.Tab) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                FeedTab.entries.forEach { tab ->
                    TabLabel(
                        text = stringResource(tab.labelRes),
                        selected = tab == content.tab,
                        onClick = { onSelectTab(tab) },
                    )
                }
            }
            IconButton(onClick = onOpenSearch, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search), tint = Color.White)
            }
        } else {
            val title = when (content) {
                is FeedContent.Search -> content.query.text
                is FeedContent.Collection -> content.title
                is FeedContent.Tab -> ""
            }
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickableNoRipple(onOpenSearch)
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                IconButton(onClick = onClearSearch) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_clear), tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun TabLabel(text: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickableNoRipple(onClick)
            .padding(horizontal = 2.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .width(20.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) Color.White else Color.Transparent),
        )
    }
}

private val FeedTab.labelRes: Int
    get() = when (this) {
        FeedTab.TECH -> R.string.tab_tech
        FeedTab.TRENDING -> R.string.tab_trending
        FeedTab.LATEST -> R.string.tab_latest
        FeedTab.LOCAL -> R.string.tab_local
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
