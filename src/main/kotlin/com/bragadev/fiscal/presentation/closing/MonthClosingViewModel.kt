package com.bragadev.fiscal.presentation.closing

import com.bragadev.fiscal.domain.model.MonthReview
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.usecase.GenerateMonthReportUseCase
import com.bragadev.fiscal.domain.usecase.ObserveSettingsUseCase
import com.bragadev.fiscal.domain.usecase.ReviewMonthUseCase
import com.bragadev.fiscal.presentation.common.DocumentChangeNotifier
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

/** Janela "Concluir mês": a conferência e onde salvar o relatório. */
data class MonthClosingDialog(
    val review: MonthReview,
    val outputFolder: Path?,
    val outputName: String,
    val isWorking: Boolean = false,
    /** Relatório já gravado: a janela passa a oferecer "Abrir relatório". */
    val created: Path? = null,
    val error: String? = null,
)

data class MonthClosingUiState(val dialog: MonthClosingDialog? = null, val error: String? = null)

/**
 * "Concluir mês": confere a pasta do mês (faltando, pendências) e gera o relatório em PDF.
 * O relatório vai, por padrão, para a pasta de origem (não para a pasta do mês, para não virar
 * um "arquivo sem número de categoria" na prestação de contas).
 */
class MonthClosingViewModel(
    private val reviewMonth: ReviewMonthUseCase,
    private val generateReport: GenerateMonthReportUseCase,
    private val observeSettings: ObserveSettingsUseCase,
    private val documentChanges: DocumentChangeNotifier,
) : ViewModel() {
    private val state = MutableStateFlow(MonthClosingUiState())
    val uiState: StateFlow<MonthClosingUiState> = state.asStateFlow()

    fun onOpen(monthFolder: Path) {
        scope.launch {
            when (val result = reviewMonth(monthFolder)) {
                is Outcome.Success -> state.update {
                    val review = result.value
                    MonthClosingUiState(
                        dialog = MonthClosingDialog(
                            review = review,
                            outputFolder = observeSettings().value.sourceFolder,
                            outputName = Strings.reportFileName(review.month, review.account?.displayName),
                        ),
                    )
                }
                is Outcome.Failure -> state.update { it.copy(error = result.error.toUserMessage()) }
            }
        }
    }

    fun onOutputFolderChanged(folder: Path) = edit { it.copy(outputFolder = folder, error = null) }

    fun onOutputNameChanged(name: String) = edit { it.copy(outputName = name, error = null) }

    fun onGenerate() {
        val dialog = state.value.dialog ?: return
        if (dialog.isWorking || dialog.created != null) return
        val folder = dialog.outputFolder ?: return edit { it.copy(error = Strings.REPORT_CHOOSE_FOLDER) }
        edit { it.copy(isWorking = true, error = null) }
        scope.launch {
            when (val result = generateReport(dialog.review, folder, dialog.outputName)) {
                is Outcome.Success -> {
                    edit { it.copy(isWorking = false, created = result.value) }
                    // O relatório aparece (e fica selecionado) na lista da esquerda, se foi salvo lá.
                    documentChanges.notifyChanged(selectPath = result.value)
                }
                is Outcome.Failure -> edit { it.copy(isWorking = false, error = result.error.toUserMessage()) }
            }
        }
    }

    fun onOpenReport() {
        state.value.dialog?.created?.let(DesktopActions::openFile)
    }

    fun onDismiss() {
        state.update { MonthClosingUiState() }
    }

    fun onErrorShown() {
        state.update { it.copy(error = null) }
    }

    private fun edit(transform: (MonthClosingDialog) -> MonthClosingDialog) {
        state.update { current -> current.copy(dialog = current.dialog?.let(transform)) }
    }
}
