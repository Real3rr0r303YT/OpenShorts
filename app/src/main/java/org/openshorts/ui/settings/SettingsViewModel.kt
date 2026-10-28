package org.openshorts.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openshorts.OpenShortsApp
import org.openshorts.data.ColorPalette
import org.openshorts.data.FontChoice
import org.openshorts.data.ThemeMode
import org.openshorts.data.normalizeInstanceUrl

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as OpenShortsApp
    private val repository = app.settingsRepository

    private val _fontError = MutableStateFlow(false)

    /** True after a picked file could not be used as a font. */
    val fontError: StateFlow<Boolean> = _fontError.asStateFlow()

    fun setThemeMode(value: ThemeMode) = launch { repository.setThemeMode(value) }
    fun setDynamicColor(value: Boolean) = launch { repository.setDynamicColor(value) }
    fun setPureBlack(value: Boolean) = launch { repository.setPureBlack(value) }
    fun setPalette(value: ColorPalette) = launch { repository.setPalette(value) }
    fun setFont(value: FontChoice) = launch { repository.setFont(value) }
    fun setCustomHue(value: Float) = launch { repository.setCustomHue(value) }

    /** Imports a .ttf/.otf file and applies it app-wide. */
    fun importFont(uri: Uri) = launch {
        try {
            val (path, name) = app.localFiles.importFont(uri)
            repository.setImportedFont(path, name)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _fontError.value = true
        }
    }

    fun dismissFontError() {
        _fontError.value = false
    }
    fun setShortsOnly(value: Boolean) = launch { repository.setShortsOnly(value) }
    fun setLoopVideos(value: Boolean) = launch { repository.setLoopVideos(value) }
    fun setYouTubeEnabled(value: Boolean) = launch { repository.setYouTubeEnabled(value) }

    /** Returns false if [input] is not a valid HTTPS instance URL. */
    fun setInstanceUrl(input: String): Boolean {
        val url = normalizeInstanceUrl(input) ?: return false
        launch { repository.setInstanceUrl(url) }
        return true
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
