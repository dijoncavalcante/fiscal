package com.bragadev.fiscal.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Painel com título usado nas colunas da tela principal.
 *
 * @param collapse quando informado, mostra no título o botão que recolhe o painel para a borda da janela
 * (à direita do título num painel da esquerda; à esquerda do título num painel da direita).
 */
@Composable
fun Panel(
    title: String,
    modifier: Modifier = Modifier,
    collapse: PanelCollapse? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (collapse?.side == PanelSide.RIGHT) {
                    PanelToggleButton(
                        collapse.side,
                        collapsed = false,
                        title,
                        collapse.onCollapse,
                        Modifier.padding(start = 4.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp),
                )
                if (collapse?.side == PanelSide.LEFT) {
                    PanelToggleButton(
                        collapse.side,
                        collapsed = false,
                        title,
                        collapse.onCollapse,
                        Modifier.padding(end = 4.dp)
                    )
                }
            }
            HorizontalDivider()
            content()
        }
    }
}
