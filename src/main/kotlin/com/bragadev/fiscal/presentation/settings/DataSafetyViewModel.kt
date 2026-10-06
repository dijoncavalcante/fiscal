package com.bragadev.fiscal.presentation.settings

import com.bragadev.fiscal.domain.model.BackupStats
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.repository.DiagnosticsRepository
import com.bragadev.fiscal.domain.usecase.CleanOldBackupsUseCase
import com.bragadev.fiscal.domain.usecase.ExportDataUseCase
import com.bragadev.fiscal.domain.usecase.GetBackupStatsUseCase
import com.bragadev.fiscal.domain.usecase.ImportDataUseCase
import com.bragadev.fiscal.domain.usecase.ObserveSettingsUseCase
import com.bragadev.fiscal.domain.usecase.UpdateSettingsUseCase
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.common.ViewModel
import com.bragadev.fiscal.presentation.common.toUserMessage
import com.bragadev.fiscal.presentation.components.DesktopActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.file.Path

/** Pedido que precisa de confirmação antes de executar. */
sealed interface DataSafetyConfirmation {
    data object CleanBackups : DataSafetyConfirmation
    data class Import(val source: Path) : DataSafetyConfirmation
}

data class DataSafetyUiState(
    val backupStats: BackupStats? = null,
    val autoCleanBackups: Boolean = false,
    val backupRetentionDays: Int = 90,
    val confirmation: DataSafetyConfirmation? = null,
    val message: String? = null,
    val isError: Boolean = false,
)

/** Configurações → "Dados e segurança": backups, exportar/importar dados e diagnóstico. */
class DataSafetyViewModel(
    private val observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
    private val getBackupStats: GetBackupStatsUseCase,
    private val cleanOldBackups: CleanOldBackupsUseCase,
    private val exportData: ExportDataUseCase,
    private val importData: ImportDataUseCase,
    private val diagnostics: DiagnosticsRepository,
) : ViewModel() {
    private val state = MutableStateFlow(DataSafetyUiState())
    val uiState: StateFlow<DataSafetyUiState> = state.asStateFlow()

    val logFolder: Path? get() = diagnostics.logFolder

    init {
        scope.launch {
            observeSettings().collect { settings ->
                state.update { it.copy(autoCleanBackups = settings.autoCleanBackups, backupRetentionDays = settings.backupRetentionDays) }
            }
        }
        refreshStats()
    }

    fun refreshStats() {
        scope.launch { state.update { it.copy(backupStats = getBackupStats()) } }
    }

    fun onAutoCleanChanged(enabled: Boolean) {
        scope.launch { updateSettings { it.copy(autoCleanBackups = enabled) } }
    }

    fun onRetentionChanged(days: Int) {
        scope.launch { updateSettings { it.copy(backupRetentionDays = days) } }
    }

    fun onCleanBackupsRequested() = confirm(DataSafetyConfirmation.CleanBackups)

    fun onImportSelected(source: Path) = confirm(DataSafetyConfirmation.Import(source))

    fun onConfirmed() {
        val pending = state.value.confirmation ?: return
        state.update { it.copy(confirmation = null) }
        scope.launch {
            when (pending) {
                DataSafetyConfirmation.CleanBackups -> {
                    val deleted = cleanOldBackups.now()
                    showMessage(Strings.backupsCleaned(deleted))
                    refreshStats()
                }
                is DataSafetyConfirmation.Import -> when (val result = importData(pending.source)) {
                    is Outcome.Success -> showMessage(Strings.IMPORT_READY)
                    is Outcome.Failure -> showMessage(result.error.toUserMessage(), isError = true)
                }
            }
        }
    }

    fun onDismissConfirmation() {
        state.update { it.copy(confirmation = null) }
    }

    fun onExport(target: Path) {
        scope.launch {
            when (val result = exportData(target)) {
                is Outcome.Success -> showMessage(Strings.dataExported(target.fileName.toString()))
                is Outcome.Failure -> showMessage(result.error.toUserMessage(), isError = true)
            }
        }
    }

    fun onCopyDiagnostics() {
        DesktopActions.copyToClipboard(diagnostics.report(error = null))
        showMessage(Strings.DIAGNOSTICS_COPIED)
    }

    fun onOpenLogs() {
        diagnostics.logFolder?.let(DesktopActions::openFolder)
    }

    private fun confirm(confirmation: DataSafetyConfirmation) {
        state.update { it.copy(confirmation = confirmation, message = null) }
    }

    private fun showMessage(text: String, isError: Boolean = false) {
        state.update { it.copy(message = text, isError = isError) }
    }
}
