package com.bragadev.fiscal.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
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
import com.bragadev.fiscal.domain.model.ThemeMode
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.components.AppIcons
import com.bragadev.fiscal.presentation.components.FolderPathField
import com.bragadev.fiscal.presentation.components.pickFolder
import org.koin.compose.koinInject

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit, onOpenOnboarding: () -> Unit) {
    val state by viewModel.uiState.collectAsState()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Icon(AppIcons.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(Strings.BACK, Modifier.padding(start = 6.dp))
            }
            Text(Strings.SETTINGS, style = MaterialTheme.typography.headlineSmall)
        }
        Column(Modifier.widthIn(max = 720.dp).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FoldersSection(state, viewModel, onOpenOnboarding)
            AppearanceSection(state.themeMode, viewModel::onThemeChanged)
            Section(Strings.SETTINGS_LOCK_SECTION) {
                CutoffMonthSection(state.firstEditableMonth, viewModel::onFirstEditableMonthChanged)
            }
            DuplicatesSection(state.duplicatePolicy, viewModel::onDuplicatePolicyChanged)
            ConfirmationSection(state, viewModel)
            Section(Strings.DATA_SECTION) { DataSafetySection(koinInject()) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun FoldersSection(state: SettingsUiState, viewModel: SettingsViewModel, onOpenOnboarding: () -> Unit) =
    Section(Strings.SETTINGS_FOLDERS_SECTION) {
        FolderPathField(
            label = Strings.SOURCE_FOLDER_LABEL,
            path = state.sourceFolder,
            emptyText = Strings.SOURCE_FOLDER_EMPTY,
            editDescription = Strings.SOURCE_FOLDER_EDIT,
            onEdit = {
                pickFolder(Strings.SOURCE_FOLDER_PICKER_TITLE, viewModel.currentSourceFolder)?.let(viewModel::onSourceFolderSelected)
            },
        )
        FolderPathField(
            label = Strings.MONTH_FOLDER_LABEL,
            path = state.monthFolder,
            emptyText = Strings.MONTH_FOLDER_EMPTY,
            editDescription = Strings.MONTH_FOLDER_EDIT,
            onEdit = {
                pickFolder(Strings.MONTH_FOLDER_PICKER_TITLE, viewModel.currentMonthFolder)?.let(viewModel::onMonthFolderSelected)
            },
            modifier = Modifier.padding(top = 12.dp),
        )
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onOpenOnboarding) { Text(Strings.OPEN_ONBOARDING) }
            Text(
                Strings.OPEN_ONBOARDING_NOTE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }

@Composable
private fun AppearanceSection(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) =
    Section(Strings.APPEARANCE_SECTION) {
        listOf(
            ThemeMode.SYSTEM to Strings.THEME_SYSTEM,
            ThemeMode.LIGHT to Strings.THEME_LIGHT,
            ThemeMode.DARK to Strings.THEME_DARK,
        ).forEach { (mode, label) ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = selected == mode, onClick = { onSelect(mode) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == mode, onClick = { onSelect(mode) })
                Text(label)
            }
        }
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
