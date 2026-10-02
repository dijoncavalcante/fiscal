package com.bragadev.fiscal.app

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.bragadev.fiscal.data.AppDirectories
import com.bragadev.fiscal.data.database.Database
import com.bragadev.fiscal.presentation.common.Strings
import org.koin.core.context.startKoin
import javax.swing.UIManager

fun main() {
    // Seletor de pastas com a aparência nativa do Windows.
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }

    val directories = AppDirectories().apply { ensureCreated() }
    val koin = startKoin { modules(appModule(directories)) }.koin

    application {
        Window(
            onCloseRequest = {
                koin.get<Database>().close()
                exitApplication()
            },
            title = Strings.APP_TITLE,
            state = rememberWindowState(width = 1320.dp, height = 840.dp),
        ) {
            App()
        }
    }
}
