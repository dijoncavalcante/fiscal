package com.bragadev.fiscal.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.domain.model.DuplicatePolicy
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.components.pickFolder

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(Strings.BACK) }
            Text(Strings.SETTINGS, style = MaterialTheme.typography.headlineSmall)
        }
        Column(Modifier.widthIn(max = 720.dp).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            RootFolderSection(state.rootPath) { pickFolder(viewModel.currentRoot)?.let(viewModel::onRootSelected) }
            DuplicatesSection(state.duplicatePolicy, viewModel::onDuplicatePolicyChanged)
            ConfirmationSection(state, viewModel)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun RootFolderSection(rootPath: String, onChangeFolder: () -> Unit) = Section(Strings.SETTINGS_ROOT_SECTION) {
    Text(Strings.SETTINGS_CURRENT_FOLDER, style = MaterialTheme.typography.labelMedium)
    Text(rootPath.ifBlank { Strings.SETTINGS_NO_FOLDER }, style = MaterialTheme.typography.bodyLarge)
    OutlinedButton(onClick = onChangeFolder, modifier = Modifier.padding(top = 8.dp)) { Text(Strings.SETTINGS_CHANGE_FOLDER) }
}

@Composable
private fun DuplicatesSection(selected: DuplicatePolicy, onSelect: (DuplicatePolicy) -> Unit) =
    Section(Strings.SETTINGS_DUPLICATES_SECTION) {
        listOf(
            DuplicatePolicy.ASK to Strings.SETTINGS_DUPLICATE_ASK,
            DuplicatePolicy.AUTO_NUMBERED_COPY to Strings.SETTINGS_DUPLICATE_AUTO,
            DuplicatePolicy.FORBID to Strings.SETTINGS_DUPLICATE_FORBID,
        ).forEach { (policy, label) ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = selected == policy, onClick = { onSelect(policy) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == policy, onClick = { onSelect(policy) })
                Text(label)
            }
        }
    }

@Composable
private fun ConfirmationSection(state: SettingsUiState, viewModel: SettingsViewModel) =
    Section(Strings.SETTINGS_CONFIRM_SECTION) {
        CheckboxRow(Strings.SETTINGS_CONFIRM_MOVE, state.confirmBeforeMove, viewModel::onConfirmMoveChanged)
        CheckboxRow(Strings.SETTINGS_CONFIRM_RENAME, state.confirmBeforeRename, viewModel::onConfirmRenameChanged)
        Text(
            Strings.SETTINGS_CONFIRM_NOTE,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

@Composable
private fun CheckboxRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Column(Modifier.padding(top = 8.dp)) { content() }
        }
    }
}
