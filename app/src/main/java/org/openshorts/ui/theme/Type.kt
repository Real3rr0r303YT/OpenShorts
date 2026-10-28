package org.openshorts.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import org.openshorts.data.FontChoice

/**
 * Maps a [FontChoice] to a [FontFamily].
 *
 * To bundle a custom font, put the .ttf/.otf files into `app/src/main/res/font/`,
 * add a [FontChoice] entry and map it here, e.g.
 * `FontFamily(Font(R.font.inter_regular), Font(R.font.inter_bold, FontWeight.Bold))`.
 * Downloadable (Google) fonts are intentionally not used: they contact Google services.
 */
val FontChoice.fontFamily: FontFamily
    get() = when (this) {
        FontChoice.SYSTEM -> FontFamily.Default
        FontChoice.SANS_SERIF -> FontFamily.SansSerif
        FontChoice.SERIF -> FontFamily.Serif
        FontChoice.MONOSPACE -> FontFamily.Monospace
        FontChoice.CURSIVE -> FontFamily.Cursive
    }

/** Material 3 type scale with every style switched to the chosen font family. */
fun typographyFor(font: FontChoice): Typography {
    val base = Typography()
    if (font == FontChoice.SYSTEM) return base
    val family = font.fontFamily
    fun TextStyle.withFamily() = copy(fontFamily = family)
    return base.copy(
        displayLarge = base.displayLarge.withFamily(),
        displayMedium = base.displayMedium.withFamily(),
        displaySmall = base.displaySmall.withFamily(),
        headlineLarge = base.headlineLarge.withFamily(),
        headlineMedium = base.headlineMedium.withFamily(),
        headlineSmall = base.headlineSmall.withFamily(),
        titleLarge = base.titleLarge.withFamily(),
        titleMedium = base.titleMedium.withFamily(),
        titleSmall = base.titleSmall.withFamily(),
        bodyLarge = base.bodyLarge.withFamily(),
        bodyMedium = base.bodyMedium.withFamily(),
        bodySmall = base.bodySmall.withFamily(),
        labelLarge = base.labelLarge.withFamily(),
        labelMedium = base.labelMedium.withFamily(),
        labelSmall = base.labelSmall.withFamily(),
    )
}
