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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.domain.model.MonthFolderInfo
import com.bragadev.fiscal.domain.model.MonthFolderStatus
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.components.FolderPathField
import java.time.YearMonth

private val EditableColor = Color(0xFF1B7F3B)
private val EditableBackground = Color(0xFFE3F4E8)
private val LockedBackground = Color(0xFFFDE7E7)
private val UnknownBackground = Color(0xFFFFF4D6)
private val UnknownColor = Color(0xFF8A5A00)

/**
 * Cabeçalho do lado direito: o mês em edição em destaque, se ele está liberado ou bloqueado,
 * e o caminho completo da pasta, sempre visível e protegido (só o lápis troca a pasta).
 */
@Composable
fun MonthHeader(
    monthFolder: MonthFolderInfo?,
    firstEditableMonth: YearMonth,
    onEditFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        MonthBanner(monthFolder, firstEditableMonth)
        FolderPathField(
            label = Strings.MONTH_FOLDER_LABEL,
            path = monthFolder?.path?.toString().orEmpty(),
            emptyText = Strings.MONTH_FOLDER_EMPTY,
            editDescription = Strings.MONTH_FOLDER_EDIT,
            onEdit = onEditFolder,
            highlightedSegment = monthFolder?.detectedMonth?.folderName,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun MonthBanner(monthFolder: MonthFolderInfo?, firstEditableMonth: YearMonth) {
    val (title, status, hint) = bannerTexts(monthFolder, firstEditableMonth)
    val (background, accent) = when (monthFolder?.status) {
        MonthFolderStatus.EDITABLE -> EditableBackground to EditableColor
        MonthFolderStatus.LOCKED -> LockedBackground to MaterialTheme.colorScheme.error
        else -> UnknownBackground to UnknownColor
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
