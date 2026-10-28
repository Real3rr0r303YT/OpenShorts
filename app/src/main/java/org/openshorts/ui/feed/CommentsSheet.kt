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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val app = LocalContext.current.applicationContext as OpenShortsApp
    val client = app.peerTubeClient
    val accounts = app.accountRepository
    val session by accounts.session.collectAsStateWithLifecycle()
    var draft by rememberSaveable(video.uuid) { mutableStateOf("") }
    var posting by remember { mutableStateOf(false) }
    var postError by remember { mutableStateOf<String?>(null) }
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
                if (video.isYouTube) {
                    // YouTube comments (first page) through the Piped/Invidious proxy, read-only.
                    val page = app.youTubeClient.comments(video)
                    comments = page.comments
                    total = maxOf(total, page.total)
                    end = true
                } else {
                    val page = client.comments(instanceUrl, video.uuid, nextStart, PAGE_SIZE)
                    comments = comments + page.comments
                    total = page.total
                    nextStart += PAGE_SIZE
                    end = nextStart >= page.total
                }
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
            val errorText = postError
            if (session != null && !video.isYouTube) {
                // Logged in: write a comment through the user's own PeerTube server.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = {
                            draft = it
                            postError = null
                        },
                        placeholder = { Text(stringResource(R.string.comment_hint)) },
                        isError = errorText != null,
                        supportingText = if (errorText != null) {
                            { Text(errorText) }
                        } else {
                            null
                        },
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    val failedText = stringResource(R.string.comment_failed)
                    FilledIconButton(
                        enabled = draft.isNotBlank() && !posting,
                        onClick = {
                            posting = true
                            scope.launch {
                                try {
                                    val created = accounts.postComment(video, draft)
                                    if (created != null) comments = listOf(created) + comments
                                    total += 1
                                    draft = ""
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    postError = e.message?.let { "$failedText ($it)" } ?: failedText
                                } finally {
                                    posting = false
                                }
                            }
                        },
                    ) {
                        if (posting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chat_send))
                        }
                    }
                }
            } else Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(if (video.isYouTube) R.string.comments_youtube_hint else R.string.comments_login_hint),
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
