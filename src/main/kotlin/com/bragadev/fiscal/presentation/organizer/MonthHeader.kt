package com.bragadev.fiscal.presentation.organizer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.domain.model.MonthFolderInfo
import com.bragadev.fiscal.domain.model.MonthFolderStatus
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.components.StatusColors
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
) {
    Column(modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        MonthBanner(monthFolder, firstEditableMonth)
    }
}

@Composable
private fun MonthBanner(monthFolder: MonthFolderInfo?, firstEditableMonth: YearMonth) {
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
                status?.let { Text(it, color = accent, fontWeight = FontWeight.SemiBold) }
            }
            monthFolder?.account?.let { Text(Strings.accountLabel(it.displayName), style = MaterialTheme.typography.bodyMedium) }
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
