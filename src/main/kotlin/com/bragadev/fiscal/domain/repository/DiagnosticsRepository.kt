package com.bragadev.fiscal.domain.repository

import java.nio.file.Path

/** Informações para investigar problemas: texto de diagnóstico e pasta dos logs. */
interface DiagnosticsRepository {
    val logFolder: Path?

    /** Versão, sistema, erro (se houver) e final do log, para o usuário copiar e enviar. */
    fun report(error: Throwable?): String
}
