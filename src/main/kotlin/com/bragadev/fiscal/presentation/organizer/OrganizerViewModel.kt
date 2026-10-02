package com.bragadev.fiscal.presentation.organizer

import com.bragadev.fiscal.domain.model.DuplicatePolicy
import com.bragadev.fiscal.domain.model.DuplicateResolution
import com.bragadev.fiscal.domain.model.FileOperationError
import com.bragadev.fiscal.domain.model.MonthFolderStatus
import com.bragadev.fiscal.domain.model.OrganizationPlan
import com.bragadev.fiscal.domain.model.OrganizeMode
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.model.getOrNull
import com.bragadev.fiscal.domain.rules.EditablePeriodPolicy
import com.bragadev.fiscal.domain.usecase.ChangeMonthFolderUseCase
import com.bragadev.fiscal.domain.usecase.DescribeMonthFolderUseCase
import com.bragadev.fiscal.domain.usecase.GetCategoryTreeUseCase
import com.bragadev.fiscal.domain.usecase.ObserveSettingsUseCase
import com.bragadev.fiscal.domain.usecase.OrganizeDocumentUseCase
import com.bragadev.fiscal.domain.usecase.OrganizeResult
import com.bragadev.fiscal.domain.usecase.PlanOrganizationUseCase
import com.bragadev.fiscal.domain.usecase.UndoOperationUseCase
import com.bragadev.fiscal.presentation.common.DocumentChangeNotifier
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.common.UserMessage
import com.bragadev.fiscal.presentation.common.ViewModel
import com.bragadev.fiscal.presentation.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.util.UUID

