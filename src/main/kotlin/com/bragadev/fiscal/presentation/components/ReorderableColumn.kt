package com.bragadev.fiscal.presentation.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
 * Lista em que o usuário muda a ordem segurando uma linha e arrastando para cima ou para baixo.
 *
 * - O gesto é medido na lista (que não se move), a partir do ponto onde o arraste começou: o item
 *   acompanha o mouse com exatidão, sem acumular erro quando as linhas trocam de lugar.
 * - Ao passar da metade da linha vizinha, o item troca de lugar com ela ([onMove]).
 * - Cada linha fica ligada ao seu item (`key`), então o estado acompanha o item ao mudar de posição.
 * - Cliques e botões dentro da linha continuam funcionando; só um movimento com o botão pressionado arrasta.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    key: (T) -> Any,
    itemHeight: Dp,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    row: @Composable (index: Int, item: T, isDragging: Boolean) -> Unit,
) {
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }
    val currentItems by rememberUpdatedState(items)
    val currentOnMove by rememberUpdatedState(onMove)
    var draggingKey by remember { mutableStateOf<Any?>(null) }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var offset by remember { mutableFloatStateOf(0f) }

    val dragModifier = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            // O item é o que estava sob o mouse no momento do clique (não onde o arraste foi reconhecido,
            // que num movimento rápido já pode estar em outra linha).
            val down = awaitFirstDown(requireUnconsumed = false)
            val startIndex = (down.position.y / itemHeightPx).toInt()
            if (startIndex !in currentItems.indices) return@awaitEachGesture
            val firstMove = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                ?: return@awaitEachGesture // foi só um clique: os botões e o clique na linha seguem normais

            draggingIndex = startIndex
            draggingKey = key(currentItems[startIndex])
            fun follow(y: Float) {
                // Distância real do mouse desde o clique, descontando as linhas que o item já pulou.
                offset = (y - down.position.y) - (draggingIndex - startIndex) * itemHeightPx
                while (offset > itemHeightPx / 2 && draggingIndex < currentItems.lastIndex) {
                    currentOnMove(draggingIndex, draggingIndex + 1)
                    draggingIndex += 1
                    offset -= itemHeightPx
                }
                while (offset < -itemHeightPx / 2 && draggingIndex > 0) {
                    currentOnMove(draggingIndex, draggingIndex - 1)
                    draggingIndex -= 1
                    offset += itemHeightPx
                }
            }
            follow(firstMove.position.y)
            drag(firstMove.id) { change ->
                change.consume()
                follow(change.position.y)
            }
            draggingKey = null
            draggingIndex = -1
            offset = 0f
        }
    }

    Column(modifier.then(dragModifier)) {
        items.forEachIndexed { index, item ->
            val itemKey = key(item)
            key(itemKey) {
                val isDragging = itemKey == draggingKey
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
                    row(index, item, isDragging)
                }
            }
        }
    }
}
