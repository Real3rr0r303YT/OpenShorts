package org.openshorts.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.openshorts.BuildConfig
import org.openshorts.R
import org.openshorts.data.peertube.Creator
import org.openshorts.data.peertube.Video
import org.openshorts.ui.components.Avatar
import org.openshorts.ui.components.RemoteImage
import org.openshorts.ui.components.formatCount

private enum class ProfileTab(val icon: ImageVector, val label: Int) {
    POSTED(Icons.Filled.VideoLibrary, R.string.profile_tab_posted),
    LIKED(Icons.Filled.Favorite, R.string.profile_tab_liked),
    SAVED(Icons.Filled.Bookmark, R.string.profile_tab_saved),
    FOLLOWING(Icons.Filled.People, R.string.profile_tab_following),
}

/** TikTok-style profile of the local OpenShorts account. */
@Composable
fun ProfileScreen(
    onEditProfile: () -> Unit,
    onPlayVideos: (title: String, videos: List<Video>, startIndex: Int) -> Unit,
    onOpenCreator: (Creator) -> Unit,
    viewModel: ProfileViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    var tab by rememberSaveable { mutableIntStateOf(ProfileTab.LIKED.ordinal) }
    val selectedTab = ProfileTab.entries[tab]
    val profile = state.profile
    val likedTitle = stringResource(R.string.profile_tab_liked)
    val savedTitle = stringResource(R.string.profile_tab_saved)

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(230.dp)) {
                    ProfileBanner(
                        bannerPath = profile.bannerPath,
                        hue = profile.bannerHue,
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                    )
                    Avatar(
                        source = profile.avatarPath,
                        name = profile.displayName.ifBlank { profile.username.ifBlank { "?" } },
                        size = 104.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .border(4.dp, MaterialTheme.colorScheme.background, CircleShape),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.displayName.ifBlank { stringResource(R.string.profile_anonymous) },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (profile.showSupporterBadge) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.Verified,
                            contentDescription = stringResource(R.string.supporter_badge),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (profile.username.isNotBlank()) {
                    Text(
                        "@${profile.username}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Stat(formatCount(state.following.size.toLong()), stringResource(R.string.stat_following))
                    // A local account cannot be followed by anyone (yet): no server, no followers.
                    Stat("0", stringResource(R.string.stat_followers))
                    Stat(formatCount(state.liked.size.toLong()), stringResource(R.string.stat_likes))
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (profile.isSetUp) {
                        OutlinedButton(onClick = onEditProfile) { Text(stringResource(R.string.edit_profile)) }
                    } else {
                        Button(onClick = onEditProfile) { Text(stringResource(R.string.setup_profile)) }
                    }
                    if (profile.supportUrl.isNotBlank()) {
                        FilledTonalButton(onClick = { uriHandler.openUri(profile.supportUrl) }) {
                            Icon(Icons.Filled.LocalCafe, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.profile_tip_me))
                        }
                    }
                }
                if (profile.bio.isNotBlank()) {
                    Text(
                        text = profile.bio,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
                    )
                } else {
                    Spacer(Modifier.height(12.dp))
                }
                SupportOpenShortsBanner(onClick = { uriHandler.openUri(BuildConfig.DONATION_URL) })
                Spacer(Modifier.height(8.dp))
                PrimaryTabRow(selectedTabIndex = tab) {
                    ProfileTab.entries.forEach { entry ->
                        Tab(
                            selected = tab == entry.ordinal,
                            onClick = { tab = entry.ordinal },
                            icon = { Icon(entry.icon, contentDescription = stringResource(entry.label)) },
                        )
                    }
                }
            }
        }

        when (selectedTab) {
            ProfileTab.POSTED -> item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyTab(stringResource(R.string.profile_posted_empty))
            }
            ProfileTab.LIKED, ProfileTab.SAVED -> {
                val videos = if (selectedTab == ProfileTab.LIKED) state.liked else state.saved
                val title = if (selectedTab == ProfileTab.LIKED) likedTitle else savedTitle
                if (videos.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyTab(
                            stringResource(
                                if (selectedTab == ProfileTab.LIKED) R.string.profile_liked_empty else R.string.profile_saved_empty,
                            ),
                        )
                    }
                } else {
                    itemsIndexed(videos, key = { _, video -> video.uuid }) { index, video ->
                        VideoTile(video = video, onClick = { onPlayVideos(title, videos, index) })
                    }
                }
            }
            ProfileTab.FOLLOWING -> {
                if (state.following.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { EmptyTab(stringResource(R.string.profile_following_empty)) }
                } else {
                    items(state.following, key = { it.handle }, span = { GridItemSpan(maxLineSpan) }) { creator ->
                        CreatorRow(
                            creator = creator,
                            onClick = { onOpenCreator(creator) },
                            onUnfollow = { viewModel.unfollow(creator) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SupportOpenShortsBanner(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(Icons.Filled.LocalCafe, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.donate_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
        )
        Text(
            "buymeacoffee.com",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun VideoTile(video: Video, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(9f / 14f)
            .background(Color.Black)
            .clickable(onClick = onClick),
    ) {
        RemoteImage(
            url = video.thumbnailUrl,
            contentDescription = video.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Text(formatCount(video.views), color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CreatorRow(creator: Creator, onClick: () -> Unit, onUnfollow: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Avatar(source = creator.avatarUrl, name = creator.displayName, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(creator.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "@${creator.handle}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedButton(onClick = onUnfollow) { Text(stringResource(R.string.following_button)) }
    }
}

@Composable
private fun EmptyTab(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
    )
}
