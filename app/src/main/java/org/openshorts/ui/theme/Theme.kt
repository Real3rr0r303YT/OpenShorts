package org.openshorts.ui.theme

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.openshorts.data.AppSettings

@get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
val supportsDynamicColor: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * OpenShorts theme engine: combines dark mode, Material You dynamic color,
 * built-in palettes, a custom accent color, an AMOLED black option and the
 * selected (bundled or imported) font.
 */
@Composable
fun OpenShortsTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    val dark = settings.isDark(isSystemInDarkTheme())
    val context = LocalContext.current
    val useDynamic = settings.dynamicColor && supportsDynamicColor

    val baseScheme = when {
        useDynamic && dark -> dynamicDarkColorScheme(context)
        useDynamic -> dynamicLightColorScheme(context)
        else -> remember(settings.palette, settings.customHue, dark) {
            settings.palette.colorScheme(dark, settings.customHue)
        }
    }
    val colorScheme = if (dark && settings.pureBlack) baseScheme.toPureBlack() else baseScheme
    val typography = remember(settings.font, settings.importedFontPath) {
        typographyFor(fontFamilyFor(settings.font, settings.importedFontPath))
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content,
    )
}
