package com.bragadev.fiscal.presentation.organizer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.domain.model.MonthFolderInfo
import com.bragadev.fiscal.domain.model.MonthFolderStatus
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.components.AppIcons
import com.bragadev.fiscal.presentation.components.IconText
import com.bragadev.fiscal.presentation.components.StatusColors
import java.nio.file.Path
import java.time.YearMonth

/**
 * Cabeçalho do lado direito: o mês em edição em destaque e se ele está liberado ou bloqueado.
 * O caminho completo da pasta fica no seletor de mês ([com.bragadev.fiscal.presentation.navigator.MonthNavigator]).
 */
@Composable
fun MonthHeader(
    monthFolder: MonthFolderInfo?,
    firstEditableMonth: YearMonth,
    modifier: Modifier = Modifier,
    /** "Concluir mês": só aparece com um mês identificado (também nos fechados, para gerar o relatório de novo). */
    onConcludeMonth: (Path) -> Unit = {},
    /** "Checar pendências": `null` esconde o botão (mês sem conta com documentos conferidos). */
    onCheckIssues: (() -> Unit)? = null,
    isCheckingIssues: Boolean = false,
) {
    Column(modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        MonthBanner(monthFolder, firstEditableMonth, onConcludeMonth, onCheckIssues, isCheckingIssues)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MonthBanner(
    monthFolder: MonthFolderInfo?,
    firstEditableMonth: YearMonth,
    onConcludeMonth: (Path) -> Unit,
    onCheckIssues: (() -> Unit)?,
    isCheckingIssues: Boolean,
) {
    val (title, status, hint) = bannerTexts(monthFolder, firstEditableMonth)
    val (background, accent) = when (monthFolder?.status) {
        MonthFolderStatus.EDITABLE -> StatusColors.PositiveBackground to StatusColors.Positive
        MonthFolderStatus.LOCKED -> StatusColors.LockedBackground to MaterialTheme.colorScheme.error
        else -> StatusColors.WarningBackground to StatusColors.Warning
    }
    Surface(color = background, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                status?.let {
                    val icon = if (monthFolder?.status == MonthFolderStatus.LOCKED) AppIcons.Lock else AppIcons.CheckCircle
                    IconText(icon, it, color = accent, fontWeight = FontWeight.SemiBold, iconSize = 18.dp)
                }
            }
            monthFolder?.account?.let {
                Text(Strings.accountLabel(it.displayName), style = MaterialTheme.typography.bodyMedium)
            }
            // Botões numa linha própria: lado a lado com a conta, o nome dela ficava espremido.
            if (monthFolder?.detectedMonth != null) Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onCheckIssues != null) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                        tooltip = { PlainTooltip { Text(Strings.CHECK_ISSUES_HINT) } },
                        state = rememberTooltipState(),
                    ) {
                        OutlinedButton(onClick = onCheckIssues, enabled = !isCheckingIssues) {
                            if (isCheckingIssues) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Text(
                                if (isCheckingIssues) Strings.CHECKING_ISSUES else Strings.CHECK_ISSUES,
                                Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                }
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                    tooltip = { PlainTooltip { Text(Strings.CONCLUDE_MONTH_HINT) } },
                    state = rememberTooltipState(),
                ) {
                    OutlinedButton(onClick = { onConcludeMonth(monthFolder.path) }) {
                        Icon(AppIcons.TaskDone, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(Strings.CONCLUDE_MONTH, Modifier.padding(start = 6.dp))
                    }
                }
            }
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = accent, modifier = Modifier.padding(top = 4.dp)) }
        }
    }
}

private fun bannerTexts(info: MonthFolderInfo?, firstEditableMonth: YearMonth): Triple<String, String?, String?> {
    val month = info?.detectedMonth?.month
    return when {
        info == null -> Triple(Strings.MONTH_NOT_SELECTED, null, Strings.MONTH_NOT_SELECTED_HINT)
        month == null -> Triple(Strings.MONTH_UNKNOWN, null, Strings.MONTH_UNKNOWN_HINT)
        info.status == MonthFolderStatus.LOCKED ->
            Triple(Strings.monthTitle(month), Strings.STATUS_LOCKED, Strings.lockedHint(firstEditableMonth))
        else -> Triple(Strings.monthTitle(month), Strings.STATUS_EDITABLE, null)
    }
}