/** Lado direito: mês em edição, categorias e as operações de organizar e desfazer. */
class OrganizerViewModel(
    private val getCategoryTree: GetCategoryTreeUseCase,
    private val planOrganization: PlanOrganizationUseCase,
    private val organizeDocument: OrganizeDocumentUseCase,
    private val undoOperation: UndoOperationUseCase,
    private val changeMonthFolder: ChangeMonthFolderUseCase,
    private val describeMonthFolder: DescribeMonthFolderUseCase,
    private val observeSettings: ObserveSettingsUseCase,
    private val documentChanges: DocumentChangeNotifier,
    periodPolicy: EditablePeriodPolicy,
) : ViewModel() {
    private val state = MutableStateFlow(OrganizerUiState(firstEditableMonth = periodPolicy.firstEditableMonth))
    val uiState: StateFlow<OrganizerUiState> = state.asStateFlow()

    val currentMonthFolder: Path? get() = observeSettings().value.monthFolder

    init {
        scope.launch { refreshUndoAvailability() }
        scope.launch {
            observeSettings().map { it.monthFolder }.distinctUntilChanged().collect(::showMonthFolder)
        }
    }

    fun onMonthFolderSelected(path: Path) {
        scope.launch {
            val result = changeMonthFolder(path)
            if (result is Outcome.Failure) showError(result.error.toUserMessage())
        }
    }

    /** Arquivo(s) solto(s) sobre uma categoria, ou documento selecionado + clique na categoria. */
    fun onFilesDropped(paths: List<Path>, categoryId: String) {
        blockedReason()?.let { return showError(it) }
        val source = paths.singleOrNull() ?: return showError(Strings.ONE_FILE_AT_A_TIME)
        scope.launch { proposeOrganization(source, categoryId) }
    }

    fun onCategoryClickedWithoutDocument() = showError(Strings.SELECT_OR_DRAG)

    fun onModeChosen(mode: OrganizeMode) {
        val proposal = state.value.dialog as? OrganizerDialog.Proposal ?: return
        val plan = if (mode == OrganizeMode.RENAME_ONLY) proposal.renamePlan else proposal.movePlan
        plan?.let { proceed(it, resolution = null) }
    }

    fun onConflictResolved(resolution: DuplicateResolution) {
        val conflict = state.value.dialog as? OrganizerDialog.Conflict ?: return
        if (resolution == DuplicateResolution.CANCEL) return dismissDialog()
        proceed(conflict.plan, resolution)
    }

    fun onConfirmed() {
        val confirm = state.value.dialog as? OrganizerDialog.Confirm ?: return
        execute(confirm.plan, confirm.resolution)
    }

    fun dismissDialog() {
        state.update { it.copy(dialog = null) }
    }

    fun undo(operationId: UUID? = state.value.lastUndoableOperationId) {
        operationId ?: return
        scope.launch {
            state.update { it.copy(isWorking = true) }
            when (val result = undoOperation(operationId)) {
                is Outcome.Success -> {
                    showMessage(UserMessage(Strings.UNDONE))
                    documentChanges.notifyChanged(selectPath = Path.of(result.value.originalPath))
                }
                is Outcome.Failure -> showError(result.error.toUserMessage())
            }
            state.update { it.copy(isWorking = false) }
            refreshUndoAvailability()
        }
    }

    fun onMessageShown() {
        state.update { it.copy(message = null) }
    }

    private suspend fun showMonthFolder(folder: Path?) {
        val info = folder?.let { describeMonthFolder(it) }
        val groups = getCategoryTree(info?.account)
        state.update { it.copy(monthFolder = info, groups = groups) }
    }

    /** Motivo para recusar a organização antes mesmo de calcular a proposta. */
    private fun blockedReason(): String? {
        val current = state.value
        val info = current.monthFolder ?: return FileOperationError.MonthFolderNotSelected.toUserMessage()
        return when (info.status) {
            MonthFolderStatus.EDITABLE -> null
            MonthFolderStatus.UNKNOWN_MONTH -> FileOperationError.MonthNotIdentified.toUserMessage()
            MonthFolderStatus.LOCKED ->
                FileOperationError.MonthLocked(info.detectedMonth!!.month, current.firstEditableMonth).toUserMessage()
        }
    }

    private suspend fun proposeOrganization(source: Path, categoryId: String) {
        val movePlan = planOrganization(source, categoryId, OrganizeMode.RENAME_AND_MOVE)
        val renamePlan = planOrganization(source, categoryId, OrganizeMode.RENAME_ONLY)
        val available = listOfNotNull(movePlan.getOrNull(), renamePlan.getOrNull())
        if (available.isEmpty()) return showError(firstError(movePlan, renamePlan).toUserMessage())

        val category = available.first().category
        state.update {
            it.copy(dialog = OrganizerDialog.Proposal(source, category, renamePlan.getOrNull(), movePlan.getOrNull()))
        }
    }

    /** Resolve conflito (conforme configuração) e confirmação antes de executar. */
    private fun proceed(plan: OrganizationPlan, resolution: DuplicateResolution?) {
        val settings = observeSettings().value
        var finalResolution = resolution
        if (plan.hasConflict && finalResolution == null) {
            when (settings.duplicatePolicy) {
                DuplicatePolicy.ASK -> return showDialog(OrganizerDialog.Conflict(plan))
                DuplicatePolicy.FORBID -> return showError(FileOperationError.DuplicatesNotAllowed.toUserMessage())
                DuplicatePolicy.AUTO_NUMBERED_COPY -> finalResolution = DuplicateResolution.NUMBERED_COPY
            }
        }
        val needsConfirmation = when (plan.mode) {
            OrganizeMode.RENAME_ONLY -> settings.confirmBeforeRename
            OrganizeMode.RENAME_AND_MOVE -> settings.confirmBeforeMove
        }
        if (needsConfirmation) showDialog(OrganizerDialog.Confirm(plan, finalResolution)) else execute(plan, finalResolution)
    }

    private fun execute(plan: OrganizationPlan, resolution: DuplicateResolution?) {
        scope.launch {
            state.update { it.copy(isWorking = true, dialog = null) }
            when (val result = organizeDocument(plan, resolution)) {
                is Outcome.Success -> handleOrganizeResult(plan, result.value)
                is Outcome.Failure -> showError(result.error.toUserMessage())
            }
            state.update { it.copy(isWorking = false) }
            refreshUndoAvailability()
        }
    }

    private fun handleOrganizeResult(plan: OrganizationPlan, result: OrganizeResult) {
        when (result) {
            is OrganizeResult.Done -> {
                showMessage(UserMessage(Strings.ORGANIZED, undoOperationId = result.operation.id))
                documentChanges.notifyChanged(selectPath = Path.of(result.operation.newPath))
            }
            // O destino passou a existir depois da proposta: pergunta ao usuário.
            OrganizeResult.NeedsConflictChoice -> showDialog(OrganizerDialog.Conflict(plan.copy(hasConflict = true)))
            OrganizeResult.Cancelled -> Unit
        }
    }

    private suspend fun refreshUndoAvailability() {
        val last = undoOperation.lastUndoable()
        state.update { it.copy(lastUndoableOperationId = last?.id) }
    }

    private fun firstError(vararg outcomes: Outcome<*>): FileOperationError =
        outcomes.firstNotNullOfOrNull { (it as? Outcome.Failure)?.error } ?: FileOperationError.Unknown(IllegalStateException())

    private fun showDialog(dialog: OrganizerDialog) {
        state.update { it.copy(dialog = dialog) }
    }

    private fun showError(text: String) {
        state.update { it.copy(dialog = null, message = UserMessage(text, isError = true)) }
    }

    private fun showMessage(message: UserMessage) {
        state.update { it.copy(message = message) }
    }
}
