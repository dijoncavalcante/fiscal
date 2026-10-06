package com.bragadev.fiscal.presentation.navigator

import com.bragadev.fiscal.domain.model.AccountType
import com.bragadev.fiscal.domain.model.MonthTree
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.usecase.BrowseMonthFoldersUseCase
import com.bragadev.fiscal.domain.usecase.ChangeMonthFolderUseCase
import com.bragadev.fiscal.domain.usecase.ChangeMonthsRootUseCase
import com.bragadev.fiscal.domain.usecase.ObserveSettingsUseCase
import com.bragadev.fiscal.domain.usecase.ResolveMonthsRootUseCase
import com.bragadev.fiscal.presentation.common.DocumentChangeNotifier
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
import java.time.YearMonth

/**
 * Seletor de mês do lado direito: mostra as contas, anos de serviço, trimestres e meses
 * da pasta raiz, e troca a pasta do mês em edição com um clique.
 */
class MonthNavigatorViewModel(
    private val resolveMonthsRoot: ResolveMonthsRootUseCase,
    private val changeMonthsRoot: ChangeMonthsRootUseCase,
    private val browseMonthFolders: BrowseMonthFoldersUseCase,
    private val changeMonthFolder: ChangeMonthFolderUseCase,
    private val observeSettings: ObserveSettingsUseCase,
    private val documentChanges: DocumentChangeNotifier,
) : ViewModel() {
    private val state = MutableStateFlow(MonthNavigatorUiState())
    val uiState: StateFlow<MonthNavigatorUiState> = state.asStateFlow()

    val currentRoot: Path? get() = state.value.root

    init {
        scope.launch {
            // Mês de corte junto: os cadeados dos meses mudam quando ele muda nas Configurações.
            var lastFirstEditable: YearMonth? = null
            observeSettings().map { Triple(it.monthsRoot, it.monthFolder, it.firstEditableMonth) }.distinctUntilChanged().collect { (_, month, firstEditable) ->
                val root = resolveMonthsRoot()
                if (root != state.value.root || state.value.tree == null || firstEditable != lastFirstEditable) loadTree(root)
                lastFirstEditable = firstEditable
                showCurrentMonth(month)
            }
        }
        // "Atualizar" e mudanças feitas pelo app podem criar pastas novas.
        scope.launch { documentChanges.changes.collect { loadTree(state.value.root) } }
    }

    fun onRootSelected(path: Path) {
        scope.launch {
            val result = changeMonthsRoot(path)
            if (result is Outcome.Failure) state.update { it.copy(error = result.error.toUserMessage()) }
        }
    }

    fun onAccountSelected(account: AccountType) {
        state.update { it.copy(selectedAccount = account, selectedYear = defaultYear(it.tree, account, it.currentMonth)) }
    }

    fun onYearSelected(year: Path) {
        state.update { it.copy(selectedYear = year) }
    }

    fun onMonthSelected(month: Path) {
        scope.launch {
            val result = changeMonthFolder(month)
            if (result is Outcome.Failure) state.update { it.copy(error = result.error.toUserMessage()) }
        }
    }

    fun onToggleExpanded() {
        state.update { it.copy(expanded = !it.expanded) }
    }

    fun onErrorShown() {
        state.update { it.copy(error = null) }
    }

    private suspend fun loadTree(root: Path?) {
        if (root == null) return state.update { it.copy(root = null, tree = null) }
        state.update { it.copy(root = root, isLoading = true) }
        val tree = when (val result = browseMonthFolders(root)) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> null.also { state.update { it.copy(error = result.error.toUserMessage()) } }
        }
        state.update { it.copy(tree = tree, isLoading = false) }
        showCurrentMonth(state.value.currentMonth, keepUserChoice = true)
    }

    /** Abre o seletor na conta e no ano do mês em edição (ou mantém a escolha atual, se ainda existir). */
    private fun showCurrentMonth(month: Path?, keepUserChoice: Boolean = false) {
        state.update { current ->
            val tree = current.tree
            val monthAccount = tree?.accounts?.firstOrNull { account -> month != null && month.startsWith(account.path) }
            val account = when {
                keepUserChoice && tree?.accounts?.any { it.account == current.selectedAccount } == true -> current.selectedAccount
                monthAccount != null -> monthAccount.account
                else -> tree?.accounts?.firstOrNull()?.account
            }
            val year = if (keepUserChoice && current.account?.serviceYears?.any { it.path == current.selectedYear } == true) {
                current.selectedYear
            } else {
                defaultYear(tree, account, month)
            }
            current.copy(currentMonth = month, selectedAccount = account, selectedYear = year)
        }
    }

    /** Ano de serviço do mês em edição, se for da conta; senão o mais recente. */
    private fun defaultYear(tree: MonthTree?, account: AccountType?, month: Path?): Path? {
        val years = tree?.accounts?.firstOrNull { it.account == account }?.serviceYears.orEmpty()
        return years.firstOrNull { month != null && month.startsWith(it.path) }?.path ?: years.lastOrNull()?.path
    }
}
