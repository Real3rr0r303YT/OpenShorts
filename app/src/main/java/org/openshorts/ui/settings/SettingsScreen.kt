package org.openshorts.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.openshorts.BuildConfig
import org.openshorts.R
import org.openshorts.data.AppSettings
import org.openshorts.data.ColorPalette
import org.openshorts.data.FontChoice
import org.openshorts.data.ThemeMode
import org.openshorts.data.YouTubeInstanceType
import org.openshorts.data.PRESET_INSTANCES
import org.openshorts.ui.account.AccountSummary
import org.openshorts.ui.account.AccountViewModel
import org.openshorts.ui.account.SyncState
import org.openshorts.ui.theme.colorScheme
import org.openshorts.ui.theme.fontFamilyFor
import org.openshorts.ui.theme.supportsDynamicColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onOpenLogin: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
    accountViewModel: AccountViewModel = viewModel(),
) {
    val account by accountViewModel.account.collectAsStateWithLifecycle()
    val syncState by accountViewModel.sync.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val fontError by viewModel.fontError.collectAsStateWithLifecycle()
    val pickFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importFont)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings)) }) },
        // The bottom navigation bar already handles the system bar insets.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DonationCard(onDonate = { uriHandler.openUri(BuildConfig.DONATION_URL) })

            SectionHeader(R.string.section_account)
            val current = account
            if (current == null) {
                Text(
                    stringResource(R.string.account_logged_out),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onOpenLogin, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.login_button))
                }
            } else {
                AccountSummary(
                    displayName = current.displayName,
                    handle = current.handle,
                    avatarUrl = current.avatarUrl,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = accountViewModel::sync, enabled = syncState != SyncState.Running) {
                        Text(stringResource(if (syncState == SyncState.Running) R.string.sync_running else R.string.sync))
                    }
                    TextButton(onClick = accountViewModel::logout) { Text(stringResource(R.string.logout)) }
                }
                Text(
                    stringResource(R.string.account_sync_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionHeader(R.string.section_appearance)

            SettingLabel(R.string.theme_mode)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                    ) { Text(stringResource(mode.labelRes)) }
                }
            }

            SwitchRow(
                title = R.string.dynamic_color,
                summary = if (supportsDynamicColor) R.string.dynamic_color_summary else R.string.dynamic_color_unsupported,
                checked = settings.dynamicColor && supportsDynamicColor,
                enabled = supportsDynamicColor,
                onCheckedChange = viewModel::setDynamicColor,
            )
            SwitchRow(
                title = R.string.pure_black,
                summary = R.string.pure_black_summary,
                checked = settings.pureBlack,
                onCheckedChange = viewModel::setPureBlack,
            )

            SettingLabel(R.string.color_scheme)
            val paletteEnabled = !(settings.dynamicColor && supportsDynamicColor)
            PalettePicker(
                selected = settings.palette,
                customHue = settings.customHue,
                enabled = paletteEnabled,
                dark = settings.isDark(isSystemInDarkTheme()),
                onSelect = viewModel::setPalette,
            )
            if (settings.palette == ColorPalette.CUSTOM && paletteEnabled) {
                SettingLabel(R.string.accent_color)
                AccentHueSlider(hue = settings.customHue, onHueChange = viewModel::setCustomHue)
            }

            SettingLabel(R.string.font)
            FontChoice.entries.filter { it != FontChoice.IMPORTED }.forEach { font ->
                RadioRow(
                    selected = settings.font == font,
                    onClick = { viewModel.setFont(font) },
                ) {
                    Text(
                        text = stringResource(font.labelRes),
                        fontFamily = fontFamilyFor(font),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            if (settings.importedFontPath != null) {
                RadioRow(
                    selected = settings.font == FontChoice.IMPORTED,
                    onClick = { viewModel.setFont(FontChoice.IMPORTED) },
                ) {
                    Text(
                        text = settings.importedFontName ?: stringResource(R.string.font_imported),
                        fontFamily = fontFamilyFor(FontChoice.IMPORTED, settings.importedFontPath),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            OutlinedButton(
                onClick = {
                    pickFont.launch(
                        arrayOf("font/ttf", "font/otf", "font/sfnt", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream"),
                    )
                },
            ) {
                Icon(Icons.Filled.FontDownload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.font_import))
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader(R.string.section_feed)
            SettingLabel(R.string.instance_presets)
            InstancePresets(current = settings.instanceUrl, onSelect = { viewModel.setInstanceUrl(it) })
            InstanceField(currentUrl = settings.instanceUrl, onSave = viewModel::setInstanceUrl)
            SwitchRow(
                title = R.string.shorts_only,
                summary = R.string.shorts_only_summary,
                checked = settings.shortsOnly,
                onCheckedChange = viewModel::setShortsOnly,
            )
            SwitchRow(
                title = R.string.loop_videos,
                summary = R.string.loop_videos_summary,
                checked = settings.loopVideos,
                onCheckedChange = viewModel::setLoopVideos,
            )
            SwitchRow(
                title = R.string.youtube_enabled,
                summary = R.string.youtube_enabled_summary,
                checked = settings.youTubeEnabled,
                onCheckedChange = viewModel::setYouTubeEnabled,
            )
            if (settings.youTubeEnabled) {
                YouTubeInstanceField(
                    currentUrl = settings.youTubeCustomInstance.orEmpty(),
                    currentType = settings.youTubeCustomType,
                    onSave = viewModel::setYouTubeCustomInstance,
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader(R.string.section_about)
            Text(stringResource(R.string.about_privacy), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.about_license), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { uriHandler.openUri(BuildConfig.SOURCE_URL) }) {
                Icon(Icons.Filled.Code, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.source_code))
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (fontError) {
        AlertDialog(
            onDismissRequest = viewModel::dismissFontError,
            confirmButton = { TextButton(onClick = viewModel::dismissFontError) { Text(stringResource(R.string.ok)) } },
            text = { Text(stringResource(R.string.font_import_error)) },
        )
    }
}

/** Prominent "Support OpenShorts" card linking to the project's donation page. */
@Composable
private fun DonationCard(onDonate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.donate_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.donate_body), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onDonate,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(Icons.Filled.LocalCafe, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.donate_button), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InstancePresets(current: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PRESET_INSTANCES.forEach { preset ->
            FilterChip(
                selected = preset.url == current,
                onClick = { onSelect(preset.url) },
                label = { Text(preset.name) },
                leadingIcon = if (preset.isTech) {
                    { Icon(Icons.Filled.Terminal, contentDescription = stringResource(R.string.instance_tech), modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
            )
        }
    }
}

/** Hue slider drawn over the full color wheel, for the custom accent color. */
@Composable
private fun AccentHueSlider(hue: Float, onHueChange: (Float) -> Unit) {
    var value by remember(hue) { mutableFloatStateOf(hue) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(CircleShape)
            .background(Brush.horizontalGradient((0..360 step 30).map { Color.hsl(it.toFloat(), 0.75f, 0.5f) })),
    )
    Slider(
        value = value,
        onValueChange = { value = it },
        onValueChangeFinished = { onHueChange(value) },
        valueRange = 0f..360f,
        colors = SliderDefaults.colors(
            thumbColor = Color.hsl(value, 0.75f, 0.5f),
            activeTrackColor = Color.hsl(value, 0.75f, 0.5f),
        ),
    )
}

@Composable
private fun PalettePicker(
    selected: ColorPalette,
    customHue: Float,
    enabled: Boolean,
    dark: Boolean,
    onSelect: (ColorPalette) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .alpha(if (enabled) 1f else 0.4f),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ColorPalette.entries.forEach { palette ->
            val scheme = remember(palette, dark, customHue) { palette.colorScheme(dark, customHue) }
            val isSelected = palette == selected
            val label = stringResource(palette.labelRes)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.selectable(
                    selected = isSelected,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = { onSelect(palette) },
                ),
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(scheme.primary)
                        .border(
                            BorderStroke(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else scheme.outline,
                            ),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(scheme.tertiary),
                    )
                    if (isSelected) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = scheme.onTertiary, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun InstanceField(currentUrl: String, onSave: (String) -> Boolean) {
    var text by rememberSaveable(currentUrl) { mutableStateOf(currentUrl) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val save = {
        invalid = !onSave(text)
        if (!invalid) focusManager.clearFocus()
    }

    Column {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                invalid = false
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.instance)) },
            placeholder = { Text(BuildConfig.DEFAULT_INSTANCE) },
            singleLine = true,
            isError = invalid,
            supportingText = {
                Text(stringResource(if (invalid) R.string.instance_invalid else R.string.instance_summary))
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { text = BuildConfig.DEFAULT_INSTANCE; save() }) {
                Text(stringResource(R.string.reset))
            }
            Button(onClick = save, enabled = text.trim() != currentUrl) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

/** The user's own Piped/Invidious instance, tried before the public ones. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YouTubeInstanceField(
    currentUrl: String,
    currentType: YouTubeInstanceType,
    onSave: (String, YouTubeInstanceType) -> Boolean,
) {
    var text by rememberSaveable(currentUrl) { mutableStateOf(currentUrl) }
    var type by rememberSaveable(currentType) { mutableStateOf(currentType) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val save = {
        invalid = !onSave(text, type)
        if (!invalid) focusManager.clearFocus()
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingLabel(R.string.youtube_instance)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            YouTubeInstanceType.entries.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = type == entry,
                    onClick = { type = entry },
                    shape = SegmentedButtonDefaults.itemShape(index, YouTubeInstanceType.entries.size),
                ) { Text(if (entry == YouTubeInstanceType.PIPED) "Piped" else "Invidious") }
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it.trim()
                invalid = false
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.youtube_instance_url)) },
            placeholder = { Text(if (type == YouTubeInstanceType.PIPED) "https://pipedapi.example.org" else "https://invidious.example.org") },
            singleLine = true,
            isError = invalid,
            supportingText = {
                Text(stringResource(if (invalid) R.string.instance_invalid else R.string.youtube_instance_hint))
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (currentUrl.isNotEmpty()) {
                TextButton(onClick = {
                    text = ""
                    save()
                }) { Text(stringResource(R.string.reset)) }
            }
            Button(onClick = save, enabled = text != currentUrl || type != currentType) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

@Composable
private fun SectionHeader(@StringRes title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingLabel(@StringRes title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SwitchRow(
    @StringRes title: Int,
    @StringRes summary: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun RadioRow(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        content()
    }
}

@get:StringRes
private val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }

@get:StringRes
private val ColorPalette.labelRes: Int
    get() = when (this) {
        ColorPalette.OCEAN -> R.string.palette_ocean
        ColorPalette.FOREST -> R.string.palette_forest
        ColorPalette.SUNSET -> R.string.palette_sunset
        ColorPalette.GRAPE -> R.string.palette_grape
        ColorPalette.MONOCHROME -> R.string.palette_monochrome
        ColorPalette.CUSTOM -> R.string.palette_custom
    }

@get:StringRes
private val FontChoice.labelRes: Int
    get() = when (this) {
        FontChoice.SYSTEM -> R.string.font_system
        FontChoice.POPPINS -> R.string.font_poppins
        FontChoice.ATKINSON_HYPERLEGIBLE -> R.string.font_atkinson
        FontChoice.SPACE_MONO -> R.string.font_space_mono
        FontChoice.PACIFICO -> R.string.font_pacifico
        FontChoice.SERIF -> R.string.font_serif
        FontChoice.IMPORTED -> R.string.font_imported
    }
