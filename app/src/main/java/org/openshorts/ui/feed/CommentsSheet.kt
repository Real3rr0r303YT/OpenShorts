package org.openshorts.ui.feed

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.openshorts.OpenShortsApp
import org.openshorts.R
import org.openshorts.data.peertube.Comment
import org.openshorts.data.peertube.Video
import org.openshorts.ui.components.Avatar
import java.time.Instant

/** TikTok-style comment sheet with the video's PeerTube comments (read-only). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(video: Video, instanceUrl: String, onDismiss: () -> Unit) {
    val client = (LocalContext.current.applicationContext as OpenShortsApp).peerTubeClient
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var comments by remember(video.uuid) { mutableStateOf<List<Comment>>(emptyList()) }
    var total by remember(video.uuid) { mutableIntStateOf(video.comments.toInt()) }
    var nextStart by remember(video.uuid) { mutableIntStateOf(0) }
    var loading by remember(video.uuid) { mutableStateOf(false) }
    var end by remember(video.uuid) { mutableStateOf(false) }
    var error by remember(video.uuid) { mutableStateOf(false) }

    fun loadMore() {
        if (loading || end) return
        loading = true
        error = false
        scope.launch {
            try {
                val page = client.comments(instanceUrl, video.uuid, nextStart, PAGE_SIZE)
                comments = comments + page.comments
                total = page.total
                nextStart += PAGE_SIZE
                end = nextStart >= page.total
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = true
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(video.uuid) { loadMore() }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last >= comments.size - 3) loadMore() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .navigationBarsPadding(),
        ) {
            Text(
                text = pluralStringResource(R.plurals.comment_count, total, total),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
            HorizontalDivider()
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(16.dp),
            ) {
                items(comments, key = { it.id }) { comment -> CommentRow(comment) }
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        when {
                            loading -> CircularProgressIndicator()
                            error -> TextButton(onClick = ::loadMore) { Text(stringResource(R.string.retry)) }
                            comments.isEmpty() && end -> Text(
                                stringResource(R.string.comments_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            HorizontalDivider()
            // PeerTube needs an account on the instance to write comments.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.comments_write_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { uriHandler.openUri(video.watchUrl) }) {
                    Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.open_in_browser))
                }
            }
        }
    }
}

@Composable
private fun CommentRow(comment: Comment) {
    Row {
        Avatar(source = comment.authorAvatarUrl, name = comment.authorName, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = comment.authorName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(text = comment.text, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = relativeTime(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (comment.replies > 0) {
                    Text(
                        text = pluralStringResource(R.plurals.reply_count, comment.replies, comment.replies),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun relativeTime(iso: String): String =
    runCatching {
        DateUtils.getRelativeTimeSpanString(
            Instant.parse(iso).toEpochMilli(),
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    }.getOrDefault("")

private const val PAGE_SIZE = 20
