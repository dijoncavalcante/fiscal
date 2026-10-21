package com.bragadev.fiscal.presentation.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Barra de rolagem vertical das listas (Documentos e Mês em edição): o tamanho e a posição da barra mostram
 * quanto da lista já foi visto e quanto falta. Pode ser arrastada. Fica ao lado da lista (não por cima),
 * para não cobrir o ⋮ das linhas. Sem nada para rolar, a barra não aparece.
 */
@Composable
fun ListScrollbar(state: LazyListState, modifier: Modifier = Modifier) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(state),
        modifier = modifier.fillMaxHeight().padding(vertical = 2.dp, horizontal = 1.dp),
        style = scrollbarStyle(),
    )
}

/** Barra de rolagem horizontal, com o mesmo visual da [ListScrollbar] (ex.: preview com zoom maior que o painel). */
@Composable
fun HorizontalListScrollbar(state: ScrollState, modifier: Modifier = Modifier) {
    HorizontalScrollbar(
        adapter = rememberScrollbarAdapter(state),
        modifier = modifier.fillMaxWidth().padding(vertical = 1.dp, horizontal = 2.dp),
        style = scrollbarStyle(),
    )
}

@Composable
private fun scrollbarStyle(): ScrollbarStyle {
    val colors = MaterialTheme.colorScheme
    return ScrollbarStyle(
        minimalHeight = 24.dp,
        thickness = 8.dp,
        shape = RoundedCornerShape(4.dp),
        hoverDurationMillis = 200,
        unhoverColor = colors.outline.copy(alpha = 0.55f),
        hoverColor = colors.primary,
    )
}
