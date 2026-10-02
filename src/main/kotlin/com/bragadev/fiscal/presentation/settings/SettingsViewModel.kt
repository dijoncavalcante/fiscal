package com.bragadev.fiscal.presentation.settings

import com.bragadev.fiscal.domain.model.AppSettings
import com.bragadev.fiscal.domain.model.DuplicatePolicy
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.rules.EditablePeriodPolicy
import com.bragadev.fiscal.domain.usecase.ChangeMonthFolderUseCase
import com.bragadev.fiscal.domain.usecase.ChangeSourceFolderUseCase
import com.bragadev.fiscal.domain.usecase.ObserveSettingsUseCase
import com.bragadev.fiscal.domain.usecase.UpdateSettingsUseCase
import com.bragadev.fiscal.presentation.common.ViewModel
import com.bragadev.fiscal.presentation.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.file.Path

class SettingsViewModel(
    private val observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
    private val changeSourceFolder: ChangeSourceFolderUseCase,
    private val changeMonthFolder: ChangeMonthFolderUseCase,
    periodPolicy: EditablePeriodPolicy,
) : ViewModel() {
    private val state = MutableStateFlow(SettingsUiState(firstEditableMonth = periodPolicy.firstEditableMonth))
    val uiState: StateFlow<SettingsUiState> = state.asStateFlow()

    val currentSourceFolder: Path? get() = observeSettings().value.sourceFolder
    val currentMonthFolder: Path? get() = observeSettings().value.monthFolder

    init {
        scope.launch {
            observeSettings().collect { settings -> state.update { it.fromSettings(settings) } }
        }
    }

    fun onSourceFolderSelected(path: Path) = changeFolder { changeSourceFolder(path) }

    fun onMonthFolderSelected(path: Path) = changeFolder { changeMonthFolder(path) }

    fun onDuplicatePolicyChanged(policy: DuplicatePolicy) = update { it.copy(duplicatePolicy = policy) }

    fun onConfirmMoveChanged(enabled: Boolean) = update { it.copy(confirmBeforeMove = enabled) }

    fun onConfirmRenameChanged(enabled: Boolean) = update { it.copy(confirmBeforeRename = enabled) }

    private fun changeFolder(change: suspend () -> Outcome<*>) {
        scope.launch {
            val result = change()
            state.update { it.copy(error = (result as? Outcome.Failure)?.error?.toUserMessage()) }
        }
    }

    private fun update(transform: (AppSettings) -> AppSettings) {
        scope.launch { updateSettings(transform) }
    }

    private fun SettingsUiState.fromSettings(settings: AppSettings) = copy(
        sourceFolder = settings.sourceFolder?.toString().orEmpty(),
        monthFolder = settings.monthFolder?.toString().orEmpty(),
        duplicatePolicy = settings.duplicatePolicy,
        confirmBeforeMove = settings.confirmBeforeMove,
        confirmBeforeRename = settings.confirmBeforeRename,
    )
}
