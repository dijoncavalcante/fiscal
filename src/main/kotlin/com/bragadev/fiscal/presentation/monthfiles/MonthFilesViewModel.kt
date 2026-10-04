package com.bragadev.fiscal.presentation.monthfiles

import com.bragadev.fiscal.domain.model.DocumentCategory
import com.bragadev.fiscal.domain.model.DuplicatePolicy
import com.bragadev.fiscal.domain.model.DuplicateResolution
import com.bragadev.fiscal.domain.model.FileOperation
import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.NamingRule
import com.bragadev.fiscal.domain.model.OrganizationPlan
import com.bragadev.fiscal.domain.model.OrganizeMode
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.rules.DescribedSequenceNaming
import com.bragadev.fiscal.domain.rules.FileNameRules
import com.bragadev.fiscal.domain.usecase.FileFlagUseCase
import com.bragadev.fiscal.domain.usecase.ObserveSettingsUseCase
import com.bragadev.fiscal.domain.usecase.OrganizeDocumentUseCase
import com.bragadev.fiscal.domain.usecase.OrganizeResult
import com.bragadev.fiscal.domain.usecase.PlanOrganizationUseCase
import com.bragadev.fiscal.domain.usecase.PlanRenameUseCase
import com.bragadev.fiscal.domain.usecase.RemoveFromMonthUseCase
import com.bragadev.fiscal.presentation.common.DocumentChangeNotifier
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.common.UserMessage
import com.bragadev.fiscal.presentation.common.ViewModel
import com.bragadev.fiscal.presentation.common.toUserMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.file.Path

