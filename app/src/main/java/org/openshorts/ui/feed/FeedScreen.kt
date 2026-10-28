package org.openshorts.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.pager.PagerSnapDistance
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import org.openshorts.R
import org.openshorts.data.TrendCategory
import org.openshorts.data.peertube.Video

/** Full-screen vertical video feed: swipe to switch videos, tap to pause, double-tap to like. */
@Composable
fun FeedScreen(
    onOpenSettings: () -> Unit,
    onMessageCreator: (Video) -> Unit,
    /** Height of the (transparent) navigation bar drawn over the video. */
    bottomInset: Dp = 0.dp,
    viewModel: FeedViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val likedIds by viewModel.likedIds.collectAsStateWithLifecycle()
    val savedIds by viewModel.savedIds.collectAsStateWithLifecycle()
    val followed by viewModel.followedHandles.collectAsStateWithLifecycle()
    val loggedIn by viewModel.loggedIn.collectAsStateWithLifecycle()
    val youTubeEnabled by viewModel.youTubeEnabled.collectAsStateWithLifecycle()
    val tikTokEnabled by viewModel.tikTokEnabled.collectAsStateWithLifecycle()
    var pasteOpen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.notices.collect { snackbarHost.showSnackbar(context.getString(it)) }
    }
    val syncErrorText = stringResource(R.string.sync_error)
    LaunchedEffect(viewModel) {
        viewModel.syncErrors.collect { detail ->
            snackbarHost.showSnackbar(if (detail.isNullOrBlank()) syncErrorText else "$syncErrorText ($detail)")
        }
    }
    var commentsFor by remember { mutableStateOf<Video?>(null) }
    var creatorFor by remember { mutableStateOf<Video?>(null) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    // "Clear display": long-press hides every overlay for a pure full-screen video.
    var cleanMode by rememberSaveable { mutableStateOf(false) }

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
            state.videos.isEmpty() && state.youTubeUnavailable ->
                FeedMessage(
                    text = stringResource(R.string.youtube_unavailable),
                    actionLabel = stringResource(R.string.retry),
                    onAction = viewModel::retry,
                    secondaryLabel = stringResource(R.string.switch_to_peertube),
                    onSecondary = { viewModel.selectPlatform(FeedPlatform.PEERTUBE) },
                    tertiaryLabel = stringResource(R.string.youtube_load_direct),
                    onTertiary = viewModel::enableYouTubeDirect,
                )

            state.videos.isEmpty() && state.content == FeedContent.TikTok && state.endReached ->
                FeedMessage(
                    text = stringResource(R.string.tiktok_empty),
                    actionLabel = stringResource(R.string.tiktok_paste),
                    onAction = { pasteOpen = true },
                )

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
                    bottomInset = bottomInset,
                    cleanMode = cleanMode,
                    onToggleCleanMode = { cleanMode = !cleanMode },
                    likedIds = likedIds,
                    savedIds = savedIds,
                    followed = followed,
                    viewModel = viewModel,
                    onOpenComments = { commentsFor = it },
                    onOpenCreator = { creatorFor = it },
                )
            }
        }

        AnimatedVisibility(
            visible = !cleanMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            FeedHeader(
                content = state.content,
                loggedIn = loggedIn,
                youTubeEnabled = youTubeEnabled,
                tikTokEnabled = tikTokEnabled,
                onPasteTikTok = { pasteOpen = true },
                onSelectPlatform = { platform ->
                    if (platform == viewModel.platform) viewModel.refresh() else viewModel.selectPlatform(platform)
                },
                onSelectTab = { tab ->
                    if (state.content == FeedContent.Tab(tab)) viewModel.refresh() else viewModel.selectTab(tab)
                },
                onSelectYouTubeCategory = { category ->
                    if (state.content == FeedContent.YouTube(category = category)) {
                        viewModel.refresh()
                    } else {
                        viewModel.selectYouTubeCategory(category)
                    }
                },
                onOpenSearch = { searchOpen = true },
                onClearSearch = viewModel::clearSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
            )
        }

        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomInset + 16.dp),
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

    if (pasteOpen) {
        TikTokPasteDialog(
            onAdd = { link ->
                pasteOpen = false
                viewModel.addTikTokLink(link)
            },
            onDismiss = { pasteOpen = false },
        )
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
                video.toCreator()?.let(viewModel::openCreator)
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
    bottomInset: Dp,
    cleanMode: Boolean,
    onToggleCleanMode: () -> Unit,
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
    // A video could not be played: move on to the next one instead of showing an error.
    LaunchedEffect(pagerState) {
        viewModel.skipFrom.collect { index ->
            if (index == pagerState.currentPage && index + 1 < pagerState.pageCount) {
                pagerState.animateScrollToPage(index + 1)
            }
        }
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
            // TikTok-style snap: exactly one video per swipe, fast and without bounce;
            // a short flick (20 % of the screen) is enough.
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                pagerSnapDistance = PagerSnapDistance.atMost(1),
                snapAnimationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
                snapPositionalThreshold = 0.2f,
            ),
        ) { index ->
            val video = state.videos[index]
            val isActive = video.uuid == state.playingUuid
            VideoPage(
                video = video,
                player = viewModel.playerFor(video.uuid, state),
                progressPlayer = viewModel.currentPlayer(state),
                isActive = isActive,
                bottomInset = bottomInset,
                cleanMode = cleanMode,
                onLongPress = onToggleCleanMode,
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
                onEmbedError = { viewModel.onVideoFailed(video.uuid) },
                onEmbedReady = viewModel::onVideoReady,
            )
        }
    }
}

