package com.bragadev.fiscal.presentation.organizer

import com.bragadev.fiscal.domain.model.CategoryNode
import com.bragadev.fiscal.domain.model.DocumentCategory
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
import com.bragadev.fiscal.domain.usecase.GetMonthChecklistUseCase
import com.bragadev.fiscal.domain.usecase.ObserveSettingsUseCase
import com.bragadev.fiscal.domain.usecase.OrganizeDocumentUseCase
import com.bragadev.fiscal.domain.usecase.OrganizeResult
import com.bragadev.fiscal.domain.usecase.PlanOrganizationUseCase
import com.bragadev.fiscal.domain.usecase.UndoOperationUseCase
import com.bragadev.fiscal.domain.usecase.WatchFolderUseCase
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.util.UUID

/** Lado direito: mês em edição, categorias e as operações de organizar e desfazer. */
class OrganizerViewModel(
    private val getCategoryTree: GetCategoryTreeUseCase,
    private val getMonthChecklist: GetMonthChecklistUseCase,
    private val planOrganization: PlanOrganizationUseCase,
    private val organizeDocument: OrganizeDocumentUseCase,
    private val undoOperation: UndoOperationUseCase,
    private val changeMonthFolder: ChangeMonthFolderUseCase,
    private val describeMonthFolder: DescribeMonthFolderUseCase,
    private val observeSettings: ObserveSettingsUseCase,
    private val watchFolder: WatchFolderUseCase,
    private val documentChanges: DocumentChangeNotifier,
    periodPolicy: EditablePeriodPolicy,
) : ViewModel() {
    private var descriptionJob: Job? = null
    private val state = MutableStateFlow(OrganizerUiState(firstEditableMonth = periodPolicy.firstEditableMonth))
    val uiState: StateFlow<OrganizerUiState> = state.asStateFlow()

    val currentMonthFolder: Path? get() = observeSettings().value.monthFolder

    init {
        scope.launch { refreshUndoAvailability() }
        scope.launch {
            observeSettings().map { it.monthFolder }.distinctUntilChanged().collectLatest { folder ->
                showMonthFolder(folder)
                // Mudanças feitas fora do app (ex.: no Explorer) atualizam o "já existe / faltando" sozinhas.
                folder?.let { watchFolder(it).collect { refreshChecklist() } }
            }
        }
        scope.launch {
            documentChanges.changes.collect {
                refreshChecklist()
                refreshUndoAvailability()
            }
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
        descriptionJob?.cancel()
        scope.launch {
            // Recalcula com a descrição atual: o usuário pode ter clicado antes do cálculo automático.
            val fresh = if (proposal.needsDescription) buildProposal(proposal.source, proposal.category, proposal.description) else proposal
            val plan = if (mode == OrganizeMode.RENAME_ONLY) fresh.renamePlan else fresh.movePlan
            if (plan == null) state.update { it.copy(dialog = fresh) } else proceed(plan, resolution = null)
        }
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
        refreshChecklist()
    }

    /** Relê a pasta do mês para mostrar o que já existe e o que está faltando. */
    private suspend fun refreshChecklist() {
        val info = state.value.monthFolder
        val checklist = info?.let { getMonthChecklist(it.path, it.account) }
        state.update { it.copy(checklist = checklist) }
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

    /** Descrição digitada no diálogo (ex.: Despesas): recalcula o nome final enquanto o usuário escreve. */
    fun onDescriptionChanged(text: String) {
        val proposal = state.value.dialog as? OrganizerDialog.Proposal ?: return
        state.update { it.copy(dialog = proposal.copy(description = text)) }
        descriptionJob?.cancel()
        descriptionJob = scope.launch {
            delay(DESCRIPTION_DEBOUNCE_MS)
            val updated = buildProposal(proposal.source, proposal.category, text)
            val current = state.value.dialog as? OrganizerDialog.Proposal
            if (current?.source == proposal.source && current.category == proposal.category) {
                state.update { it.copy(dialog = updated) }
            }
        }
    }

    private suspend fun proposeOrganization(source: Path, categoryId: String) {
        val category = findCategory(categoryId)
            ?: return showError(FileOperationError.CategoryNotFound.toUserMessage())
        val description = planOrganization.suggestDescription(source, categoryId)
        val plans = planBoth(source, categoryId, description)
        val proposal = proposalFrom(source, category, description, plans)

        val nothingAvailable = proposal.movePlan == null && proposal.renamePlan == null
        if (nothingAvailable && proposal.inputError == null) return showError(firstError(*plans).toUserMessage())
        state.update { it.copy(dialog = proposal) }
    }

    /**
     * Monta a proposta com os dois planos. Problemas na descrição viram aviso dentro do diálogo;
     * os demais (mês bloqueado, arquivo inexistente...) são tratados por quem chamou.
     */
    private suspend fun buildProposal(source: Path, category: DocumentCategory, description: String?) =
        proposalFrom(source, category, description, planBoth(source, category.id, description))

    private fun proposalFrom(
        source: Path,
        category: DocumentCategory,
        description: String?,
        plans: Array<Outcome<OrganizationPlan>>,
    ): OrganizerDialog.Proposal {
        val (movePlan, renamePlan) = plans
        val inputError = if (description == null) null else descriptionError(movePlan, renamePlan)
        return OrganizerDialog.Proposal(source, category, renamePlan.getOrNull(), movePlan.getOrNull(), description, inputError)
    }

    private suspend fun planBoth(source: Path, categoryId: String, description: String?): Array<Outcome<OrganizationPlan>> = arrayOf(
        planOrganization(source, categoryId, OrganizeMode.RENAME_AND_MOVE, description),
        planOrganization(source, categoryId, OrganizeMode.RENAME_ONLY, description),
    )

    private fun descriptionError(vararg outcomes: Outcome<OrganizationPlan>): String? {
        if (outcomes.any { it is Outcome.Success }) return null
        val error = firstError(*outcomes)
        val isInputProblem = error == FileOperationError.DescriptionRequired ||
            error == FileOperationError.InvalidFileName || error == FileOperationError.AlreadyInPlace
        return if (isInputProblem) error.toUserMessage() else null
    }

    private fun findCategory(id: String): DocumentCategory? {
        fun search(nodes: List<CategoryNode>): DocumentCategory? =
            nodes.firstNotNullOfOrNull { node -> node.category.takeIf { it.id == id } ?: search(node.children) }
        return state.value.groups.firstNotNullOfOrNull { search(it.nodes) }
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

    private companion object {
        const val DESCRIPTION_DEBOUNCE_MS = 250L
    }
}
