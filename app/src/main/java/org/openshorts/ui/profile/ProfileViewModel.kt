package org.openshorts.ui.profile

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.openshorts.OpenShortsApp
import org.openshorts.data.UserProfile
import org.openshorts.data.peertube.Creator
import org.openshorts.data.peertube.Video

data class ProfileUiState(
    val profile: UserProfile = UserProfile(),
    val liked: List<Video> = emptyList(),
    val saved: List<Video> = emptyList(),
    val following: List<Creator> = emptyList(),
    val loaded: Boolean = false,
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as OpenShortsApp
    private val repository = app.profileRepository

    val state: StateFlow<ProfileUiState> = combine(
        repository.profile,
        repository.likedVideos,
        repository.savedVideos,
        repository.following,
    ) { profile, liked, saved, following ->
        ProfileUiState(profile, liked, saved, following, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    private val _imageError = MutableStateFlow(false)

    /** True after an image could not be imported (e.g. unsupported format). */
    val imageError: StateFlow<Boolean> = _imageError.asStateFlow()

    fun save(profile: UserProfile) {
        viewModelScope.launch { repository.saveProfile(profile) }
    }

    fun importAvatar(uri: Uri) = importImage(uri, "avatar", 512) { repository.setAvatarPath(it) }

    fun importBanner(uri: Uri) = importImage(uri, "banner", 1600) { repository.setBannerPath(it) }

    fun removeAvatar() {
        viewModelScope.launch { repository.setAvatarPath(null) }
    }

    fun removeBanner() {
        viewModelScope.launch { repository.setBannerPath(null) }
    }

    fun unfollow(creator: Creator) {
        viewModelScope.launch { repository.toggleFollow(creator) }
    }

    fun dismissImageError() {
        _imageError.value = false
    }

    private fun importImage(uri: Uri, name: String, maxSize: Int, store: suspend (String) -> Unit) {
        viewModelScope.launch {
            try {
                store(app.localFiles.importImage(uri, name, maxSize))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _imageError.value = true
            }
        }
    }
}
