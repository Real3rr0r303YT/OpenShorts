package org.openshorts.ui.upload

import android.app.Application
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openshorts.OpenShortsApp
import org.openshorts.data.UploadValidation
import org.openshorts.data.normalizeTag
import org.openshorts.data.peertube.UploadMetadata
import java.io.IOException

enum class Privacy(val value: Int) { PUBLIC(1), UNLISTED(2), PRIVATE(3) }

data class PickedVideo(
    val uri: Uri,
    val fileName: String,
    val size: Long,
    val mimeType: String,
    /** Display size (rotation applied) and length; 0 if unknown. */
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0,
) {
    val isVertical: Boolean get() = width > 0 && height > width
    val isShort: Boolean get() = durationMs in 1..60_000
}

sealed interface UploadPhase {
    data object Editing : UploadPhase
    data class Uploading(val progress: Float) : UploadPhase
    data class Done(val watchUrl: String) : UploadPhase
    data class Failed(val message: String?) : UploadPhase
}

data class UploadUiState(
    val video: PickedVideo? = null,
    val title: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val channelId: Long? = null,
    val privacy: Privacy = Privacy.PUBLIC,
    val phase: UploadPhase = UploadPhase.Editing,
) {
    val validation: UploadValidation get() = UploadValidation.check(title, tags, video != null, channelId != null)
}

/** Uploads a short video from the gallery to the user's PeerTube channel (resumable upload). */
class UploadViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as OpenShortsApp
    private val accounts = app.accountRepository

    private val _state = MutableStateFlow(UploadUiState())
    val state: StateFlow<UploadUiState> = _state.asStateFlow()

    val account = accounts.account
    private var job: Job? = null
    private var sessionUrl: String? = null

    fun pickVideo(uri: Uri) {
        viewModelScope.launch {
            val picked = withContext(Dispatchers.IO) { describe(uri) }
            _state.update {
                it.copy(
                    video = picked,
                    title = it.title.ifBlank { picked?.fileName?.substringBeforeLast('.')?.take(UploadValidation.MAX_TITLE).orEmpty() },
                    phase = UploadPhase.Editing,
                )
            }
        }
    }

    fun setTitle(value: String) = _state.update { it.copy(title = value.take(UploadValidation.MAX_TITLE)) }
    fun setDescription(value: String) = _state.update { it.copy(description = value.take(UploadValidation.MAX_DESCRIPTION)) }
    fun setPrivacy(value: Privacy) = _state.update { it.copy(privacy = value) }
    fun setChannel(id: Long) = _state.update { it.copy(channelId = id) }

    fun addTag(raw: String) {
        val tag = normalizeTag(raw) ?: return
        _state.update { s ->
            if (tag in s.tags || s.tags.size >= UploadValidation.MAX_TAGS) s else s.copy(tags = s.tags + tag)
        }
    }

    fun removeTag(tag: String) = _state.update { it.copy(tags = it.tags - tag) }

    fun upload() {
        val s = _state.value
        val video = s.video ?: return
        val channelId = s.channelId ?: account.value?.channels?.firstOrNull()?.id ?: return
        if (!UploadValidation.check(s.title, s.tags, true, true).isValid || job?.isActive == true) return
        _state.update { it.copy(phase = UploadPhase.Uploading(0f), channelId = channelId) }

        job = viewModelScope.launch {
            try {
                val metadata = UploadMetadata(
                    name = s.title.trim(),
                    channelId = channelId,
                    filename = video.fileName,
                    privacy = s.privacy.value,
                    description = s.description.trim().ifBlank { null },
                    tags = s.tags,
                )
                val session = accounts.startUpload(metadata, video.size, video.mimeType)
                sessionUrl = session
                val uploaded = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(video.uri)?.use { input ->
                        accounts.uploadChunks(session, input, video.size) { progress ->
                            _state.update { it.copy(phase = UploadPhase.Uploading(progress)) }
                        }
                    } ?: throw IOException("Cannot read video")
                }
                sessionUrl = null
                val instance = account.value?.instanceUrl.orEmpty()
                _state.update { it.copy(phase = UploadPhase.Done("$instance/w/${uploaded.shortUUID ?: uploaded.uuid}")) }
            } catch (e: CancellationException) {
                sessionUrl?.let { url -> withContext(NonCancellable) { accounts.cancelUpload(url) } }
                sessionUrl = null
                _state.update { it.copy(phase = UploadPhase.Editing) }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(phase = UploadPhase.Failed(e.message)) }
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    /** Clears the form for the next upload. */
    fun reset() {
        job?.cancel()
        _state.value = UploadUiState()
    }

    private fun describe(uri: Uri): PickedVideo? {
        val resolver = app.contentResolver
        var name = "video.mp4"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        if (size <= 0) return null
        // Read resolution, rotation and length to tell the user whether this is a real short.
        val retriever = MediaMetadataRetriever()
        var width = 0
        var height = 0
        var duration = 0L
        try {
            retriever.setDataSource(app, uri)
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) width = height.also { height = width }
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            // Unknown format: upload anyway, the server decides.
        } finally {
            retriever.release()
        }
        return PickedVideo(uri, name, size, resolver.getType(uri) ?: "video/mp4", width, height, duration)
    }
}
