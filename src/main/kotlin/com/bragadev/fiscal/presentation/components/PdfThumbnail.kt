package com.bragadev.fiscal.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.model.PageSize
import com.bragadev.fiscal.domain.usecase.PreviewPdfUseCase
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.common.toUserMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.nio.file.Path

/**
 * Visualização compacta de um PDF dentro dos diálogos, para conferir o arquivo antes de renomear,
 * organizar ou retirar. Ajusta a página à largura disponível e permite trocar de página.
 */
@Composable
fun PdfThumbnail(path: Path, modifier: Modifier = Modifier) {
    val previewPdf = koinInject<PreviewPdfUseCase>()
    var pageSizes by remember(path) { mutableStateOf<List<PageSize>>(emptyList()) }
    var pageIndex by remember(path) { mutableIntStateOf(0) }
    var image by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    var error by remember(path) { mutableStateOf<String?>(null) }

    LaunchedEffect(path) {
        when (val info = previewPdf.open(path)) {
            is Outcome.Success -> pageSizes = info.value.pageSizes
            is Outcome.Failure -> error = info.error.toUserMessage()
        }
    }

    Column(modifier.background(Color(0xFFE9ECEF))) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            val widthPx = with(LocalDensity.current) { (maxWidth - 16.dp).toPx() }
            val page = pageSizes.getOrNull(pageIndex)
            LaunchedEffect(path, pageIndex, page, widthPx) {
                page ?: return@LaunchedEffect
                image = null
                when (val rendered = previewPdf.render(path, pageIndex, widthPx / page.widthPoints)) {
                    is Outcome.Success -> image = withContext(Dispatchers.Default) { rendered.value.image.toComposeImageBitmap() }
                    is Outcome.Failure -> error = rendered.error.toUserMessage()
                }
            }
            when {
                error != null -> Text(error.orEmpty(), Modifier.padding(16.dp), textAlign = TextAlign.Center)
                image != null -> Image(
                    bitmap = image!!,
                    contentDescription = path.fileName.toString(),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(8.dp).background(Color.White),
                )
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
        if (pageSizes.size > 1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { pageIndex-- }, enabled = pageIndex > 0) { Text(Strings.PREVIOUS) }
                Text(Strings.pageShort(pageIndex + 1, pageSizes.size), style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { pageIndex++ }, enabled = pageIndex < pageSizes.size - 1) { Text(Strings.NEXT) }
            }
        }
    }
}
