package com.bragadev.fiscal.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.domain.model.ImagePage
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.usecase.ReadImageUseCase
import com.bragadev.fiscal.presentation.common.toUserMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.jetbrains.skia.Image as SkiaImage

/** Visualização de uma imagem com a rotação escolhida, como ela vai ficar na página do PDF. */
@Composable
fun ImageThumbnail(page: ImagePage, modifier: Modifier = Modifier) {
    val readImage = koinInject<ReadImageUseCase>()
    var bitmap by remember(page.path) { mutableStateOf<ImageBitmap?>(null) }
    var error by remember(page.path) { mutableStateOf<String?>(null) }

    LaunchedEffect(page.path) {
        when (val bytes = readImage(page.path)) {
            is Outcome.Success -> bitmap = withContext(Dispatchers.Default) {
                runCatching { SkiaImage.makeFromEncoded(bytes.value).toComposeImageBitmap() }.getOrNull()
            }
            is Outcome.Failure -> error = bytes.error.toUserMessage()
        }
    }

    Box(modifier.background(StatusColors.PreviewBackdrop), contentAlignment = Alignment.Center) {
        when {
            error != null -> Text(error.orEmpty(), Modifier.padding(16.dp), textAlign = TextAlign.Center)
            bitmap != null -> Image(
                bitmap = bitmap!!,
                contentDescription = page.path.fileName.toString(),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(16.dp).rotate(page.rotationDegrees.toFloat()),
            )
            else -> CircularProgressIndicator()
        }
    }
}
