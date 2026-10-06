package com.bragadev.fiscal.data.logging

import com.bragadev.fiscal.domain.repository.DiagnosticsRepository
import java.nio.file.Path
import java.time.LocalDateTime

/** Monta o texto de "Copiar diagnóstico". Nada é enviado: o usuário decide onde colar. */
class DiagnosticsRepositoryImpl(
    private val appName: String,
    private val appVersion: String,
) : DiagnosticsRepository {
    override val logFolder: Path? get() = AppLog.logDirectory

    override fun report(error: Throwable?): String = buildString {
        appendLine("$appName $appVersion")
        appendLine("Data: ${LocalDateTime.now()}")
        appendLine("Windows: ${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})")
        appendLine("Java: ${System.getProperty("java.version")}")
        error?.let {
            appendLine()
            appendLine("Erro:")
            append(AppLog.stackTraceOf(it))
        }
        appendLine()
        appendLine("Final do log:")
        AppLog.tail(TAIL_LINES).forEach(::appendLine)
    }

    private companion object {
        const val TAIL_LINES = 80
    }
}
