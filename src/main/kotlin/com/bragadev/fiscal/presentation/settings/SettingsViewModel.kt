package com.bragadev.fiscal.presentation.settings

import com.bragadev.fiscal.domain.model.AppSettings
import com.bragadev.fiscal.domain.model.DuplicatePolicy
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.usecase.ChangeRootFolderUseCase
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
    private val changeRootFolder: ChangeRootFolderUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = state.asStateFlow()

    val currentRoot: Path? get() = observeSettings().value.rootPath

    init {
        scope.launch {
            observeSettings().collect { settings -> state.update { it.fromSettings(settings) } }
        }
    }

    fun onRootSelected(path: Path) {
        scope.launch {
            val result = changeRootFolder(path)
            if (result is Outcome.Failure) state.update { it.copy(error = result.error.toUserMessage()) }
        }
    }

    fun onDuplicatePolicyChanged(policy: DuplicatePolicy) = update { it.copy(duplicatePolicy = policy) }

    fun onConfirmMoveChanged(enabled: Boolean) = update { it.copy(confirmBeforeMove = enabled) }

    fun onConfirmRenameChanged(enabled: Boolean) = update { it.copy(confirmBeforeRename = enabled) }

    fun onErrorShown() {
        state.update { it.copy(error = null) }
    }

    private fun update(transform: (AppSettings) -> AppSettings) {
        scope.launch { updateSettings(transform) }
    }

    private fun SettingsUiState.fromSettings(settings: AppSettings) = copy(
        rootPath = settings.rootPath?.toString().orEmpty(),
        duplicatePolicy = settings.duplicatePolicy,
        confirmBeforeMove = settings.confirmBeforeMove,
        confirmBeforeRename = settings.confirmBeforeRename,
    )
}
