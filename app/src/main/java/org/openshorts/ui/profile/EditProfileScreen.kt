package org.openshorts.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.openshorts.R
import org.openshorts.data.UserProfile
import org.openshorts.data.normalizeSupportUrl
import org.openshorts.data.sanitizeUsername
import org.openshorts.ui.components.Avatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onDone: () -> Unit,
    viewModel: ProfileViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val imageError by viewModel.imageError.collectAsStateWithLifecycle()
    val profile = state.profile

    var initialized by rememberSaveable { mutableStateOf(false) }
    var displayName by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var bio by rememberSaveable { mutableStateOf("") }
    var supportUrl by rememberSaveable { mutableStateOf("") }
    var supporterBadge by rememberSaveable { mutableStateOf(false) }
    var bannerHue by rememberSaveable { mutableFloatStateOf(UserProfile().bannerHue) }
    var supportUrlInvalid by rememberSaveable { mutableStateOf(false) }

    // Fill the form once the stored profile has loaded.
    LaunchedEffect(state.loaded) {
        if (state.loaded && !initialized) {
            displayName = profile.displayName
            username = profile.username
            bio = profile.bio
            supportUrl = profile.supportUrl
            supporterBadge = profile.showSupporterBadge
            bannerHue = profile.bannerHue
            initialized = true
        }
    }

    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::importAvatar)
    }
    val pickBanner = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::importBanner)
    }
    val imagesOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    fun save() {
        val url = normalizeSupportUrl(supportUrl)
        if (url == null) {
            supportUrlInvalid = true
            return
        }
        viewModel.save(
            profile.copy(
                displayName = displayName,
                username = username,
                bio = bio,
                supportUrl = url,
                showSupporterBadge = supporterBadge,
                bannerHue = bannerHue,
            ),
        )
        onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_profile)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = { TextButton(onClick = ::save) { Text(stringResource(R.string.save)) } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            // Banner + avatar, both tappable.
            Box(Modifier.fillMaxWidth().height(210.dp)) {
                ProfileBanner(
                    bannerPath = profile.bannerPath,
                    hue = bannerHue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clickable { pickBanner.launch(imagesOnly) },
                )
                CameraBadge(Modifier.align(Alignment.TopEnd).padding(12.dp))
                Box(Modifier.align(Alignment.BottomCenter)) {
                    Avatar(
                        source = profile.avatarPath,
                        name = displayName.ifBlank { "?" },
                        size = 96.dp,
                        modifier = Modifier
                            .border(4.dp, MaterialTheme.colorScheme.background, CircleShape)
                            .clickable { pickAvatar.launch(imagesOnly) },
                    )
                    CameraBadge(Modifier.align(Alignment.BottomEnd))
                }
            }
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = { pickAvatar.launch(imagesOnly) }) { Text(stringResource(R.string.change_avatar)) }
                TextButton(onClick = { pickBanner.launch(imagesOnly) }) { Text(stringResource(R.string.change_banner)) }
            }
            if (profile.avatarPath != null || profile.bannerPath != null) {
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    if (profile.avatarPath != null) {
                        TextButton(onClick = viewModel::removeAvatar) { Text(stringResource(R.string.remove_avatar)) }
                    }
                    if (profile.bannerPath != null) {
                        TextButton(onClick = viewModel::removeBanner) { Text(stringResource(R.string.remove_banner)) }
                    }
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (profile.bannerPath == null) {
                    Text(stringResource(R.string.banner_color), style = MaterialTheme.typography.bodyLarge)
                    Slider(value = bannerHue, onValueChange = { bannerHue = it }, valueRange = 0f..360f)
                }
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it.take(UserProfile.MAX_NAME_LENGTH) },
                    label = { Text(stringResource(R.string.profile_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = sanitizeUsername(it) },
                    label = { Text(stringResource(R.string.profile_username)) },
                    prefix = { Text("@") },
                    singleLine = true,
                    supportingText = { Text(stringResource(R.string.profile_username_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it.take(UserProfile.MAX_BIO_LENGTH) },
                    label = { Text(stringResource(R.string.profile_bio)) },
                    minLines = 3,
                    supportingText = { Text("${bio.length}/${UserProfile.MAX_BIO_LENGTH}") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    stringResource(R.string.section_support_settings),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = supportUrl,
                    onValueChange = {
                        supportUrl = it
                        supportUrlInvalid = false
                    },
                    label = { Text(stringResource(R.string.profile_support_url)) },
                    placeholder = { Text("https://buymeacoffee.com/…") },
                    singleLine = true,
                    isError = supportUrlInvalid,
                    supportingText = {
                        Text(
                            stringResource(
                                if (supportUrlInvalid) R.string.profile_support_url_invalid else R.string.profile_support_url_hint,
                            ),
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.supporter_badge), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.supporter_badge_summary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Switch(checked = supporterBadge, onCheckedChange = { supporterBadge = it })
                }
                Text(
                    stringResource(R.string.profile_local_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
    }

    if (imageError) {
        AlertDialog(
            onDismissRequest = viewModel::dismissImageError,
            confirmButton = { TextButton(onClick = viewModel::dismissImageError) { Text(stringResource(R.string.ok)) } },
            text = { Text(stringResource(R.string.image_import_error)) },
        )
    }
}

@Composable
private fun CameraBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(30.dp)
            .background(Color.Black.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}
