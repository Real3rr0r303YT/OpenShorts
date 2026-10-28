package org.openshorts

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.lifecycle.viewmodel.compose.viewModel
import org.openshorts.data.AppSettings
import org.openshorts.ui.account.AccountViewModel
import org.openshorts.ui.account.LoginScreen
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
import org.openshorts.ui.upload.UploadScreen
import androidx.compose.ui.graphics.Color as ComposeColor

class MainActivity : ComponentActivity() {

    /** Text shared to OpenShorts (e.g. a TikTok link), consumed by the feed. */
    private val sharedText = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)

        val settingsFlow = (application as OpenShortsApp).settingsRepository.settings
        setContent {
            // Wait for persisted settings so the theme and instance never "jump" on start.
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = null)
            settings?.let { current ->
                OpenShortsRoot(
                    settings = current,
                    sharedText = sharedText,
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            sharedText.value = intent.getStringExtra(Intent.EXTRA_TEXT)
        }
    }
}

/** Bottom navigation tabs. [UPLOAD] is the "+" button in the middle and opens a full-screen page. */
private enum class Tab(val icon: ImageVector, val label: Int) {
    HOME(Icons.Filled.Home, R.string.nav_home),
    INBOX(Icons.AutoMirrored.Filled.Chat, R.string.nav_inbox),
    UPLOAD(Icons.Filled.AddBox, R.string.nav_upload),
    PROFILE(Icons.Filled.Person, R.string.nav_profile),
    SETTINGS(Icons.Filled.Settings, R.string.nav_settings),
}

/** Full-screen pages shown on top of the tabs. */
private enum class Page { EDIT_PROFILE, LOGIN, UPLOAD }

@Composable
private fun OpenShortsRoot(
    settings: AppSettings,
    sharedText: MutableStateFlow<String?>,
    onLightSystemBarIcons: (Boolean) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var page by rememberSaveable { mutableStateOf<Page?>(null) }
    var openChatId by rememberSaveable { mutableStateOf<String?>(null) }
    val pageOpen = page != null
    val stateHolder = rememberSaveableStateHolder()
    val dark = settings.isDark(isSystemInDarkTheme())

    // Activity-scoped, so the feed keeps its position and the player survives tab switches.
    val feedViewModel: FeedViewModel = viewModel()
    val chatViewModel: ChatViewModel = viewModel()
    val accountViewModel: AccountViewModel = viewModel()
    val account by accountViewModel.account.collectAsStateWithLifecycle()
    val sessionExpired by accountViewModel.sessionExpired.collectAsStateWithLifecycle()
    // A link shared to OpenShorts (TikTok app → Share → OpenShorts) goes to the TikTok feed.
    val shared by sharedText.collectAsStateWithLifecycle()
    LaunchedEffect(shared) {
        val text = shared ?: return@LaunchedEffect
        sharedText.value = null
        page = null
        openChatId = null
        tab = Tab.HOME
        feedViewModel.addTikTokLink(text)
    }

    // Uploading needs a PeerTube account: the "+" opens the login first.
    val openUpload = { page = if (account != null) Page.UPLOAD else Page.LOGIN }

    val immersive = tab == Tab.HOME && !pageOpen && openChatId == null
    LaunchedEffect(immersive, tab, dark) {
        // The feed is black and the profile starts with a dark banner: light icons there.
        onLightSystemBarIcons(immersive || (tab == Tab.PROFILE && !pageOpen && openChatId == null) || dark)
    }

    OpenShortsTheme(settings = settings) {
        when {
            page == Page.EDIT_PROFILE -> {
                BackHandler { page = null }
                EditProfileScreen(onDone = { page = null })
            }

            page == Page.LOGIN -> {
                BackHandler { page = null }
                LoginScreen(defaultInstance = settings.instanceUrl, onDone = { page = null }, viewModel = accountViewModel)
            }

            page == Page.UPLOAD -> {
                BackHandler { page = null }
                UploadScreen(onClose = { page = null })
            }

            openChatId != null -> {
                BackHandler { openChatId = null }
                ChatScreen(conversationId = openChatId!!, onBack = { openChatId = null }, viewModel = chatViewModel)
            }

            else -> Box(Modifier.fillMaxSize()) {
                // The navigation bar floats transparently over the video feed (full-screen feel);
                // other tabs are laid out above it.
                val barHeight = NAV_BAR_HEIGHT + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = if (tab == Tab.HOME) 0.dp else barHeight),
                ) {
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
                                bottomInset = barHeight,
                                viewModel = feedViewModel,
                            )
                            Tab.INBOX -> InboxScreen(
                                onOpenConversation = { openChatId = it },
                                viewModel = chatViewModel,
                            )
                            Tab.UPLOAD -> Unit
                            Tab.PROFILE -> ProfileScreen(
                                onEditProfile = { page = Page.EDIT_PROFILE },
                                onLogin = { page = Page.LOGIN },
                                onUpload = openUpload,
                                accountViewModel = accountViewModel,
                                onPlayVideos = { title, videos, index ->
                                    feedViewModel.playCollection(title, videos, index)
                                    tab = Tab.HOME
                                },
                                onOpenCreator = { creator ->
                                    feedViewModel.openCreator(creator)
                                    tab = Tab.HOME
                                },
                            )
                            Tab.SETTINGS -> SettingsScreen(
                                settings = settings,
                                onOpenLogin = { page = Page.LOGIN },
                                accountViewModel = accountViewModel,
                            )
                        }
                    }
                }
                BottomBar(
                    selected = tab,
                    onSelect = { if (it == Tab.UPLOAD) openUpload() else tab = it },
                    overVideo = tab == Tab.HOME,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
        if (tab != Tab.HOME && !pageOpen && openChatId == null) {
            BackHandler { tab = Tab.HOME }
        }
        if (sessionExpired) {
            AlertDialog(
                onDismissRequest = accountViewModel::acknowledgeSessionExpired,
                title = { Text(stringResource(R.string.session_expired_title)) },
                text = { Text(stringResource(R.string.session_expired_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        accountViewModel.acknowledgeSessionExpired()
                        page = Page.LOGIN
                    }) { Text(stringResource(R.string.login_button)) }
                },
                dismissButton = {
                    TextButton(onClick = accountViewModel::acknowledgeSessionExpired) { Text(stringResource(R.string.cancel)) }
                },
            )
        }
    }
}

