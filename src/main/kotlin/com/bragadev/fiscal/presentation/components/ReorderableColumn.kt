package com.bragadev.fiscal.presentation.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex

/**
 * Lista em que o usuário muda a ordem arrastando a "alça" de cada item.
 *
 * Cada linha tem altura fixa [itemHeight]; ao arrastar mais da metade de uma linha para cima ou para baixo,
 * o item troca de lugar com o vizinho ([onMove]). A linha arrastada acompanha o mouse por cima das outras.
 *
 * @param row conteúdo da linha; aplique `handle` ao elemento que serve de alça (ex.: "⠿").
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    key: (T) -> Any,
    itemHeight: Dp,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    row: @Composable (index: Int, item: T, handle: Modifier, isDragging: Boolean) -> Unit,
) {
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }
    val currentItems by rememberUpdatedState(items)
    val currentOnMove by rememberUpdatedState(onMove)
    var draggingKey by remember { mutableStateOf<Any?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }

    Column(modifier) {
        items.forEachIndexed { index, item ->
            val itemKey = key(item)
            val isDragging = itemKey == draggingKey
            val handle = Modifier.pointerInput(itemKey) {
                detectDragGestures(
                    onDragStart = {
                        draggingKey = itemKey
                        offset = 0f
                    },
                    onDragEnd = { draggingKey = null; offset = 0f },
                    onDragCancel = { draggingKey = null; offset = 0f },
                    onDrag = { change, amount ->
                        change.consume()
                        offset += amount.y
                        val position = currentItems.indexOfFirst { key(it) == itemKey }
                        if (offset > itemHeightPx / 2 && position < currentItems.lastIndex) {
                            currentOnMove(position, position + 1)
                            offset -= itemHeightPx
                        } else if (offset < -itemHeightPx / 2 && position > 0) {
                            currentOnMove(position, position - 1)
                            offset += itemHeightPx
                        }
                    },
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragging) offset else 0f
                        shadowElevation = if (isDragging) 8f else 0f
                    },
            ) {
                row(index, item, handle, isDragging)
            }
        }
    }
}
