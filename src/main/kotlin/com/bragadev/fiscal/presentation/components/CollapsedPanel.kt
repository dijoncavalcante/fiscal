package com.bragadev.fiscal.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.presentation.common.Strings

/** Em que borda da janela o painel fica (e para onde ele recolhe). */
enum class PanelSide { LEFT, RIGHT }

/** Botão "recolher" no título de um [Panel]. */
class PanelCollapse(val side: PanelSide, val onCollapse: () -> Unit)

/** Largura da faixa que sobra quando o painel está recolhido. */
val COLLAPSED_PANEL_WIDTH = 40.dp

/** Marca da faixa do painel recolhido, para os testes. */
const val COLLAPSED_PANEL_TAG = "painel-recolhido"

/**
 * Botão de recolher/expandir o painel. As setas duplas apontam para onde o painel vai: um painel da esquerda
 * recolhe para a esquerda («) e expande para a direita (»); um painel da direita, o contrário.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelToggleButton(
    side: PanelSide,
    collapsed: Boolean,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pointsLeft = (side == PanelSide.LEFT) != collapsed
    val label = if (collapsed) Strings.expandPanel(title) else Strings.collapsePanel(title)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, modifier = modifier.size(30.dp)) {
            Icon(
                if (pointsLeft) AppIcons.DoubleArrowLeft else AppIcons.DoubleArrowRight,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Painel recolhido: uma faixa estreita na borda com o botão de expandir e o título na vertical.
 * Clicar em qualquer lugar da faixa também expande.
 */
@Composable
fun CollapsedPanel(title: String, side: PanelSide, onExpand: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.width(COLLAPSED_PANEL_WIDTH).fillMaxHeight().testTag(COLLAPSED_PANEL_TAG)
            .clickable(onClick = onExpand),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PanelToggleButton(side, collapsed = true, title, onExpand, Modifier.padding(top = 4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.padding(top = 12.dp).vertical(),
            )
        }
    }
}

/** Gira o texto 90° (lido de cima para baixo), ocupando a largura da faixa e a altura do texto deitado. */
private fun Modifier.vertical(): Modifier = this
    .layout { measurable, constraints ->
        // Mede "deitado" (altura disponível vira largura) e ocupa o espaço "em pé".
        val placeable =
            measurable.measure(Constraints(maxWidth = constraints.maxHeight, maxHeight = constraints.maxWidth))
        layout(placeable.height, placeable.width) {
            placeable.place(x = -(placeable.width - placeable.height) / 2, y = (placeable.width - placeable.height) / 2)
        }
    }
    .graphicsLayer { rotationZ = 90f }
