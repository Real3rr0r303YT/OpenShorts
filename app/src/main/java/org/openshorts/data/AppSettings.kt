package org.openshorts.data

import org.openshorts.BuildConfig

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ColorPalette { OCEAN, FOREST, SUNSET, GRAPE, MONOCHROME }

enum class FontChoice { SYSTEM, SANS_SERIF, SERIF, MONOSPACE, CURSIVE }

/** All user-configurable options. Stored locally only, never transmitted. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val pureBlack: Boolean = false,
    val palette: ColorPalette = ColorPalette.OCEAN,
    val font: FontChoice = FontChoice.SYSTEM,
    val instanceUrl: String = BuildConfig.DEFAULT_INSTANCE,
    val shortsOnly: Boolean = true,
    val loopVideos: Boolean = true,
) {
    fun isDark(systemDark: Boolean): Boolean = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}
