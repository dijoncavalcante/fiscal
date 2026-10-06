package com.bragadev.fiscal.presentation.organizer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.domain.model.DocumentCategory
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.components.AppIcons
import com.bragadev.fiscal.presentation.components.IconText
import com.bragadev.fiscal.presentation.components.StatusColors
import java.nio.file.Path

/** O que o usuário pode fazer com um arquivo que já está na pasta do mês. */
class MonthFileActions(
    val onPreview: (Path) -> Unit,
    val onRename: (Path, DocumentCategory) -> Unit,
    val onRemove: (Path) -> Unit,
    val onFlag: (file: Path, currentNote: String?) -> Unit,
    val onClearFlag: (Path) -> Unit,
)

/**
 * Um arquivo da pasta do mês: clicar mostra no preview; o lápis renomeia; o menu ⋮ retira do mês
 * ou marca/remove pendência. Com o mês bloqueado, só o preview e as pendências ficam disponíveis.
 *
 * @param category categoria do arquivo; `null` para arquivos sem número (não há regra de nome para renomear).
 */
@Composable
fun MonthFileRow(
    file: Path,
    category: DocumentCategory?,
    issue: String?,
    editable: Boolean,
    actions: MonthFileActions,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
) {
    // Mesmo destaque da lista de documentos (lado esquerdo) para o arquivo aberto no preview.
    val background = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Column(modifier.fillMaxWidth().background(background, RoundedCornerShape(6.dp)).padding(start = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconText(
                icon = AppIcons.Document,
                text = file.fileName.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                iconSize = 14.dp,
                maxLines = 1,
                modifier = Modifier.weight(1f).clickable { actions.onPreview(file) }.padding(vertical = 4.dp),
            )
            if (editable && category != null) {
                IconButton(onClick = { actions.onRename(file, category) }, modifier = Modifier.size(28.dp)) {
                    Icon(AppIcons.Pencil, Strings.RENAME_FILE, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            FileMenu(file, issue, editable, actions)
        }
        issue?.let {
            IconText(
                icon = AppIcons.Warning,
                text = Strings.issueLabel(it),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = StatusColors.Warning,
                iconSize = 14.dp,
                modifier = Modifier.padding(start = 20.dp, bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun FileMenu(file: Path, issue: String?, editable: Boolean, actions: MonthFileActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(28.dp)) {
            Icon(AppIcons.MoreVertical, Strings.FILE_ACTIONS, Modifier.size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(Strings.PREVIEW_FILE) }, onClick = { open = false; actions.onPreview(file) })
            DropdownMenuItem(
                text = { Text(Strings.REMOVE_FROM_MONTH) },
                enabled = editable,
                onClick = { open = false; actions.onRemove(file) },
            )
            DropdownMenuItem(text = { Text(Strings.MARK_ISSUE) }, onClick = { open = false; actions.onFlag(file, issue) })
            if (issue != null) {
                DropdownMenuItem(text = { Text(Strings.CLEAR_ISSUE) }, onClick = { open = false; actions.onClearFlag(file) })
            }
        }
    }
}
