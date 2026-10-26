package com.bragadev.fiscal.presentation.organizer

import com.bragadev.fiscal.domain.model.*
import com.bragadev.fiscal.domain.rules.EditablePeriodPolicy
import com.bragadev.fiscal.domain.usecase.*
import com.bragadev.fiscal.presentation.common.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.util.*

/** Lado direito: mês em edição, categorias e as operações de organizar e desfazer. */
class OrganizerViewModel(
    private val getCategoryTree: GetCategoryTreeUseCase,
    private val getMonthChecklist: GetMonthChecklistUseCase,
    private val checkExpenses: CheckExpensesUseCase,
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
    private var expenseJob: Job? = null

    /**
     * Arquivos (nome em minúsculas) que acabaram de ser organizados e ainda precisam ser conferidos. Os outros
     * só mostram o resultado guardado: abrir o app ou trocar de mês não lê nenhum PDF.
     */
    private val pendingChecks = mutableSetOf<String>()

    /** "Checar pendências" pedido e ainda não terminado (continua se a lista for relida no meio). */
    private var checkAllRequested = false
    private val state = MutableStateFlow(OrganizerUiState(firstEditableMonth = periodPolicy.firstEditableMonth))
    val uiState: StateFlow<OrganizerUiState> = state.asStateFlow()

    val currentMonthFolder: Path? get() = observeSettings().value.monthFolder

    init {
        scope.launch { refreshUndoAvailability() }
        scope.launch {
            // O mês de corte também entra: mudar nas Configurações trava/libera o mês aberto na hora.
            observeSettings().map { it.monthFolder to it.firstEditableMonth }.distinctUntilChanged().collectLatest { (folder, firstEditable) ->
                state.update { it.copy(firstEditableMonth = firstEditable) }
                showMonthFolder(folder)
                // Mudanças feitas fora do app (ex.: no Explorer) atualizam o "já existe / faltando" sozinhas.
                folder?.let { watchFolder(it).collect { refreshChecklist() } }
            }
        }
        scope.launch {
            // Trocar quem assina (despesas, extratos, servo de contas do TO-62) ou os termos de isenção em
            // Configurações invalida os resultados guardados: os documentos voltam a "não conferido" até o próximo
            // "Checar pendências".
            observeSettings().map {
                listOf(
                    it.expenseSigners,
                    it.expenseExemptTerms,
                    it.statementSigner,
                    it.accountsServant
                )
            }
                .distinctUntilChanged().drop(1)
                .collect { refreshChecklist() }
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

    /** Botão "Checar pendências": confere a folha, as despesas e o extrato do mês que ainda não foram conferidos. */
    fun onCheckIssuesRequested() {
        if (state.value.isCheckingAll) return
        checkAllRequested = true
        state.update { it.copy(isCheckingAll = true) }
        scope.launch { refreshChecklist() }
    }

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
        // Outro mês: nomes iguais em meses diferentes são outros arquivos; um "Checar pendências" em andamento para.
        pendingChecks.clear()
        checkAllRequested = false
        state.update { it.copy(monthFolder = info, groups = groups, expenseChecks = emptyMap(), isCheckingAll = false) }
        refreshChecklist()
    }

    /** Relê a pasta do mês para mostrar o que já existe e o que está faltando. */
    private suspend fun refreshChecklist() {
        val info = state.value.monthFolder
        val checklist = info?.let { getMonthChecklist(it.path, it.account) }
        state.update { it.copy(checklist = checklist) }
        startExpenseCheck(info, checklist)
    }

    /**
     * Mostra a conferência da folha, das despesas e do extrato. Só lê os PDFs recém-organizados
     * ([pendingChecks]) ou, com "Checar pendências", todos os que ainda não foram conferidos; os demais mostram o
     * resultado guardado ou "não conferido". Roda em segundo plano (o OCR leva alguns segundos por arquivo); cada
     * resultado aparece assim que sai e, se alguma pendência mudou, a lista é relida.
     */
    private fun startExpenseCheck(info: MonthFolderInfo?, checklist: MonthChecklist?) {
        expenseJob?.cancel()
        val names =
            if (info != null && checklist != null) checkExpenses.expenseFiles(info.account, checklist) else emptyList()
        // Organizado numa categoria sem regras (ou já retirado do mês): nada a conferir.
        pendingChecks.retainAll(names.map { it.lowercase() }.toSet())
        val only = if (checkAllRequested) null else pendingChecks.toSet()
        val known = state.value.expenseChecks
        state.update { current ->
            current.copy(expenseChecks = names.associate { name ->
                val key = name.lowercase()
                val willRead = only == null || key in only
                val shown = known[key]?.takeIf { it == ExpenseStatus.VERIFIED || it == ExpenseStatus.HAS_ISSUES }
                key to (shown ?: if (willRead) ExpenseStatus.CHECKING else ExpenseStatus.UNCHECKED)
            })
        }
        if (info == null || checklist == null || names.isEmpty()) return finishCheckAll()
        expenseJob = scope.launch {
            val flagsChanged =
                checkExpenses(
                    info.path,
                    info.account,
                    checklist,
                    info.detectedMonth?.month,
                    only
                ) { name, status, evidence ->
                    val key = name.lowercase()
                    if (status != ExpenseStatus.UNCHECKED) pendingChecks.remove(key)
                    state.update {
                        it.copy(
                            expenseChecks = it.expenseChecks + (key to status),
                            expenseEvidence = if (evidence == null) it.expenseEvidence - key else it.expenseEvidence + (key to evidence),
                        )
                    }
                }
            if (flagsChanged && state.value.monthFolder?.path == info.path) {
                val updated = getMonthChecklist(info.path, info.account)
                state.update { it.copy(checklist = updated) }
            }
            if (only == null) finishCheckAll()
        }
    }

    /** Fim do "Checar pendências": avisa quantos documentos ficaram com pendência. */
    private fun finishCheckAll() {
        if (!checkAllRequested) return
        checkAllRequested = false
        val statuses = state.value.expenseChecks.values
        val text = when {
            statuses.isEmpty() -> Strings.CHECK_ISSUES_NOTHING
            else -> Strings.checkIssuesDone(
                withIssues = statuses.count { it == ExpenseStatus.HAS_ISSUES },
                notChecked = statuses.count { it == ExpenseStatus.NOT_CHECKED },
            )
        }
        state.update { it.copy(isCheckingAll = false, message = UserMessage(text)) }
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
                // O documento que chegou ao mês é conferido logo (se a categoria dele tiver regras).
                val newPath = Path.of(result.operation.newPath)
                if (newPath.parent == state.value.monthFolder?.path) {
                    pendingChecks += newPath.fileName.toString().lowercase()
                }
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
