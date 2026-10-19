package com.bragadev.fiscal.presentation.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.presentation.common.Strings
import kotlinx.coroutines.delay

/**
 * Botão que copia [text] para a área de transferência (caminho de pasta, nome de arquivo...).
 * Depois do clique o ícone vira um "certo" verde por um instante, para o usuário saber que copiou.
 *
 * @param copy quem coloca o texto na área de transferência (nos testes, um falso).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopyButton(
    text: String,
    description: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 18.dp,
    copy: (String) -> Unit = DesktopActions::copyToClipboard,
) {
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_FEEDBACK_MS)
            copied = false
        }
    }
    val label = if (copied) Strings.COPIED else description
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = {
                copy(text)
                copied = true
            },
            enabled = text.isNotBlank(),
            modifier = modifier,
        ) {
            Icon(
                if (copied) AppIcons.Check else AppIcons.Copy,
                contentDescription = label,
                modifier = Modifier.size(iconSize),
                tint = when {
                    copied -> StatusColors.Positive
                    text.isNotBlank() -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
        }
    }
}

private const val COPIED_FEEDBACK_MS = 1500L
