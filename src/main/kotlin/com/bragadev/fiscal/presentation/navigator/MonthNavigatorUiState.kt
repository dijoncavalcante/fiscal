package com.bragadev.fiscal.presentation.navigator

import com.bragadev.fiscal.domain.model.AccountFolder
import com.bragadev.fiscal.domain.model.AccountType
import com.bragadev.fiscal.domain.model.MonthTree
import com.bragadev.fiscal.domain.model.ServiceYearFolder
import java.nio.file.Path

/** Seletor de mês: pasta raiz das contas → conta → ano de serviço → trimestre → mês. */
data class MonthNavigatorUiState(
    val root: Path? = null,
    val tree: MonthTree? = null,
    val selectedAccount: AccountType? = null,
    val selectedYear: Path? = null,
    /** Pasta do mês em edição, destacada no seletor. */
    val currentMonth: Path? = null,
    val expanded: Boolean = true,
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val account: AccountFolder? get() = tree?.accounts?.firstOrNull { it.account == selectedAccount }
    val year: ServiceYearFolder? get() = account?.serviceYears?.firstOrNull { it.path == selectedYear }
}