/** Renomear, retirar do mês e marcar pendência em arquivos que já estão na pasta do mês. */
class MonthFilesViewModel(
    private val planOrganization: PlanOrganizationUseCase,
    private val planRename: PlanRenameUseCase,
    private val organizeDocument: OrganizeDocumentUseCase,
    private val removeFromMonth: RemoveFromMonthUseCase,
    private val fileFlags: FileFlagUseCase,
    private val observeSettings: ObserveSettingsUseCase,
    private val documentChanges: DocumentChangeNotifier,
) : ViewModel() {
    private val state = MutableStateFlow(MonthFilesUiState())
    val uiState: StateFlow<MonthFilesUiState> = state.asStateFlow()
    private var inputJob: Job? = null

    // ---- Renomear ----

    fun onRenameRequested(file: Path, category: DocumentCategory) {
        scope.launch {
            val numbered = category.namingRule == NamingRule.DESCRIBED_SEQUENCE
            // Ainda sem alteração: o botão fica desativado até o usuário mudar algo.
            val dialog = MonthFileDialog.Rename(
                file = file,
                category = category,
                mode = if (numbered) RenameMode.NUMBER_AND_DESCRIPTION else RenameMode.FULL_NAME,
                numberedModeAvailable = numbered,
                number = if (numbered) planOrganization.currentNumber(file, category.id) else "",
                description = if (numbered) planOrganization.suggestDescription(file, category.id).orEmpty() else "",
                fullName = FileNameRules.baseName(file.fileName.toString()),
            )
            state.update { it.copy(dialog = dialog) }
        }
    }

    fun onRenameModeChanged(mode: RenameMode) = editRename { it.copy(mode = mode) }

    fun onRenameNumberChanged(text: String) = editRename { it.copy(number = text) }

    fun onRenameDescriptionChanged(text: String) = editRename { it.copy(description = text) }

    fun onRenameFullNameChanged(text: String) = editRename { it.copy(fullName = text) }

    fun onRenameConfirmed() {
        val dialog = state.value.dialog as? MonthFileDialog.Rename ?: return
        inputJob?.cancel()
        scope.launch {
            val fresh = withPlan(dialog)
            val plan = fresh.plan
            if (plan == null || fresh.inputError != null) return@launch state.update { it.copy(dialog = fresh) }
            run(Strings.FILE_RENAMED) { executeRename(plan) }
        }
    }

    // ---- Retirar do mês ----

    fun onRemoveRequested(file: Path) {
        scope.launch {
            when (val target = removeFromMonth.plan(file)) {
                is Outcome.Success -> state.update { it.copy(dialog = MonthFileDialog.Remove(file, target.value)) }
                is Outcome.Failure -> showError(target.error.toUserMessage())
            }
        }
    }

    fun onRemoveConfirmed() {
        val dialog = state.value.dialog as? MonthFileDialog.Remove ?: return
        scope.launch { run(Strings.FILE_REMOVED) { removeFromMonth(dialog.file) } }
    }

    // ---- Pendências ----

    fun onFlagRequested(file: Path, currentNote: String?) {
        state.update { it.copy(dialog = MonthFileDialog.Flag(file, currentNote ?: Strings.ISSUE_DEFAULT_NOTE)) }
    }

    fun onFlagNoteChanged(note: String) {
        val dialog = state.value.dialog as? MonthFileDialog.Flag ?: return
        state.update { it.copy(dialog = dialog.copy(note = note)) }
    }

    fun onFlagConfirmed() {
        val dialog = state.value.dialog as? MonthFileDialog.Flag ?: return
        if (dialog.note.isBlank()) return
        scope.launch {
            fileFlags.mark(dialog.file, dialog.note)
            finish(UserMessage(Strings.ISSUE_MARKED))
        }
    }

    fun onFlagCleared(file: Path) {
        scope.launch {
            fileFlags.clear(file)
            finish(UserMessage(Strings.ISSUE_CLEARED))
        }
    }

    fun dismissDialog() {
        inputJob?.cancel()
        state.update { it.copy(dialog = null) }
    }

    fun onMessageShown() {
        state.update { it.copy(message = null) }
    }

    // ---- Internos ----

    /** Aplica a edição no diálogo e recalcula o novo nome depois de uma pausa na digitação. */
    private fun editRename(transform: (MonthFileDialog.Rename) -> MonthFileDialog.Rename) {
        val dialog = state.value.dialog as? MonthFileDialog.Rename ?: return
        val edited = transform(dialog).copy(plan = null, inputError = null, warning = null)
        state.update { it.copy(dialog = edited) }
        inputJob?.cancel()
        inputJob = scope.launch {
            delay(INPUT_DEBOUNCE_MS)
            val updated = withPlan(edited)
            state.update { current -> if (current.dialog == edited) current.copy(dialog = updated) else current }
        }
    }

    private suspend fun withPlan(dialog: MonthFileDialog.Rename): MonthFileDialog.Rename {
        val outcome = when (dialog.mode) {
            RenameMode.FULL_NAME -> planRename(dialog.file, dialog.category.id, dialog.fullName)
            RenameMode.NUMBER_AND_DESCRIPTION -> {
                // Número vazio = próximo livre; número de outra categoria é recusado.
                val index = dialog.number.takeIf { it.isNotBlank() }?.let {
                    DescribedSequenceNaming.parseIndex(dialog.category, it)
                        ?: return dialog.copy(plan = null, inputError = FileOperationError.InvalidSequenceNumber.toUserMessage())
                }
                planOrganization(dialog.file, dialog.category.id, OrganizeMode.RENAME_ONLY, dialog.description, index)
            }
        }
        return when (outcome) {
            is Outcome.Success -> dialog.copy(
                plan = outcome.value,
                inputError = conflictError(outcome.value),
                warning = outcome.value.sameNumberFiles.takeIf { it.isNotEmpty() }?.let(Strings::sameNumberWarning),
            )
            // Nome igual ao atual não é erro: só não há o que renomear.
            is Outcome.Failure -> dialog.copy(
                plan = null,
                inputError = outcome.error.takeUnless { it == FileOperationError.AlreadyInPlace }?.toUserMessage(),
            )
        }
    }

    /** Renomear nunca substitui outro arquivo; só cria cópia numerada se a configuração mandar. */
    private fun conflictError(plan: OrganizationPlan): String? {
        val autoCopy = observeSettings().value.duplicatePolicy == DuplicatePolicy.AUTO_NUMBERED_COPY
        return if (plan.hasConflict && !autoCopy) Strings.NAME_TAKEN else null
    }

    private suspend fun executeRename(plan: OrganizationPlan): Outcome<FileOperation> {
        val resolution = if (plan.hasConflict) DuplicateResolution.NUMBERED_COPY else null
        return when (val result = organizeDocument(plan, resolution)) {
            is Outcome.Success -> when (val value = result.value) {
                is OrganizeResult.Done -> Outcome.Success(value.operation)
                // Outro arquivo ocupou o nome enquanto o diálogo estava aberto.
                else -> Outcome.Failure(FileOperationError.DestinationAlreadyExists)
            }
            is Outcome.Failure -> result
        }
    }

    /** Executa a operação, mostra o resultado (com "Desfazer" quando houver) e atualiza as listas. */
    private suspend fun run(successText: String, operation: suspend () -> Outcome<FileOperation>) {
        state.update { it.copy(isWorking = true) }
        when (val result = operation()) {
            is Outcome.Success -> {
                val done = result.value
                finish(UserMessage(successText, undoOperationId = done.id), selectPath = Path.of(done.newPath))
            }
            is Outcome.Failure -> showError(result.error.toUserMessage())
        }
        state.update { it.copy(isWorking = false) }
    }

    private fun finish(message: UserMessage, selectPath: Path? = null) {
        state.update { it.copy(dialog = null, message = message) }
        documentChanges.notifyChanged(selectPath)
    }

    private fun showError(text: String) {
        state.update { it.copy(message = UserMessage(text, isError = true)) }
    }

    private companion object {
        const val INPUT_DEBOUNCE_MS = 250L
    }
}
