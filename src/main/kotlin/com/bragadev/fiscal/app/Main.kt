package com.bragadev.fiscal.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.bragadev.fiscal.data.AppDirectories
import com.bragadev.fiscal.data.database.Database
import com.bragadev.fiscal.data.instance.SingleInstance
import com.bragadev.fiscal.data.logging.AppLog
import com.bragadev.fiscal.data.maintenance.DataMaintenanceRepositoryImpl
import com.bragadev.fiscal.domain.model.AppSettings
import com.bragadev.fiscal.domain.model.WindowBounds
import com.bragadev.fiscal.domain.repository.SettingsRepository
import com.bragadev.fiscal.domain.usecase.UpdateSettingsUseCase
import com.bragadev.fiscal.presentation.common.KeyboardShortcuts
import com.bragadev.fiscal.presentation.common.LocalKeyboardShortcuts
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.common.UnexpectedErrors
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.UIManager
import kotlin.math.roundToInt
import kotlin.system.exitProcess

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Seletor de pastas com a aparência nativa do Windows.
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }

    val directories = AppDirectories().apply { ensureCreated() }

    // Uma cópia só: a segunda pede para a primeira aparecer e fecha (sem tocar no log, que é da primeira).
    val instance = SingleInstance(directories.instanceLockFile, directories.activationSignalFile)
    if (!instance.tryAcquire()) {
        instance.requestActivation()
        exitProcess(0)
    }
    AppLog.init(directories.logDirectory)
    AppLog.info("Iniciando ${AppInfo.NAME} ${AppInfo.FULL_VERSION}")
    Thread.setDefaultUncaughtExceptionHandler { _, error -> UnexpectedErrors.report(error) }

    DataMaintenanceRepositoryImpl.applyPendingImport(directories)
    val koin = startKoin { modules(appModule(directories)) }.koin
    // Carrega as configurações antes de abrir a janela: tema, tamanho/posição e se é a primeira vez.
    // Se o banco não abrir, a janela abre mesmo assim (com os padrões) e mostra "Algo deu errado", em vez de fechar calada.
    val initialSettings = runCatching { runBlocking { koin.get<SettingsRepository>().load() } }.getOrElse { error ->
        UnexpectedErrors.report(error)
        AppSettings(onboardingDone = true)
    }
    val start = WindowRestore.start(initialSettings.windowBounds, screenBounds())
    val icons = AppIconImages.load()

    application {
        // Erros dentro da janela abrem "Algo deu errado" em vez de fechar o app.
        val exceptionHandler = WindowExceptionHandlerFactory { WindowExceptionHandler(UnexpectedErrors::report) }
        val shortcuts = remember { KeyboardShortcuts() }
        CompositionLocalProvider(
            LocalWindowExceptionHandlerFactory provides exceptionHandler,
            LocalKeyboardShortcuts provides shortcuts,
        ) {
            val windowState = rememberWindowState(
                placement = if (start.maximized) WindowPlacement.Maximized else WindowPlacement.Floating,
                position = if (start.x != null && start.y != null) WindowPosition(start.x.dp, start.y.dp) else WindowPosition(Alignment.Center),
                size = DpSize(start.width.dp, start.height.dp),
            )
            // Último tamanho/posição "normal" (não maximizada), que é o que volta ao restaurar a janela.
            val normalBounds = remember { NormalBounds(start) }
            Window(
                onCloseRequest = {
                    AppLog.info("Fechando o app")
                    val bounds = normalBounds.toWindowBounds(maximized = windowState.placement == WindowPlacement.Maximized)
                    runCatching { runBlocking { koin.get<UpdateSettingsUseCase>().invoke { it.copy(windowBounds = bounds) } } }
                        .onFailure { AppLog.error("Não foi possível guardar o tamanho da janela", it) }
                    koin.get<Database>().close()
                    instance.release()
                    exitApplication()
                },
                title = Strings.APP_TITLE,
                icon = icons.largest?.let { BitmapPainter(it.toComposeImageBitmap()) },
                state = windowState,
                onKeyEvent = shortcuts::handle,
            ) {
                LaunchedEffect(Unit) {
                    // Vários tamanhos: o Windows escolhe o certo para a barra de título, a barra de tarefas e o Alt+Tab.
                    if (icons.all.isNotEmpty()) window.iconImages = icons.all
                }
                LaunchedEffect(Unit) {
                    snapshotFlow { Triple(windowState.placement, windowState.position, windowState.size) }.collect { (placement, position, size) ->
                        if (placement == WindowPlacement.Floating && position is WindowPosition.Absolute) {
                            normalBounds.update(position.x.value, position.y.value, size.width.value, size.height.value)
                        }
                    }
                }
                LaunchedEffect(Unit) {
                    instance.activationRequests().collect {
                        windowState.isMinimized = false
                        window.extendedState = window.extendedState and Frame.ICONIFIED.inv()
                        window.toFront()
                        window.requestFocus()
                    }
                }
                App()
            }
        }
    }
}

/** Área de cada monitor, para conferir se a posição salva ainda está visível. */
private fun screenBounds() = runCatching {
    GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration.bounds }
}.getOrDefault(emptyList())

private class NormalBounds(start: WindowStart) {
    private var x = start.x
    private var y = start.y
    private var width = start.width
    private var height = start.height

    fun update(x: Float, y: Float, width: Float, height: Float) {
        if (width.isNaN() || height.isNaN()) return
        this.x = x.roundToInt()
        this.y = y.roundToInt()
        this.width = width.roundToInt()
        this.height = height.roundToInt()
    }

    /** Sem posição conhecida (abriu maximizada e nunca foi restaurada): nada a guardar, abre no padrão. */
    fun toWindowBounds(maximized: Boolean): WindowBounds? {
        val left = x ?: return null
        val top = y ?: return null
        return WindowBounds(left, top, width, height, maximized)
    }
}

/** Ícone do app em vários tamanhos (gerados por tools/IconGenerator.java). */
private class AppIconImages(val all: List<BufferedImage>) {
    val largest: BufferedImage? get() = all.maxByOrNull { it.width }

    companion object {
        private val SIZES = listOf(16, 24, 32, 48, 64, 128, 256)

        fun load() = AppIconImages(
            SIZES.mapNotNull { size ->
                runCatching { AppIconImages::class.java.getResourceAsStream("/icons/fiscal-$size.png")?.use(ImageIO::read) }.getOrNull()
            },
        )
    }
}