@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit, overVideo: Boolean, modifier: Modifier = Modifier) {
    // On the video feed the bar is transparent, so the video really runs edge to edge.
    val container = if (overVideo) ComposeColor.Transparent else MaterialTheme.colorScheme.surfaceContainer
    val content = if (overVideo) ComposeColor.White else MaterialTheme.colorScheme.onSurface
    NavigationBar(
        modifier = modifier.height(NAV_BAR_HEIGHT + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        containerColor = container,
        tonalElevation = 0.dp,
    ) {
        Tab.entries.forEach { item ->
            if (item == Tab.UPLOAD) {
                UploadNavItem(onClick = { onSelect(item) }, overVideo = overVideo)
            } else NavigationBarItem(
                selected = item == selected,
                onClick = { onSelect(item) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(stringResource(item.label)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = if (overVideo) ComposeColor.Black else MaterialTheme.colorScheme.onSecondaryContainer,
                    indicatorColor = if (overVideo) ComposeColor.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.secondaryContainer,
                    selectedTextColor = content,
                    unselectedIconColor = content.copy(alpha = 0.7f),
                    unselectedTextColor = content.copy(alpha = 0.7f),
                ),
            )
        }
    }
}

/** TikTok-style "+" button in the middle of the bottom bar. */
@Composable
private fun RowScope.UploadNavItem(onClick: () -> Unit, overVideo: Boolean) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (overVideo) ComposeColor.White else MaterialTheme.colorScheme.primary)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = stringResource(R.string.nav_upload),
                tint = if (overVideo) ComposeColor.Black else MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/** Height of the bottom navigation bar without the system navigation inset (compact, TikTok-like). */
private val NAV_BAR_HEIGHT = 72.dp
