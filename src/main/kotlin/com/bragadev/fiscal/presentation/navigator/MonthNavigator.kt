package com.bragadev.fiscal.presentation.navigator

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.domain.model.MonthFolderInfo
import com.bragadev.fiscal.domain.model.MonthFolderStatus
import com.bragadev.fiscal.domain.model.QuarterFolder
import com.bragadev.fiscal.domain.model.ServiceYearFolder
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.components.FolderPathField
import java.nio.file.Path

/**
 * Seletor de mês: um clique no mês troca a pasta em edição, sem abrir o seletor de pastas do Windows.
 * Pode ser recolhido para dar mais espaço às categorias.
 */
@Composable
fun MonthNavigator(
    state: MonthNavigatorUiState,
    viewModel: MonthNavigatorViewModel,
    monthFolder: MonthFolderInfo?,
    onEditMonthFolder: () -> Unit,
    onEditRoot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .padding(8.dp),
    ) {
        Row(Modifier.fillMaxWidth().clickable(onClick = viewModel::onToggleExpanded), verticalAlignment = Alignment.CenterVertically) {
            Text(if (state.expanded) "▾" else "▸", Modifier.width(18.dp), color = MaterialTheme.colorScheme.primary)
            Text(Strings.NAVIGATOR_TITLE, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        if (!state.expanded) return@Column

        FolderPathField(
            label = Strings.MONTHS_ROOT_LABEL,
            path = state.root?.toString().orEmpty(),
            emptyText = Strings.MONTHS_ROOT_EMPTY,
            editDescription = Strings.MONTHS_ROOT_EDIT,
            onEdit = onEditRoot,
            modifier = Modifier.padding(top = 6.dp),
        )
        // Caminho completo do mês em edição: sempre visível, protegido; só o lápis troca a pasta.
        FolderPathField(
            label = Strings.MONTH_FOLDER_LABEL,
            path = monthFolder?.path?.toString().orEmpty(),
            emptyText = Strings.MONTH_FOLDER_EMPTY,
            editDescription = Strings.MONTH_FOLDER_EDIT,
            onEdit = onEditMonthFolder,
            highlightedSegment = monthFolder?.detectedMonth?.folderName,
            modifier = Modifier.padding(top = 6.dp),
        )
        val tree = state.tree
        when {
            state.root == null -> Hint(Strings.MONTHS_ROOT_HINT)
            tree == null || tree.accounts.isEmpty() -> if (!state.isLoading) Hint(Strings.NO_MONTHS_FOUND)
            else -> {
                AccountChips(state, viewModel)
                state.account?.let { account -> YearSelector(account.serviceYears, state.year, viewModel::onYearSelected) }
                // Trimestre mais recente primeiro: o mês em que se está trabalhando fica no topo.
                state.year?.quarters?.asReversed()?.forEach { quarter -> QuarterRow(quarter, state.currentMonth, viewModel::onMonthSelected) }
            }
        }
    }
}

@Composable
private fun AccountChips(state: MonthNavigatorUiState, viewModel: MonthNavigatorViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
        state.tree?.accounts?.forEach { folder ->
            FilterChip(
                selected = folder.account == state.selectedAccount,
                onClick = { viewModel.onAccountSelected(folder.account) },
                label = { Text(folder.account.displayName) },
            )
        }
    }
}

@Composable
private fun YearSelector(years: List<ServiceYearFolder>, selected: ServiceYearFolder?, onSelect: (Path) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        Text(Strings.SERVICE_YEAR, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 8.dp))
        Box {
            OutlinedButton(onClick = { open = true }) {
                Text("${selected?.name ?: "—"} ▾", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                years.asReversed().forEach { year ->
                    DropdownMenuItem(text = { Text(year.name) }, onClick = { open = false; onSelect(year.path) })
                }
            }
        }
    }
}

/** Um trimestre com seus meses; o mês em edição aparece selecionado e meses fechados com cadeado. */
@Composable
private fun QuarterRow(quarter: QuarterFolder, currentMonth: Path?, onSelect: (Path) -> Unit) {
    Column(Modifier.padding(top = 6.dp)) {
        Text(
            quarter.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            quarter.months.forEach { month -> MonthChip(month, isCurrent = month.path == currentMonth, onSelect) }
        }
    }
}

@Composable
private fun MonthChip(month: MonthFolderInfo, isCurrent: Boolean, onSelect: (Path) -> Unit) {
    val yearMonth = month.detectedMonth?.month ?: return
    FilterChip(
        selected = isCurrent,
        onClick = { onSelect(month.path) },
        label = { Text(Strings.monthChip(yearMonth, locked = month.status == MonthFolderStatus.LOCKED)) },
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}
