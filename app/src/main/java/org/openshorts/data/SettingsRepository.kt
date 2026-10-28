package org.openshorts.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.dataStore

    val settings: Flow<AppSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            val defaults = AppSettings()
            AppSettings(
                themeMode = prefs[THEME_MODE].toEnum(defaults.themeMode),
                dynamicColor = prefs[DYNAMIC_COLOR] ?: defaults.dynamicColor,
                pureBlack = prefs[PURE_BLACK] ?: defaults.pureBlack,
                palette = prefs[PALETTE].toEnum(defaults.palette),
                customHue = prefs[CUSTOM_HUE] ?: defaults.customHue,
                font = prefs[FONT].toEnum(defaults.font),
                importedFontPath = prefs[IMPORTED_FONT_PATH],
                importedFontName = prefs[IMPORTED_FONT_NAME],
                instanceUrl = prefs[INSTANCE_URL] ?: defaults.instanceUrl,
                shortsOnly = prefs[SHORTS_ONLY] ?: defaults.shortsOnly,
                loopVideos = prefs[LOOP_VIDEOS] ?: defaults.loopVideos,
            )
        }

    suspend fun setThemeMode(value: ThemeMode) = update { it[THEME_MODE] = value.name }
    suspend fun setDynamicColor(value: Boolean) = update { it[DYNAMIC_COLOR] = value }
    suspend fun setPureBlack(value: Boolean) = update { it[PURE_BLACK] = value }
    suspend fun setPalette(value: ColorPalette) = update { it[PALETTE] = value.name }
    suspend fun setCustomHue(value: Float) = update { it[CUSTOM_HUE] = value.coerceIn(0f, 360f) }
    suspend fun setFont(value: FontChoice) = update { it[FONT] = value.name }

    suspend fun setImportedFont(path: String, name: String) = update {
        it[IMPORTED_FONT_PATH] = path
        it[IMPORTED_FONT_NAME] = name
        it[FONT] = FontChoice.IMPORTED.name
    }
    suspend fun setInstanceUrl(value: String) = update { it[INSTANCE_URL] = value }
    suspend fun setShortsOnly(value: Boolean) = update { it[SHORTS_ONLY] = value }
    suspend fun setLoopVideos(value: Boolean) = update { it[LOOP_VIDEOS] = value }

    private suspend fun update(block: (MutablePreferences) -> Unit) {
        dataStore.edit { block(it) }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val PURE_BLACK = booleanPreferencesKey("pure_black")
        val PALETTE = stringPreferencesKey("palette")
        val CUSTOM_HUE = floatPreferencesKey("custom_hue")
        val FONT = stringPreferencesKey("font")
        val IMPORTED_FONT_PATH = stringPreferencesKey("imported_font_path")
        val IMPORTED_FONT_NAME = stringPreferencesKey("imported_font_name")
        val INSTANCE_URL = stringPreferencesKey("instance_url")
        val SHORTS_ONLY = booleanPreferencesKey("shorts_only")
        val LOOP_VIDEOS = booleanPreferencesKey("loop_videos")
    }
}

private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
    this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default