/**
 * Feed header: the platform switch (PeerTube | YouTube Shorts), the tabs of that platform and the
 * search button – or, while searching, the active search with a close button.
 */
@Composable
private fun FeedHeader(
    content: FeedContent,
    loggedIn: Boolean,
    youTubeEnabled: Boolean,
    tikTokEnabled: Boolean,
    onPasteTikTok: () -> Unit,
    onSelectPlatform: (FeedPlatform) -> Unit,
    onSelectTab: (FeedTab) -> Unit,
    onSelectYouTubeCategory: (TrendCategory?) -> Unit,
    onOpenSearch: () -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val platform = content.platform
    val browsing = content is FeedContent.Tab || content is FeedContent.TikTok ||
        (content is FeedContent.YouTube && content.query == null)
    val platforms = FeedPlatform.entries.filter {
        when (it) {
            FeedPlatform.PEERTUBE -> true
            FeedPlatform.YOUTUBE -> youTubeEnabled
            FeedPlatform.TIKTOK -> tikTokEnabled
        }
    }
    Column(modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (platforms.size > 1 && browsing) {
            PlatformSwitch(platforms = platforms, selected = platform, onSelect = onSelectPlatform)
        }
        Box(Modifier.fillMaxWidth()) {
            if (browsing) {
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(start = 8.dp, end = 44.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (content is FeedContent.YouTube) {
                        TabLabel(
                            text = stringResource(R.string.tab_for_you),
                            selected = content.category == null,
                            onClick = { onSelectYouTubeCategory(null) },
                        )
                        TrendCategory.entries.filter { it != TrendCategory.SHORTS }.forEach { category ->
                            TabLabel(
                                text = category.label,
                                selected = content.category == category,
                                onClick = { onSelectYouTubeCategory(category) },
                            )
                        }
                    } else if (content is FeedContent.TikTok) {
                        TabLabel(text = stringResource(R.string.tiktok_tab_shared), selected = true, onClick = {})
                        TabLabel(text = "+ " + stringResource(R.string.tiktok_paste), selected = false, onClick = onPasteTikTok)
                    } else if (content is FeedContent.Tab) {
                        FeedTab.entries.filter { loggedIn || !it.requiresLogin }.forEach { tab ->
                            TabLabel(
                                text = stringResource(tab.labelRes),
                                selected = tab == content.tab,
                                onClick = { onSelectTab(tab) },
                            )
                        }
                    }
                }
                IconButton(onClick = onOpenSearch, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search), tint = Color.White)
                }
            } else {
                val title = when (content) {
                    is FeedContent.Search -> content.query.text
                    is FeedContent.Collection -> content.title
                    is FeedContent.YouTube -> "▶ " + content.query.orEmpty()
                    is FeedContent.Tab, FeedContent.TikTok -> ""
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
}

/** Pill switch between the PeerTube network and YouTube Shorts. */
@Composable
private fun PlatformSwitch(platforms: List<FeedPlatform>, selected: FeedPlatform, onSelect: (FeedPlatform) -> Unit) {
    Row(
        modifier = Modifier
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(3.dp),
    ) {
        platforms.forEach { platform ->
            val isSelected = platform == selected
            Text(
                text = stringResource(
                    when (platform) {
                        FeedPlatform.PEERTUBE -> R.string.platform_peertube
                        FeedPlatform.YOUTUBE -> R.string.platform_youtube
                        FeedPlatform.TIKTOK -> R.string.platform_tiktok
                    },
                ),
                color = if (isSelected) Color.Black else Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) Color.White else Color.Transparent)
                    .clickableNoRipple { onSelect(platform) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
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
        FeedTab.FOR_YOU -> R.string.tab_for_you
        FeedTab.TECH -> R.string.tab_tech
        FeedTab.FOLLOWING -> R.string.tab_following
        FeedTab.TRENDING -> R.string.tab_trending
        FeedTab.LATEST -> R.string.tab_latest
        FeedTab.LOCAL -> R.string.tab_local
    }

@Composable
private fun FeedMessage(
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
    tertiaryLabel: String? = null,
    onTertiary: () -> Unit = {},
) {
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
        if (secondaryLabel != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onSecondary) { Text(secondaryLabel, color = Color.White) }
        }
        if (tertiaryLabel != null) {
            TextButton(onClick = onTertiary) { Text(tertiaryLabel, color = Color.White.copy(alpha = 0.85f)) }
        }
    }
}

/** Paste a TikTok link (e.g. copied from the TikTok app) to add it to the TikTok feed. */
@Composable
private fun TikTokPasteDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var link by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tiktok_paste)) },
        text = {
            Column {
                Text(stringResource(R.string.tiktok_paste_hint), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    placeholder = { Text("https://www.tiktok.com/@…/video/…") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(link) }, enabled = link.contains("tiktok.com", ignoreCase = true)) {
                Text(stringResource(R.string.tiktok_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
