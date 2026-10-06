package com.bragadev.fiscal.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.bragadev.fiscal.data.AppDirectories
import com.bragadev.fiscal.data.database.Database
import com.bragadev.fiscal.data.instance.SingleInstance
import com.bragadev.fiscal.data.logging.AppLog
import com.bragadev.fiscal.data.maintenance.DataMaintenanceRepositoryImpl
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.common.UnexpectedErrors
import org.koin.core.context.startKoin
import java.awt.Frame
import javax.swing.UIManager
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
    AppLog.info("Iniciando ${AppInfo.NAME} ${AppInfo.VERSION}")
    Thread.setDefaultUncaughtExceptionHandler { _, error -> UnexpectedErrors.report(error) }

    DataMaintenanceRepositoryImpl.applyPendingImport(directories)
    val koin = startKoin { modules(appModule(directories)) }.koin

    application {
        // Erros dentro da janela abrem "Algo deu errado" em vez de fechar o app.
        val exceptionHandler = WindowExceptionHandlerFactory { WindowExceptionHandler(UnexpectedErrors::report) }
        CompositionLocalProvider(LocalWindowExceptionHandlerFactory provides exceptionHandler) {
            // Abre maximizada; o tamanho é usado se o usuário restaurar a janela.
            val windowState = rememberWindowState(placement = WindowPlacement.Maximized, width = 1320.dp, height = 840.dp)
            Window(
                onCloseRequest = {
                    AppLog.info("Fechando o app")
                    koin.get<Database>().close()
                    instance.release()
                    exitApplication()
                },
                title = Strings.APP_TITLE,
                state = windowState,
            ) {
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
