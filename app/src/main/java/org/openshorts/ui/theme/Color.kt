package org.openshorts.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.openshorts.data.ColorPalette

/**
 * Hue/chroma description of a palette. Full Material 3 color schemes are derived
 * from it using Material-style tones (lightness 0–100), so adding a new palette is
 * a single line.
 */
data class PaletteSpec(
    val primaryHue: Float,
    val secondaryHue: Float,
    val tertiaryHue: Float,
    val chroma: Float,
)

/** [customHue] is only used by [ColorPalette.CUSTOM] (the user's accent color). */
fun ColorPalette.spec(customHue: Float = 0f): PaletteSpec =
    when (this) {
        ColorPalette.OCEAN -> PaletteSpec(primaryHue = 200f, secondaryHue = 210f, tertiaryHue = 250f, chroma = 0.85f)
        ColorPalette.FOREST -> PaletteSpec(primaryHue = 140f, secondaryHue = 120f, tertiaryHue = 180f, chroma = 0.55f)
        ColorPalette.SUNSET -> PaletteSpec(primaryHue = 18f, secondaryHue = 30f, tertiaryHue = 330f, chroma = 0.80f)
        ColorPalette.GRAPE -> PaletteSpec(primaryHue = 275f, secondaryHue = 285f, tertiaryHue = 330f, chroma = 0.60f)
        ColorPalette.MONOCHROME -> PaletteSpec(primaryHue = 0f, secondaryHue = 0f, tertiaryHue = 0f, chroma = 0f)
        ColorPalette.CUSTOM -> PaletteSpec(
            primaryHue = customHue,
            secondaryHue = customHue,
            tertiaryHue = (customHue + 60f) % 360f,
            chroma = 0.75f,
        )
    }

fun ColorPalette.colorScheme(dark: Boolean, customHue: Float = 0f): ColorScheme {
    val spec = this.spec(customHue)
    fun tone(hue: Float, tone: Int, chromaScale: Float = 1f) =
        Color.hsl(hue, (spec.chroma * chromaScale).coerceIn(0f, 1f), tone / 100f)

    val p = spec.primaryHue
    val s = spec.secondaryHue
    val t = spec.tertiaryHue
    // Neutral surfaces get a faint tint of the primary hue.
    fun neutral(tone: Int) = tone(p, tone, 0.12f)
    fun neutralVariant(tone: Int) = tone(p, tone, 0.22f)

    return if (!dark) {
        lightColorScheme(
            primary = tone(p, 40), onPrimary = Color.White,
            primaryContainer = tone(p, 90), onPrimaryContainer = tone(p, 10),
            inversePrimary = tone(p, 80),
            secondary = tone(s, 40, 0.45f), onSecondary = Color.White,
            secondaryContainer = tone(s, 90, 0.45f), onSecondaryContainer = tone(s, 10, 0.45f),
            tertiary = tone(t, 40, 0.6f), onTertiary = Color.White,
            tertiaryContainer = tone(t, 90, 0.6f), onTertiaryContainer = tone(t, 10, 0.6f),
            background = neutral(98), onBackground = neutral(10),
            surface = neutral(98), onSurface = neutral(10),
            surfaceVariant = neutralVariant(90), onSurfaceVariant = neutralVariant(30),
            surfaceTint = tone(p, 40),
            inverseSurface = neutral(20), inverseOnSurface = neutral(95),
            outline = neutralVariant(50), outlineVariant = neutralVariant(80),
            surfaceBright = neutral(98), surfaceDim = neutral(87),
            surfaceContainerLowest = neutral(100), surfaceContainerLow = neutral(96),
            surfaceContainer = neutral(94), surfaceContainerHigh = neutral(92),
            surfaceContainerHighest = neutral(90),
        )
    } else {
        darkColorScheme(
            primary = tone(p, 80), onPrimary = tone(p, 20),
            primaryContainer = tone(p, 30), onPrimaryContainer = tone(p, 90),
            inversePrimary = tone(p, 40),
            secondary = tone(s, 80, 0.45f), onSecondary = tone(s, 20, 0.45f),
            secondaryContainer = tone(s, 30, 0.45f), onSecondaryContainer = tone(s, 90, 0.45f),
            tertiary = tone(t, 80, 0.6f), onTertiary = tone(t, 20, 0.6f),
            tertiaryContainer = tone(t, 30, 0.6f), onTertiaryContainer = tone(t, 90, 0.6f),
            background = neutral(6), onBackground = neutral(90),
            surface = neutral(6), onSurface = neutral(90),
            surfaceVariant = neutralVariant(30), onSurfaceVariant = neutralVariant(80),
            surfaceTint = tone(p, 80),
            inverseSurface = neutral(90), inverseOnSurface = neutral(20),
            outline = neutralVariant(60), outlineVariant = neutralVariant(30),
            surfaceBright = neutral(24), surfaceDim = neutral(6),
            surfaceContainerLowest = neutral(4), surfaceContainerLow = neutral(10),
            surfaceContainer = neutral(12), surfaceContainerHigh = neutral(17),
            surfaceContainerHighest = neutral(22),
        )
    }
}

/** AMOLED variant: true black backgrounds, slightly lifted containers for contrast. */
fun ColorScheme.toPureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF111111),
)
