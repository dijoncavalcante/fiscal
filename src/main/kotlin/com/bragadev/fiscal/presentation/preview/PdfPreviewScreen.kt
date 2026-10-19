package com.bragadev.fiscal.presentation.preview

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.components.ListScrollbar
import com.bragadev.fiscal.presentation.components.Panel
import com.bragadev.fiscal.presentation.components.StatusColors
import kotlinx.coroutines.launch
import java.nio.file.Path

/** Marca da lista de páginas do preview, para os testes. */
internal const val PREVIEW_PAGES_TAG = "paginas-preview"

/** Espaço entre as páginas e em volta delas. */
private val PAGE_GAP = 12.dp

@Composable
fun PdfPreviewScreen(
    viewModel: PdfPreviewViewModel,
    path: Path?,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(path) { viewModel.open(path) }
    val state by viewModel.uiState.collectAsState()
    // Documento novo começa do topo.
    val listState = remember(state.path) { LazyListState() }
    val scope = rememberCoroutineScope()
    val goTo = { index: Int -> scope.launch { listState.animateScrollToItem(index) }; Unit }

    Panel(title = Strings.PREVIEW, modifier = modifier) {
        if (state.hasDocument) {
            Text(
                text = Strings.pageOf(state.pageNumber, state.pageCount),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        PageViewport(state, viewModel, listState, Modifier.weight(1f).fillMaxWidth())
        if (state.hasDocument) {
            HorizontalDivider()
            PreviewToolbar(
                state,
                viewModel,
                onPrevious = { goTo(state.pageIndex - 1) },
                onNext = { goTo(state.pageIndex + 1) })
        }
    }
}

/** Páginas uma abaixo da outra: a roda do mouse (ou a barra de rolagem) percorre o PDF inteiro, para cima e para baixo. */
@Composable
private fun PageViewport(
    state: PdfPreviewUiState,
    viewModel: PdfPreviewViewModel,
    listState: LazyListState,
    modifier: Modifier
) {
    Row(modifier.background(StatusColors.PreviewBackdrop)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val density = LocalDensity.current.density
            LaunchedEffect(maxWidth, maxHeight, density) {
                viewModel.onViewportChanged(maxWidth.value, maxHeight.value, density)
            }
            when {
                state.error != null && state.pages.isEmpty() -> CenteredMessage(state.error)
                state.path == null -> CenteredMessage(Strings.PREVIEW_EMPTY)
                state.pages.isNotEmpty() -> {
                    LaunchedEffect(listState, viewModel) {
                        snapshotFlow { listState.layoutInfo.let { it.visibleRange() to it.mostVisibleIndex() } }
                            .collect { (range, current) ->
                                viewModel.onVisiblePagesChanged(range)
                                current?.let(viewModel::onCurrentPageChanged)
                            }
                    }
                    // Com zoom alto a página fica mais larga que o painel: rola também para os lados.
                    val contentWidth = maxOf(maxWidth, (state.maxPageWidthDp + PAGE_GAP.value * 2).dp)
                    Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                        LazyColumn(
                            Modifier.width(contentWidth).fillMaxHeight().testTag(PREVIEW_PAGES_TAG),
                            state = listState,
                            contentPadding = PaddingValues(vertical = PAGE_GAP),
                            verticalArrangement = Arrangement.spacedBy(PAGE_GAP),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            items(state.pages, key = { it.index }) { page -> PageImage(page, state.pageCount) }
                        }
                    }
                }
            }
            if (state.isLoading) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        if (state.pages.isNotEmpty()) ListScrollbar(listState)
    }
}

/** Uma página: o espaço já tem o tamanho certo; enquanto a imagem não fica pronta, aparece a folha em branco. */
@Composable
private fun PageImage(page: PreviewPage, pageCount: Int) {
    val size = Modifier.size(page.widthDp.dp, page.heightDp.dp).background(Color.White)
    val image = page.image
    if (image == null) {
        Box(size, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        }
    } else {
        Image(
            bitmap = image,
            contentDescription = Strings.pageOf(page.index + 1, pageCount),
            contentScale = ContentScale.FillBounds,
            modifier = size,
        )
    }
}

/** Páginas que aparecem, mesmo que em parte. */
private fun LazyListLayoutInfo.visibleRange(): IntRange {
    val items = visibleItemsInfo
    return if (items.isEmpty()) IntRange.EMPTY else items.first().index..items.last().index
}

/** Página que ocupa mais espaço na tela. */
private fun LazyListLayoutInfo.mostVisibleIndex(): Int? = visibleItemsInfo.maxByOrNull { item ->
    minOf(item.offset + item.size, viewportEndOffset) - maxOf(item.offset, viewportStartOffset)
}?.index

@Composable
private fun PreviewToolbar(
    state: PdfPreviewUiState,
    viewModel: PdfPreviewViewModel,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = viewModel::zoomOut) { Text(Strings.ZOOM_OUT) }
            Text(Strings.zoomPercent(state.currentZoom), Modifier.width(56.dp), textAlign = TextAlign.Center)
            OutlinedButton(onClick = viewModel::zoomIn) { Text(Strings.ZOOM_IN) }
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = viewModel::fitWidth, enabled = state.fitMode != FitMode.WIDTH) { Text(Strings.FIT_WIDTH) }
            TextButton(onClick = viewModel::fitPage, enabled = state.fitMode != FitMode.PAGE) { Text(Strings.FIT_PAGE) }
        }
        // Atalho para pular de página; a rolagem do mouse já passa por todas.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = onPrevious, enabled = state.canGoPrevious) { Text(Strings.PREVIOUS) }
            Text(Strings.pageShort(state.pageNumber, state.pageCount))
            TextButton(onClick = onNext, enabled = state.canGoNext) { Text(Strings.NEXT) }
        }
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
