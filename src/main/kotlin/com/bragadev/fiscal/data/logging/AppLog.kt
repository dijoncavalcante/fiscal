package com.bragadev.fiscal.data.logging

import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.logging.FileHandler
import java.util.logging.Formatter
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * Registro local de operações e erros, em `%APPDATA%\Fiscal\logs\fiscal-N.log`
 * (5 arquivos de até 1 MB, o mais recente é o `fiscal-0.log`). Só fica nesta máquina.
 *
 * Antes de [init], as mensagens vão só para o console (ex.: nos testes).
 */
object AppLog {
    private const val FILE_LIMIT_BYTES = 1_000_000
    private const val FILE_COUNT = 5
    private const val FILE_PATTERN = "fiscal-%g.log"

    private val logger: Logger = Logger.getLogger("fiscal").apply { useParentHandlers = true }
    var logDirectory: Path? = null
        private set

    fun init(directory: Path) {
        Files.createDirectories(directory)
        logDirectory = directory
        val handler = FileHandler(directory.resolve(FILE_PATTERN).toString(), FILE_LIMIT_BYTES, FILE_COUNT, true).apply {
            encoding = Charsets.UTF_8.name()
            formatter = LineFormatter
        }
        logger.handlers.forEach(logger::removeHandler)
        logger.addHandler(handler)
        logger.useParentHandlers = false
        logger.level = Level.INFO
    }

    fun info(message: String) = logger.info(message)

    fun warn(message: String, error: Throwable? = null) = logger.log(Level.WARNING, message, error)

    fun error(message: String, error: Throwable? = null) = logger.log(Level.SEVERE, message, error)

    /** Últimas linhas do log atual, para o diagnóstico. */
    fun tail(lines: Int): List<String> {
        val current = logDirectory?.resolve(FILE_PATTERN.replace("%g", "0")) ?: return emptyList()
        if (!Files.exists(current)) return emptyList()
        return runCatching { Files.readAllLines(current, Charsets.UTF_8).takeLast(lines) }.getOrDefault(emptyList())
    }

    private object LineFormatter : Formatter() {
        private val time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        override fun format(record: LogRecord): String = buildString {
            append(LocalDateTime.now().format(time)).append(' ').append(record.level.name).append(' ').append(record.message)
            append(System.lineSeparator())
            record.thrown?.let { append(stackTraceOf(it)) }
        }
    }

    fun stackTraceOf(error: Throwable): String = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
}
