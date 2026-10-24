package com.alo.downloader.ui.telegram

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alo.downloader.R
import com.alo.downloader.telegram.TelegramAuth
import com.alo.downloader.ui.home.openLink
import com.alo.downloader.ui.settings.SettingsPage

@Composable
fun TelegramLoginScreen(onBack: () -> Unit, viewModel: TelegramViewModel = hiltViewModel()) {
    val auth by viewModel.auth.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val context = LocalContext.current

    SettingsPage(title = stringResource(R.string.telegram_account), onBack = onBack) {
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                stringResource(R.string.telegram_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (val state = auth) {
                TelegramAuth.NotConfigured -> {
                    var apiId by rememberSaveable { mutableStateOf("") }
                    var apiHash by rememberSaveable { mutableStateOf("") }
                    TextButton(onClick = { openLink(context, "https://my.telegram.org/apps") }) {
                        Text(stringResource(R.string.telegram_get_api))
                    }
                    OutlinedTextField(
                        value = apiId, onValueChange = { apiId = it.filter(Char::isDigit) },
                        label = { Text("API ID") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = apiHash, onValueChange = { apiHash = it.trim() },
                        label = { Text("API hash") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { viewModel.configure(apiId, apiHash) },
                        enabled = !busy && apiId.isNotBlank() && apiHash.length >= 16,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.telegram_continue)) }
                }
                TelegramAuth.Starting -> CircularProgressIndicator()
                TelegramAuth.WaitPhone -> InputStep(
                    label = stringResource(R.string.telegram_phone),
                    hint = "+1 555 123 4567",
                    keyboard = KeyboardType.Phone,
                    busy = busy,
                    onSubmit = viewModel::sendPhone,
                )
                is TelegramAuth.WaitCode -> InputStep(
                    label = stringResource(R.string.telegram_code, state.phone ?: ""),
                    hint = "12345",
                    keyboard = KeyboardType.Number,
                    busy = busy,
                    onSubmit = viewModel::sendCode,
                )
                is TelegramAuth.WaitPassword -> InputStep(
                    label = stringResource(R.string.telegram_password),
                    hint = state.hint ?: "",
                    keyboard = KeyboardType.Password,
                    password = true,
                    busy = busy,
                    onSubmit = viewModel::sendPassword,
                )
                is TelegramAuth.WaitOtherDevice -> Text(stringResource(R.string.telegram_other_device, state.link))
                TelegramAuth.Ready -> {
                    StatusCard(stringResource(R.string.telegram_logged_in))
                    OutlinedButton(onClick = viewModel::logOut, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.telegram_logout))
                    }
                }
                is TelegramAuth.Unsupported -> StatusCard(stringResource(R.string.telegram_unsupported_state, state.state))
                TelegramAuth.Closed -> StatusCard(stringResource(R.string.telegram_closed))
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (auth != TelegramAuth.NotConfigured) {
                TextButton(onClick = viewModel::reset, enabled = !busy) { Text(stringResource(R.string.telegram_reset)) }
            }
        }
    }
}

@Composable
private fun InputStep(
    label: String,
    hint: String,
    keyboard: KeyboardType,
    busy: Boolean,
    onSubmit: (String) -> Unit,
    password: Boolean = false,
) {
    var value by rememberSaveable(label) { mutableStateOf("") }
    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        label = { Text(label) },
        placeholder = { Text(hint) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { onSubmit(value) }, enabled = !busy && value.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.telegram_continue))
    }
}

@Composable
private fun StatusCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
