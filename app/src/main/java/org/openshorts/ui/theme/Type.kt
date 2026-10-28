package org.openshorts.ui.theme

import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.openshorts.R
import org.openshorts.data.FontChoice
import java.io.File

private val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

private val AtkinsonHyperlegible = FontFamily(
    Font(R.font.atkinson_hyperlegible_regular, FontWeight.Normal),
    Font(R.font.atkinson_hyperlegible_bold, FontWeight.Bold),
)

private val SpaceMono = FontFamily(
    Font(R.font.space_mono_regular, FontWeight.Normal),
    Font(R.font.space_mono_bold, FontWeight.Bold),
)

private val Pacifico = FontFamily(Font(R.font.pacifico_regular, FontWeight.Normal))

/**
 * Resolves a [FontChoice] to a [FontFamily].
 *
 * Bundled fonts live in `res/font/` (all SIL Open Font License, see `licenses/fonts/`).
 * To add one, drop the .ttf files there, add a [FontChoice] entry and map it below.
 * [FontChoice.IMPORTED] loads a font file the user picked in the settings.
 * Downloadable (Google) fonts are intentionally not used: they contact Google services.
 */
fun fontFamilyFor(font: FontChoice, importedFontPath: String? = null): FontFamily = when (font) {
    FontChoice.SYSTEM -> FontFamily.Default
    FontChoice.POPPINS -> Poppins
    FontChoice.ATKINSON_HYPERLEGIBLE -> AtkinsonHyperlegible
    FontChoice.SPACE_MONO -> SpaceMono
    FontChoice.PACIFICO -> Pacifico
    FontChoice.SERIF -> FontFamily.Serif
    FontChoice.IMPORTED -> importedFontPath
        ?.let { path -> runCatching { Typeface.createFromFile(File(path)) }.getOrNull() }
        ?.let { FontFamily(it) }
        ?: FontFamily.Default
}

/** Material 3 type scale with every style switched to [family]. */
fun typographyFor(family: FontFamily): Typography {
    val base = Typography()
    if (family == FontFamily.Default) return base
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
