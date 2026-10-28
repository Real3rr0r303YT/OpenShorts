package org.openshorts.ui.upload

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.openshorts.R
import org.openshorts.data.TECH_TAGS
import org.openshorts.data.UploadValidation
import org.openshorts.ui.components.formatDuration

/** "+ Upload": publish a vertical short from the gallery to the user's PeerTube channel. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UploadScreen(
    onClose: () -> Unit,
    viewModel: UploadViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var tagInput by rememberSaveable { mutableStateOf("") }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::pickVideo)
    }
    val uploading = state.phase is UploadPhase.Uploading
    val channels = account?.channels.orEmpty()
    val channelId = state.channelId ?: channels.firstOrNull()?.id
    val validation = UploadValidation.check(state.title, state.tags, state.video != null, channelId != null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.upload_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val finished = state.phase as? UploadPhase.Done
            if (finished != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.upload_done), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.upload_done_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { uriHandler.openUri(finished.watchUrl) }) { Text(stringResource(R.string.open_in_browser)) }
                    OutlinedButton(onClick = viewModel::reset) { Text(stringResource(R.string.upload_another)) }
                }
            } else {
                // Video picker.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(enabled = !uploading) {
                            pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val video = state.video
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            if (video == null) Icons.Filled.CloudUpload else Icons.Filled.VideoFile,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        if (video == null) {
                            Text(stringResource(R.string.upload_pick), fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(R.string.upload_pick_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(video.fileName, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            val details = buildList {
                                if (video.width > 0) add("${video.width}×${video.height}")
                                if (video.durationMs > 0) add(formatDuration((video.durationMs / 1000).toInt()))
                                add(Formatter.formatShortFileSize(context, video.size))
                            }.joinToString(" · ")
                            Text(
                                details,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                state.video?.let { video -> ShortCheck(video) }

                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::setTitle,
                    label = { Text(stringResource(R.string.upload_video_title)) },
                    singleLine = true,
                    enabled = !uploading,
                    isError = state.title.isNotEmpty() && !validation.titleValid,
                    supportingText = { Text("${state.title.length}/${UploadValidation.MAX_TITLE}") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::setDescription,
                    label = { Text(stringResource(R.string.upload_description)) },
                    minLines = 3,
                    enabled = !uploading,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Tags.
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { input ->
                        // Space or comma finishes a tag.
                        if (input.endsWith(" ") || input.endsWith(",")) {
                            viewModel.addTag(input.dropLast(1))
                            tagInput = ""
                        } else {
                            tagInput = input
                        }
                    },
                    label = { Text(stringResource(R.string.upload_tags, state.tags.size, UploadValidation.MAX_TAGS)) },
                    prefix = { Text("#") },
                    singleLine = true,
                    enabled = !uploading && state.tags.size < UploadValidation.MAX_TAGS,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        viewModel.addTag(tagInput)
                        tagInput = ""
                    }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.tags.forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = { viewModel.removeTag(tag) },
                                label = { Text("#$tag") },
                                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            )
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (TECH_TAGS + "shorts").filter { it !in state.tags }.forEach { tag ->
                        AssistChip(
                            onClick = { viewModel.addTag(tag) },
                            label = { Text("#$tag") },
                            enabled = !uploading && state.tags.size < UploadValidation.MAX_TAGS,
                        )
                    }
                }

                if (channels.size > 1) {
                    Text(stringResource(R.string.upload_channel), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        channels.forEach { channel ->
                            FilterChip(
                                selected = channel.id == channelId,
                                onClick = { viewModel.setChannel(channel.id) },
                                label = { Text(channel.displayName) },
                                enabled = !uploading,
                            )
                        }
                    }
                }

                Text(stringResource(R.string.upload_privacy), style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Privacy.entries.forEachIndexed { index, privacy ->
                        SegmentedButton(
                            selected = state.privacy == privacy,
                            onClick = { viewModel.setPrivacy(privacy) },
                            enabled = !uploading,
                            shape = SegmentedButtonDefaults.itemShape(index, Privacy.entries.size),
                        ) { Text(stringResource(privacy.labelRes)) }
                    }
                }

                when (val phase = state.phase) {
                    is UploadPhase.Uploading -> {
                        LinearProgressIndicator(progress = { phase.progress }, modifier = Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.upload_progress, (phase.progress * 100).toInt()),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedButton(onClick = viewModel::cancel) { Text(stringResource(R.string.cancel)) }
                        }
                        Text(
                            stringResource(R.string.upload_keep_open),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is UploadPhase.Failed -> Text(
                        stringResource(R.string.upload_failed, phase.message.orEmpty()),
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> Unit
                }

                Button(
                    onClick = viewModel::upload,
                    enabled = validation.isValid && !uploading,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.upload_button))
                }
                account?.let {
                    Text(
                        stringResource(R.string.upload_target, it.handle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private val Privacy.labelRes: Int
    get() = when (this) {
        Privacy.PUBLIC -> R.string.privacy_public
        Privacy.UNLISTED -> R.string.privacy_unlisted
        Privacy.PRIVATE -> R.string.privacy_private
    }

/** Shows whether the picked video is a real short (vertical, up to 60 seconds). */
@Composable
private fun ShortCheck(video: PickedVideo) {
    if (video.width <= 0 && video.durationMs <= 0) return
    val ok = video.isVertical && video.isShort
    val text = when {
        ok -> stringResource(R.string.upload_short_ok)
        !video.isVertical && video.width > 0 -> stringResource(R.string.upload_short_landscape)
        else -> stringResource(R.string.upload_short_long)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Info,
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
