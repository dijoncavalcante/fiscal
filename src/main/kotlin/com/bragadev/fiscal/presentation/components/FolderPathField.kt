package com.bragadev.fiscal.presentation.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.presentation.common.Strings
import java.io.File

/**
 * Caminho completo de uma pasta, sempre visível e protegido contra digitação.
 * O texto pode ser selecionado e copiado; só o lápis permite trocar a pasta.
 *
 * [highlightedSegment], quando informado, aparece em destaque (ex.: a pasta do mês).
 * [onOpenInExplorer], quando informado, mostra o ícone de pasta que abre o caminho no Explorer e, ao lado, o botão
 * que copia o caminho (só com caminho preenchido).
 */
@Composable
fun FolderPathField(
    label: String,
    path: String,
    emptyText: String,
    editDescription: String,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    highlightedSegment: String? = null,
    onOpenInExplorer: (() -> Unit)? = null,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SelectionContainer(Modifier.weight(1f).padding(start = 10.dp, top = 6.dp, bottom = 6.dp)) {
                    Text(
                        text = if (path.isBlank()) AnnotatedString(emptyText) else highlighted(path, highlightedSegment),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (onOpenInExplorer != null && path.isNotBlank()) {
                    CopyButton(path, Strings.COPY_PATH)
                    FieldButton(AppIcons.FolderOpen, Strings.OPEN_IN_EXPLORER, onOpenInExplorer)
                }
                FieldButton(AppIcons.Pencil, editDescription, onEdit)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun FieldButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick) {
            Icon(
                icon,
                contentDescription = description,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun highlighted(path: String, segment: String?): AnnotatedString = buildAnnotatedString {
    val segments = path.split(File.separatorChar, '/')
    segments.forEachIndexed { index, part ->
        if (index > 0) append(File.separatorChar)
        if (segment != null && part == segment) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) }
        } else {
            append(part)
        }
    }
}
