package org.openshorts.data

import org.openshorts.BuildConfig

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ColorPalette { OCEAN, FOREST, SUNSET, GRAPE, MONOCHROME, CUSTOM }

/** Bundled fonts live in res/font; [IMPORTED] is a font file the user picked. */
enum class FontChoice { SYSTEM, POPPINS, ATKINSON_HYPERLEGIBLE, SPACE_MONO, PACIFICO, SERIF, IMPORTED }

/** A PeerTube server offered as a quick pick in the settings. */
data class InstancePreset(val url: String, val name: String, val isTech: Boolean)

/** Well-known public PeerTube instances; tech/IT-focused ones are listed first. */
val PRESET_INSTANCES = listOf(
    InstancePreset("https://peertube.linuxrocks.online", "LinuxRocks", isTech = true),
    InstancePreset("https://peertube.debian.social", "Debian Social", isTech = true),
    InstancePreset("https://tilvids.com", "TILvids", isTech = true),
    InstancePreset("https://diode.zone", "Diode Zone", isTech = true),
    InstancePreset("https://tube.tchncs.de", "tchncs", isTech = true),
    InstancePreset("https://framatube.org", "Framatube", isTech = false),
    InstancePreset("https://peertube.tv", "PeerTube.TV", isTech = false),
)

/** All user-configurable options. Stored locally only, never transmitted. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val pureBlack: Boolean = false,
    val palette: ColorPalette = ColorPalette.OCEAN,
    /** Hue (0–360) of the accent color used by [ColorPalette.CUSTOM]. */
    val customHue: Float = 340f,
    val font: FontChoice = FontChoice.POPPINS,
    /** Absolute path of the imported font file used by [FontChoice.IMPORTED]. */
    val importedFontPath: String? = null,
    val importedFontName: String? = null,
    val instanceUrl: String = BuildConfig.DEFAULT_INSTANCE,
    val shortsOnly: Boolean = true,
    val loopVideos: Boolean = true,
    /** Offers the "YouTube Shorts" feed (via Piped/Invidious proxies). */
    val youTubeEnabled: Boolean = true,
    /** The user's own Piped or Invidious instance (tried first), e.g. `https://pipedapi.example.org`. */
    val youTubeCustomInstance: String? = null,
    val youTubeCustomType: YouTubeInstanceType = YouTubeInstanceType.PIPED,
) {
    fun isDark(systemDark: Boolean): Boolean = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}

enum class YouTubeInstanceType { PIPED, INVIDIOUS }
