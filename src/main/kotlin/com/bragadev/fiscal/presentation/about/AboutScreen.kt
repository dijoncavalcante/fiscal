package com.bragadev.fiscal.presentation.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.domain.repository.DiagnosticsRepository
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.components.AppIcons
import com.bragadev.fiscal.presentation.components.DesktopActions
import com.bragadev.fiscal.presentation.components.IconText
import com.bragadev.fiscal.presentation.components.StatusColors
import java.nio.file.Path
import java.time.LocalDate
import javax.imageio.ImageIO

/** O que a tela "Sobre" mostra; montado no AppModule a partir de AppInfo e das pastas do app. */
data class AboutInfo(
    val name: String,
    val version: String,
    val buildDate: LocalDate?,
    val commit: String?,
    val javaVersion: String,
    val windowsVersion: String,
    val dataFolder: Path,
    val logFolder: Path?,
)

/** "Sobre o FISCAL": versão e data, para saber qual versão está em uso quando algo der errado. */
@Composable
fun AboutScreen(info: AboutInfo, diagnostics: DiagnosticsRepository, onBack: () -> Unit) {
    var copied by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        TextButton(onClick = onBack) {
            Icon(AppIcons.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(Strings.BACK, Modifier.padding(start = 6.dp))
        }
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 1.dp,
            modifier = Modifier.padding(top = 16.dp).widthIn(max = 720.dp).fillMaxWidth(),
        ) {
            Column(Modifier.padding(28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    appIcon()?.let { Image(it, contentDescription = null, modifier = Modifier.size(88.dp)) }
                    Column(Modifier.padding(start = 20.dp)) {
                        Text(info.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            Strings.aboutVersion(info.version),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        info.buildDate?.let {
                            Text(Strings.aboutBuildDate(it), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 20.dp))

                // Selecionável: dá para copiar qualquer linha.
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        InfoRow(Strings.ABOUT_VERSION, info.version)
                        info.commit?.let { InfoRow(Strings.ABOUT_COMMIT, it) }
                        InfoRow(Strings.ABOUT_JAVA, info.javaVersion)
                        InfoRow(Strings.ABOUT_WINDOWS, info.windowsVersion)
                        InfoRow(Strings.ABOUT_DATA_FOLDER, info.dataFolder.toString())
                    }
                }

                Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        DesktopActions.copyToClipboard(diagnostics.report(error = null))
                        copied = true
                    }) { Text(Strings.ABOUT_COPY) }
                    OutlinedButton(onClick = { DesktopActions.openFolder(info.dataFolder) }) { Text(Strings.ABOUT_OPEN_DATA) }
                    info.logFolder?.let { logs -> OutlinedButton(onClick = { DesktopActions.openFolder(logs) }) { Text(Strings.OPEN_LOG_FOLDER) } }
                }
                if (copied) {
                    IconText(AppIcons.CheckCircle, Strings.ABOUT_COPIED, color = StatusColors.Positive, modifier = Modifier.padding(top = 10.dp))
                }

                HorizontalDivider(Modifier.padding(vertical = 20.dp))
                IconText(AppIcons.Lock, Strings.ABOUT_OFFLINE, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    Strings.aboutCopyright(info.buildDate?.year ?: LocalDate.now().year),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(170.dp))
        Text(value, modifier = Modifier.weight(1f))
    }
}

/** Ícone do app em alta resolução (gerado por tools/IconGenerator.java). */
@Composable
private fun appIcon(): ImageBitmap? = remember {
    runCatching {
        AboutInfo::class.java.getResourceAsStream("/icons/fiscal-128.png")?.use { ImageIO.read(it).toComposeImageBitmap() }
    }.getOrNull()
}
