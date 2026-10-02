package com.bragadev.fiscal.presentation.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * Caminho completo de uma pasta, sempre visível e protegido contra digitação.
 * O texto pode ser selecionado e copiado; só o lápis permite trocar a pasta.
 *
 * [highlightedSegment], quando informado, aparece em destaque (ex.: a pasta do mês).
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
                EditButton(editDescription, onEdit)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun EditButton(description: String, onEdit: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onEdit) {
            Icon(PencilIcon, contentDescription = description, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
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
