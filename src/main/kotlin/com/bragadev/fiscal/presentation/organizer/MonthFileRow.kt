package com.bragadev.fiscal.presentation.organizer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.domain.model.DocumentCategory
import com.bragadev.fiscal.domain.model.ExpenseStatus
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.components.AppIcons
import com.bragadev.fiscal.presentation.components.IconText
import com.bragadev.fiscal.presentation.components.StatusColors
import java.nio.file.Path
import kotlin.math.roundToInt

/**
 * O que o usuário pode fazer com um arquivo que já está na pasta do mês.
 *
 * @param onRename categoria `null` = arquivo sem número (renomeia o nome livre, mantendo a extensão).
 */
class MonthFileActions(
    val onPreview: (Path) -> Unit,
    val onRename: (Path, DocumentCategory?) -> Unit,
    val onCopy: (Path) -> Unit,
    val onDelete: (Path) -> Unit,
    val onRemove: (Path) -> Unit,
    val onFlag: (file: Path, currentNote: String?) -> Unit,
    val onClearFlag: (Path) -> Unit,
    val onSendWhatsApp: (Path) -> Unit = {},
)

/**
 * Um arquivo da pasta do mês: clicar mostra no preview; o lápis renomeia. O botão direito abre o menu onde o
 * mouse está (e mostra o arquivo no preview); o ⋮ abre o mesmo menu: renomear, copiar, excluir, retirar do mês
 * e marcar/remover pendência. Com o mês bloqueado, só o preview, copiar e as pendências ficam disponíveis.
 *
 * @param category categoria do arquivo; `null` para arquivos sem número.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MonthFileRow(
    file: Path,
    category: DocumentCategory?,
    issue: String?,
    editable: Boolean,
    actions: MonthFileActions,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    /** Conferência automática (despesas da Conta da Manutenção); `null` = arquivo não é conferido. */
    expenseStatus: ExpenseStatus? = null,
) {
    // Mesmo destaque da lista de documentos (lado esquerdo) para o arquivo aberto no preview.
    val background = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    // Onde o botão direito foi clicado (o menu abre ali); null = menu fechado.
    var pointerMenuAt by remember { mutableStateOf<Offset?>(null) }
    var buttonMenuOpen by remember { mutableStateOf(false) }
    val menu = MonthFileMenuContext(file, category, issue, editable, actions)
    Box(
        modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(6.dp))
            .onPointerEvent(PointerEventType.Press) { event ->
                if (event.buttons.isSecondaryPressed) {
                    actions.onPreview(file)
                    pointerMenuAt = event.changes.first().position
                }
            },
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 4.dp)) {
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
                ExpenseStatusIcon(expenseStatus, hasIssue = issue != null)
                if (editable && category != null) {
                    IconButton(onClick = { actions.onRename(file, category) }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            AppIcons.Pencil,
                            Strings.RENAME_FILE,
                            Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Box {
                    IconButton(onClick = { buttonMenuOpen = true }, modifier = Modifier.size(28.dp)) {
                        Icon(AppIcons.MoreVertical, Strings.FILE_ACTIONS, Modifier.size(16.dp))
                    }
                    MonthFileMenu(buttonMenuOpen, menu, onDismiss = { buttonMenuOpen = false })
                }
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
        pointerMenuAt?.let { at ->
            Box(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }) {
                MonthFileMenu(expanded = true, menu, onDismiss = { pointerMenuAt = null })
            }
        }
    }
}

/**
 * Ao lado do nome da despesa: "certo" verde quando cupom/nota, comprovante e assinatura foram encontrados, ou
 * um indicador enquanto confere. Com pendência não mostra nada: a própria pendência diz o que falta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseStatusIcon(status: ExpenseStatus?, hasIssue: Boolean) {
    val description = when {
        status == ExpenseStatus.CHECKING -> Strings.EXPENSE_CHECKING
        status == ExpenseStatus.VERIFIED && !hasIssue -> Strings.EXPENSE_VERIFIED
        else -> return
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState(),
    ) {
        Box(Modifier.size(28.dp).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
            if (status == ExpenseStatus.CHECKING) {
                CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    AppIcons.CheckCircle,
                    contentDescription = null,
                    Modifier.size(17.dp),
                    tint = StatusColors.Positive
                )
            }
        }
    }
}

private class MonthFileMenuContext(
    val file: Path,
    val category: DocumentCategory?,
    val issue: String?,
    val editable: Boolean,
    val actions: MonthFileActions,
)

/** Mesmo menu da lista de documentos (renomear, copiar, excluir) mais as ações próprias do mês. */
@Composable
private fun MonthFileMenu(expanded: Boolean, menu: MonthFileMenuContext, onDismiss: () -> Unit) {
    val file = menu.file
    val actions = menu.actions
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MenuItem(AppIcons.Pencil, Strings.RENAME, enabled = menu.editable) {
            onDismiss(); actions.onRename(
            file,
            menu.category
        )
        }
        MenuItem(AppIcons.Copy, Strings.COPY_FILE) { onDismiss(); actions.onCopy(file) }
        MenuItem(AppIcons.Send, Strings.SEND_WHATSAPP) { onDismiss(); actions.onSendWhatsApp(file) }
        MenuItem(
            AppIcons.Delete,
            Strings.DELETE_FILE,
            enabled = menu.editable,
            color = MaterialTheme.colorScheme.error
        ) {
            onDismiss()
            actions.onDelete(file)
        }
        HorizontalDivider()
        DropdownMenuItem(text = { Text(Strings.PREVIEW_FILE) }, onClick = { onDismiss(); actions.onPreview(file) })
        DropdownMenuItem(
            text = { Text(Strings.REMOVE_FROM_MONTH) },
            enabled = menu.editable,
            onClick = { onDismiss(); actions.onRemove(file) },
        )
        DropdownMenuItem(
            text = { Text(Strings.MARK_ISSUE) },
            onClick = { onDismiss(); actions.onFlag(file, menu.issue) })
        if (menu.issue != null) {
            DropdownMenuItem(text = { Text(Strings.CLEAR_ISSUE) }, onClick = { onDismiss(); actions.onClearFlag(file) })
        }
    }
}

@Composable
private fun MenuItem(
    icon: ImageVector,
    text: String,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    // Item desativado (mês fechado) fica apagado, inclusive o vermelho do Excluir.
    val tint = if (enabled) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    DropdownMenuItem(
        text = { Text(text, color = tint) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp)) },
        enabled = enabled,
        onClick = onClick,
    )
}
