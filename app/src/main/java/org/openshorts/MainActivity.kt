package org.openshorts

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.openshorts.data.AppSettings
import org.openshorts.data.SearchQuery
import org.openshorts.ui.chat.ChatScreen
import org.openshorts.ui.chat.ChatViewModel
import org.openshorts.ui.chat.InboxScreen
import org.openshorts.ui.feed.FeedScreen
import org.openshorts.ui.feed.FeedViewModel
import org.openshorts.ui.feed.toCreator
import org.openshorts.ui.profile.EditProfileScreen
import org.openshorts.ui.profile.ProfileScreen
import org.openshorts.ui.settings.SettingsScreen
import org.openshorts.ui.theme.OpenShortsTheme
import androidx.compose.ui.graphics.Color as ComposeColor

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

/** Bottom navigation tabs. */
private enum class Tab(val icon: ImageVector, val label: Int) {
    HOME(Icons.Filled.Home, R.string.nav_home),
    INBOX(Icons.AutoMirrored.Filled.Chat, R.string.nav_inbox),
    PROFILE(Icons.Filled.Person, R.string.nav_profile),
    SETTINGS(Icons.Filled.Settings, R.string.nav_settings),
}

@Composable
private fun OpenShortsRoot(
    settings: AppSettings,
    onLightSystemBarIcons: (Boolean) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    // Full-screen pages on top of the tabs.
    var editingProfile by rememberSaveable { mutableStateOf(false) }
    var openChatId by rememberSaveable { mutableStateOf<String?>(null) }
    val stateHolder = rememberSaveableStateHolder()
    val dark = settings.isDark(isSystemInDarkTheme())

    // Activity-scoped, so the feed keeps its position and the player survives tab switches.
    val feedViewModel: FeedViewModel = viewModel()
    val chatViewModel: ChatViewModel = viewModel()

    val immersive = tab == Tab.HOME && !editingProfile && openChatId == null
    LaunchedEffect(immersive, tab, dark) {
        // The feed is black and the profile starts with a dark banner: light icons there.
        onLightSystemBarIcons(immersive || (tab == Tab.PROFILE && !editingProfile && openChatId == null) || dark)
    }

    OpenShortsTheme(settings = settings) {
        when {
            editingProfile -> {
                BackHandler { editingProfile = false }
                EditProfileScreen(onDone = { editingProfile = false })
            }

            openChatId != null -> {
                BackHandler { openChatId = null }
                ChatScreen(conversationId = openChatId!!, onBack = { openChatId = null }, viewModel = chatViewModel)
            }

            else -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    stateHolder.SaveableStateProvider(tab.name) {
                        when (tab) {
                            Tab.HOME -> FeedScreen(
                                onOpenSettings = { tab = Tab.SETTINGS },
                                onMessageCreator = { video ->
                                    video.toCreator()?.let { creator ->
                                        openChatId = chatViewModel.openConversation(
                                            creator.handle,
                                            creator.displayName,
                                            creator.avatarUrl,
                                        )
                                    }
                                },
                                viewModel = feedViewModel,
                            )
                            Tab.INBOX -> InboxScreen(
                                onOpenConversation = { openChatId = it },
                                viewModel = chatViewModel,
                            )
                            Tab.PROFILE -> ProfileScreen(
                                onEditProfile = { editingProfile = true },
                                onPlayVideos = { title, videos, index ->
                                    feedViewModel.playCollection(title, videos, index)
                                    tab = Tab.HOME
                                },
                                onOpenCreator = { creator ->
                                    feedViewModel.search(SearchQuery.Channel(creator.handle))
                                    tab = Tab.HOME
                                },
                            )
                            Tab.SETTINGS -> SettingsScreen(settings = settings)
                        }
                    }
                }
                BottomBar(selected = tab, onSelect = { tab = it }, overVideo = tab == Tab.HOME)
            }
        }
        if (tab != Tab.HOME && !editingProfile && openChatId == null) {
            BackHandler { tab = Tab.HOME }
        }
    }
}

@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit, overVideo: Boolean) {
    // On the video feed the bar is black like in other short-video apps.
    val container = if (overVideo) ComposeColor.Black else MaterialTheme.colorScheme.surfaceContainer
    val content = if (overVideo) ComposeColor.White else MaterialTheme.colorScheme.onSurface
    NavigationBar(containerColor = container) {
        Tab.entries.forEach { item ->
            NavigationBarItem(
                selected = item == selected,
                onClick = { onSelect(item) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(stringResource(item.label)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = if (overVideo) ComposeColor.Black else MaterialTheme.colorScheme.onSecondaryContainer,
                    indicatorColor = if (overVideo) ComposeColor.White else MaterialTheme.colorScheme.secondaryContainer,
                    selectedTextColor = content,
                    unselectedIconColor = content.copy(alpha = 0.7f),
                    unselectedTextColor = content.copy(alpha = 0.7f),
                ),
            )
        }
    }
}
