package org.openshorts.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.openshorts.OpenShortsApp
import org.openshorts.data.ColorPalette
import org.openshorts.data.FontChoice
import org.openshorts.data.ThemeMode
import org.openshorts.data.normalizeInstanceUrl

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as OpenShortsApp).settingsRepository

    fun setThemeMode(value: ThemeMode) = launch { repository.setThemeMode(value) }
    fun setDynamicColor(value: Boolean) = launch { repository.setDynamicColor(value) }
    fun setPureBlack(value: Boolean) = launch { repository.setPureBlack(value) }
    fun setPalette(value: ColorPalette) = launch { repository.setPalette(value) }
    fun setFont(value: FontChoice) = launch { repository.setFont(value) }
    fun setShortsOnly(value: Boolean) = launch { repository.setShortsOnly(value) }
    fun setLoopVideos(value: Boolean) = launch { repository.setLoopVideos(value) }

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
