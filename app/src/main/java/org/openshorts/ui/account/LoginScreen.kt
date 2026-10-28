package org.openshorts.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.openshorts.R
import org.openshorts.data.PRESET_INSTANCES
import org.openshorts.data.normalizeInstanceUrl
import org.openshorts.ui.components.Avatar

/** PeerTube login (OAuth2 password grant) for any instance. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LoginScreen(
    defaultInstance: String,
    onDone: () -> Unit,
    viewModel: AccountViewModel = viewModel(),
) {
    val state by viewModel.login.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    var instance by rememberSaveable { mutableStateOf(defaultInstance) }
    var username by rememberSaveable { mutableStateOf("") }
    // Kept only in memory while the screen is open; never stored or saved to instance state.
    var password by remember { mutableStateOf("") }
    var otp by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    val close = {
        viewModel.resetLoginForm()
        onDone()
    }
    LaunchedEffect(state.success) { if (state.success) close() }

    val instanceValid = normalizeInstanceUrl(instance) != null
    val canSubmit = instanceValid && username.isNotBlank() && password.isNotEmpty() && !state.loading &&
        (!state.needsTwoFactor || otp.isNotBlank())
    val submit = { if (canSubmit) viewModel.login(instance, username, password, otp.ifBlank { null }) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.login_title)) },
                navigationIcon = {
                    IconButton(onClick = close) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.login_intro), style = MaterialTheme.typography.bodyMedium)

            OutlinedTextField(
                value = instance,
                onValueChange = { instance = it.trim() },
                label = { Text(stringResource(R.string.login_server)) },
                singleLine = true,
                isError = instance.isNotBlank() && !instanceValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PRESET_INSTANCES.forEach { preset ->
                    FilterChip(
                        selected = normalizeInstanceUrl(instance) == preset.url,
                        onClick = { instance = preset.url },
                        label = { Text(preset.name) },
                    )
                }
            }
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.login_username)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.login_password)) },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(R.string.login_show_password),
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (state.needsTwoFactor) ImeAction.Next else ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.needsTwoFactor) {
                OutlinedTextField(
                    value = otp,
                    onValueChange = { otp = it.filter(Char::isDigit).take(8) },
                    label = { Text(stringResource(R.string.login_otp)) },
                    supportingText = { Text(stringResource(R.string.login_otp_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            when {
                state.invalidCredentials -> ErrorText(stringResource(R.string.login_invalid))
                state.error != null -> ErrorText(stringResource(R.string.login_failed, state.error.orEmpty()))
            }
            Button(onClick = { submit() }, enabled = canSubmit, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.login_button))
                }
            }
            if (instanceValid) {
                TextButton(onClick = { normalizeInstanceUrl(instance)?.let { uriHandler.openUri("$it/signup") } }) {
                    Text(stringResource(R.string.login_signup))
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(Modifier.padding(16.dp)) {
                    Icon(Icons.Filled.Lock, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.login_privacy), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

/** Small account card used in settings and profile. Exposed so both look the same. */
@Composable
fun AccountSummary(
    displayName: String,
    handle: String,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Avatar(source = avatarUrl, name = displayName, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(displayName, style = MaterialTheme.typography.titleMedium)
            Text("@$handle", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}
