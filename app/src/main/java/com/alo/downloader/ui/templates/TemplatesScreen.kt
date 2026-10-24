package com.alo.downloader.ui.templates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alo.downloader.R
import com.alo.downloader.core.engine.CommandLine
import com.alo.downloader.core.model.EngineType
import com.alo.downloader.data.db.CommandTemplateEntity
import com.alo.downloader.ui.common.EmptyState
import com.alo.downloader.ui.settings.SettingsPage

@Composable
fun TemplatesScreen(onBack: () -> Unit, viewModel: TemplatesViewModel = hiltViewModel()) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val selected by viewModel.selectedId.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<CommandTemplateEntity?>(null) }

    SettingsPage(
        title = stringResource(R.string.settings_templates),
        onBack = onBack,
        actions = {
            IconButton(onClick = { editing = CommandTemplateEntity(name = "", engine = EngineType.YT_DLP.id, arguments = "") }) {
                Icon(Icons.Outlined.Add, stringResource(R.string.template_add))
            }
        },
    ) {
        Text(
            stringResource(R.string.templates_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        ListItem(
            modifier = Modifier.clickable { viewModel.select(-1) }.padding(horizontal = 8.dp),
            leadingContent = { RadioButton(selected = selected < 0, onClick = { viewModel.select(-1) }) },
            headlineContent = { Text(stringResource(R.string.template_none)) },
            supportingContent = { Text(stringResource(R.string.template_none_summary)) },
        )
        if (templates.isEmpty()) {
            EmptyState(Icons.Outlined.Terminal, stringResource(R.string.templates_empty_title), stringResource(R.string.templates_empty_body))
        }
        templates.forEach { t ->
            ListItem(
                modifier = Modifier.clickable { editing = t }.padding(horizontal = 8.dp),
                leadingContent = { RadioButton(selected = selected == t.id, onClick = { viewModel.select(t.id) }) },
                headlineContent = { Text(t.name) },
                supportingContent = {
                    Text(
                        "${EngineType.fromId(t.engine)?.displayName ?: t.engine} · ${t.arguments}",
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingContent = {
                    IconButton(onClick = { viewModel.delete(t.id) }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
                },
            )
        }
    }

    editing?.let { template ->
        TemplateEditor(
            initial = template,
            onDismiss = { editing = null },
            onSave = { viewModel.save(it); editing = null },
        )
    }
}

@Composable
private fun TemplateEditor(initial: CommandTemplateEntity, onDismiss: () -> Unit, onSave: (CommandTemplateEntity) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var args by remember { mutableStateOf(initial.arguments) }
    var engine by remember { mutableStateOf(EngineType.fromId(initial.engine) ?: EngineType.YT_DLP) }
    var replace by remember { mutableStateOf(initial.replaceDefaults) }
    val parseError = runCatching { CommandLine.split(args) }.exceptionOrNull()?.message

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial.id == 0L) R.string.template_add else R.string.template_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.template_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(EngineType.YT_DLP, EngineType.GALLERY_DL).forEach { e ->
                        FilterChip(selected = engine == e, onClick = { engine = e }, label = { Text(e.displayName) })
                    }
                }
                OutlinedTextField(
                    value = args,
                    onValueChange = { args = it },
                    label = { Text(stringResource(R.string.template_arguments)) },
                    placeholder = { Text("-f \"bv*[height<=720]+ba\" --embed-chapters", fontFamily = FontFamily.Monospace) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    minLines = 3,
                    isError = parseError != null,
                    supportingText = { Text(parseError ?: stringResource(R.string.template_arguments_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.template_replace), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.template_replace_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = replace, onCheckedChange = { replace = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && args.isNotBlank() && parseError == null,
                onClick = { onSave(initial.copy(name = name.trim(), arguments = args.trim(), engine = engine.id, replaceDefaults = replace)) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
