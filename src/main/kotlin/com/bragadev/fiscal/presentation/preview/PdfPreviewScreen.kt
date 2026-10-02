package com.bragadev.fiscal.presentation.preview

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.components.Panel
import java.nio.file.Path

@Composable
fun PdfPreviewScreen(
    viewModel: PdfPreviewViewModel,
    path: Path?,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(path) { viewModel.open(path) }
    val state by viewModel.uiState.collectAsState()

    Panel(title = Strings.PREVIEW, modifier = modifier) {
        if (state.hasDocument) {
            Text(
                text = Strings.pageOf(state.pageNumber, state.pageCount),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        PageViewport(state, viewModel, Modifier.weight(1f).fillMaxWidth())
        if (state.hasDocument) {
            HorizontalDivider()
            PreviewToolbar(state, viewModel)
        }
    }
}

@Composable
private fun PageViewport(state: PdfPreviewUiState, viewModel: PdfPreviewViewModel, modifier: Modifier) {
    val density = LocalDensity.current.density
    BoxWithConstraints(modifier.background(Color(0xFFE9ECEF))) {
        LaunchedEffect(maxWidth, maxHeight, density) {
            viewModel.onViewportChanged(maxWidth.value, maxHeight.value, density)
        }
        when {
            state.error != null -> CenteredMessage(state.error)
            state.path == null -> CenteredMessage(Strings.PREVIEW_EMPTY)
            state.page != null -> RenderedPage(state)
        }
        if (state.isLoading) CircularProgressIndicator(Modifier.align(Alignment.Center))
    }
}

@Composable
private fun RenderedPage(state: PdfPreviewUiState) {
    val page = state.page ?: return
    Box(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()),
    ) {
        Image(
            bitmap = page,
            contentDescription = Strings.pageOf(state.pageNumber, state.pageCount),
            modifier = Modifier
                .padding(12.dp)
                .size(state.pageWidthDp.dp, state.pageHeightDp.dp)
                .background(Color.White),
        )
    }
}

@Composable
private fun PreviewToolbar(state: PdfPreviewUiState, viewModel: PdfPreviewViewModel) {
    Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = viewModel::zoomOut) { Text(Strings.ZOOM_OUT) }
            Text(Strings.zoomPercent(state.zoom), Modifier.width(56.dp), textAlign = TextAlign.Center)
            OutlinedButton(onClick = viewModel::zoomIn) { Text(Strings.ZOOM_IN) }
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = viewModel::fitWidth, enabled = state.fitMode != FitMode.WIDTH) { Text(Strings.FIT_WIDTH) }
            TextButton(onClick = viewModel::fitPage, enabled = state.fitMode != FitMode.PAGE) { Text(Strings.FIT_PAGE) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = viewModel::previousPage, enabled = state.canGoPrevious) { Text(Strings.PREVIOUS) }
            Text(Strings.pageShort(state.pageNumber, state.pageCount))
            TextButton(onClick = viewModel::nextPage, enabled = state.canGoNext) { Text(Strings.NEXT) }
        }
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
