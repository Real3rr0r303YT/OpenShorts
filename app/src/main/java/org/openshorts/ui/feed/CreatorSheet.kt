package org.openshorts.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.openshorts.R
import org.openshorts.data.peertube.Video
import org.openshorts.ui.components.Avatar

/** Quick creator profile: follow, see their videos, message them, open their channel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorSheet(
    video: Video,
    isFollowing: Boolean,
    onToggleFollow: () -> Unit,
    onShowVideos: () -> Unit,
    onMessage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Avatar(source = video.creatorAvatarUrl, name = video.channelName, size = 88.dp)
            Spacer(Modifier.height(12.dp))
            Text(video.channelName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                video.accountHandle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (isFollowing) {
                    OutlinedButton(onClick = onToggleFollow, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.following_button))
                    }
                } else {
                    Button(onClick = onToggleFollow, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.follow))
                    }
                }
                FilledTonalButton(onClick = onMessage, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.message))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onShowVideos, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.VideoLibrary, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.creator_videos))
                }
                video.creatorUrl?.let { url ->
                    OutlinedButton(onClick = { uriHandler.openUri(url) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.open_channel))
                    }
                }
            }
        }
    }
}
