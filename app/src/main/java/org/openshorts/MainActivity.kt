package org.openshorts

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openshorts.data.AppSettings
import org.openshorts.ui.feed.FeedScreen
import org.openshorts.ui.settings.SettingsScreen
import org.openshorts.ui.theme.OpenShortsTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsFlow = (application as OpenShortsApp).settingsRepository.settings
        setContent {
            // Wait for persisted settings so the theme and instance never "jump" on start.
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = null)
            settings?.let { current ->
                OpenShortsRoot(
                    settings = current,
                    onLightSystemBarIcons = { light ->
                        val style = if (light) {
                            SystemBarStyle.dark(Color.TRANSPARENT)
                        } else {
                            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                        }
                        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                    },
                )
            }
        }
    }
}

private enum class Screen { FEED, SETTINGS }

@Composable
private fun OpenShortsRoot(
    settings: AppSettings,
    onLightSystemBarIcons: (Boolean) -> Unit,
) {
    var screen by rememberSaveable { mutableStateOf(Screen.FEED) }
    val stateHolder = rememberSaveableStateHolder()
    val dark = settings.isDark(isSystemInDarkTheme())

    // The feed is always black, so it always uses light status/navigation bar icons.
    LaunchedEffect(screen, dark) {
        onLightSystemBarIcons(screen == Screen.FEED || dark)
    }

    OpenShortsTheme(settings = settings) {
        stateHolder.SaveableStateProvider(screen.name) {
            when (screen) {
                Screen.FEED -> FeedScreen(onOpenSettings = { screen = Screen.SETTINGS })
                Screen.SETTINGS -> {
                    BackHandler { screen = Screen.FEED }
                    SettingsScreen(settings = settings, onBack = { screen = Screen.FEED })
                }
            }
        }
    }
}
