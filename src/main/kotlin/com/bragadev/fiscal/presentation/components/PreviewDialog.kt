package com.bragadev.fiscal.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.nio.file.Path

/**
 * Diálogo largo com a visualização do PDF à esquerda e o formulário à direita,
 * para o usuário conferir o arquivo antes de confirmar qualquer alteração. Enter confirma e Esc cancela.
 */
@Composable
fun PreviewDialog(
    title: String,
    file: Path,
    onDismiss: () -> Unit,
    /** Ação do botão principal, acionada também pelo Enter; `null` enquanto o botão estiver desabilitado. */
    onConfirm: (() -> Unit)?,
    buttons: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            modifier = Modifier.width(980.dp).height(640.dp).dialogKeys(onConfirm, onDismiss),
        ) {
            Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                PdfThumbnail(file, Modifier.width(420.dp).fillMaxHeight().clip(MaterialTheme.shapes.medium))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 12.dp))
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { content() }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        content = buttons,
                    )
                }
            }
        }
    }
}
